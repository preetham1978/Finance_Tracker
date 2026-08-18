package com.example.data.api

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.FinanceApplication
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

/**
 * The two paid Google Play subscription "Product ID"s, exactly as created
 * in Play Console (Monetize > Products > Subscriptions). Each product
 * needs a "monthly" and a "yearly" base plan -- see README.md's
 * "Subscriptions" section for the exact setup steps. functions/index.js's
 * PRODUCT_TIER_MAP must be kept in sync with these ids.
 */
object PlayProductIds {
    const val PROFESSIONAL = "professional"
    const val PROFESSIONAL_PLUS = "professional_plus"
}

object PlayBasePlanIds {
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
}

/**
 * Thin coroutine-friendly wrapper around Google Play's BillingClient for
 * the Professional / Professional Plus subscriptions.
 *
 * Flow: connect() once (e.g. from MainActivity) -> queryProductDetails()
 * loads live prices for the upgrade screen -> launchPurchaseFlow() when
 * the user taps buy -> Play shows its own purchase UI -> Play calls back
 * into onPurchasesUpdated() with the result -> a successful purchase gets
 * acknowledged locally (Play requires this within 3 days or it
 * auto-refunds) and then verified against our backend
 * (verifyPlayPurchase in functions/index.js), which is the step that
 * actually grants the tier in Firestore. The app itself never decides "the
 * user is subscribed now" -- see verifyPlayPurchase's doc comment for why
 * that has to happen server-side.
 */
object BillingManager {
    private const val TAG = "BillingManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    // "idle" | "purchasing" | "verifying" | "success" | "error:<message>"
    private val _purchaseStatus = MutableStateFlow("idle")
    val purchaseStatus: StateFlow<String> = _purchaseStatus.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    scope.launch {
                        for (purchase in purchases) {
                            handlePurchase(purchase)
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseStatus.value = "idle"
            }
            else -> {
                Log.e(TAG, "Purchase update failed: ${billingResult.debugMessage}")
                _purchaseStatus.value = "error:${billingResult.debugMessage}"
            }
        }
    }

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(FinanceApplication.appContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
    }

    private var connected = false

    /** Call once (e.g. from MainActivity's onCreate) before showing any upgrade UI. Safe to call more than once. */
    fun connect() {
        if (connected) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected = true
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
                Log.w(TAG, "Billing service disconnected; will retry on next connect() call")
            }
        })
    }

    private suspend fun queryProductDetails() {
        val products = listOf(PlayProductIds.PROFESSIONAL, PlayProductIds.PROFESSIONAL_PLUS).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()

        val result = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (cont.isActive) cont.resumeWith(Result.success(productDetailsList))
                } else {
                    Log.e(TAG, "queryProductDetailsAsync failed: ${billingResult.debugMessage}")
                    if (cont.isActive) cont.resumeWith(Result.success(emptyList()))
                }
            }
        }
        _productDetails.value = result.associateBy { it.productId }
    }

    /**
     * Re-checks any purchase Play already has on record for this
     * device/account (e.g. app reinstalled, or a previous
     * verifyPlayPurchase call failed on a dropped connection) and
     * re-verifies it against our backend, so the user doesn't lose access
     * they already paid for.
     */
    private suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = suspendCancellableCoroutine<List<Purchase>> { cont ->
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (cont.isActive) cont.resumeWith(Result.success(purchases))
                } else {
                    if (cont.isActive) cont.resumeWith(Result.success(emptyList()))
                }
            }
        }
        for (purchase in result) {
            handlePurchase(purchase, isRestore = true)
        }
    }

    /** Call this from the "Restore purchases" button on the upgrade screen too, in case verification failed silently before. */
    fun restorePurchasesManually() {
        scope.launch { restorePurchases() }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String, basePlanId: String) {
        val details = _productDetails.value[productId]
        if (details == null) {
            _purchaseStatus.value = "error:Plan details not loaded yet, try again in a moment"
            return
        }
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId }
            ?.offerToken
        if (offerToken == null) {
            _purchaseStatus.value = "error:No offer found for $productId/$basePlanId"
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _purchaseStatus.value = "purchasing"
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private suspend fun handlePurchase(purchase: Purchase, isRestore: Boolean = false) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            // PENDING (e.g. waiting on a bank/UPI confirmation) -- nothing to
            // grant yet, Play calls onPurchasesUpdated again once it resolves.
            return
        }

        // Play requires acknowledgment within 3 days of purchase or it's
        // automatically refunded. This is purely Play's own bookkeeping
        // that a real app received the purchase -- it has nothing to do
        // with whether our backend considers the user subscribed, which
        // is decided entirely by verifyPlayPurchase below.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            suspendCancellableCoroutine<Unit> { cont ->
                billingClient.acknowledgePurchase(ackParams) {
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
            }
        }

        val productId = purchase.products.firstOrNull() ?: return
        if (!isRestore) _purchaseStatus.value = "verifying"

        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                _purchaseStatus.value = "error:Sign in required to activate a subscription"
                return
            }
            val token = user.getIdToken(false).await().token
            if (token == null) {
                _purchaseStatus.value = "error:Could not verify your account, please try again"
                return
            }
            val response = BillingRetrofitClient.service.verifyPurchase(
                "Bearer $token",
                VerifyPurchaseRequest(productId = productId, purchaseToken = purchase.purchaseToken)
            )
            Log.d(TAG, "Purchase verified: tier=${response.tier} state=${response.subscriptionState}")
            _purchaseStatus.value = "success"
        } catch (e: Exception) {
            Log.e(TAG, "Purchase verification failed", e)
            if (!isRestore) {
                _purchaseStatus.value =
                    "error:Purchase went through with Google, but we couldn't confirm it with our server yet. Try 'Restore purchases' in a minute, or contact support if it persists."
            }
        }
    }

    fun resetStatus() {
        _purchaseStatus.value = "idle"
    }
}
