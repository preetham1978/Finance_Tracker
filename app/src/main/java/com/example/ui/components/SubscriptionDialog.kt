package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.example.data.api.BillingManager
import com.example.data.api.PlayBasePlanIds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private data class PlanInfo(
    val tierId: String,
    val displayName: String,
    val monthlyBlurb: String,
    val features: List<String>
)

private val PLANS = listOf(
    PlanInfo(
        tierId = "free",
        displayName = "Free",
        monthlyBlurb = "Always free",
        features = listOf(
            "On-device receipt scanning",
            "Manual entry, budgets & goals",
            "Cloud backup",
            "5 AI-powered scans/insights per month"
        )
    ),
    PlanInfo(
        tierId = "professional",
        displayName = "Professional",
        monthlyBlurb = "For regular AI use",
        features = listOf(
            "Everything in Free",
            "100 AI-powered scans/insights per month",
            "AI tax planning"
        )
    ),
    PlanInfo(
        tierId = "professional_plus",
        displayName = "Professional Plus",
        monthlyBlurb = "For heavy AI use",
        features = listOf(
            "Everything in Professional",
            "500 AI-powered scans/insights per month",
            "Priority support"
        )
    )
)

/** Finds the formatted price (e.g. "₹99.00") for a given base plan (monthly/yearly) on a product, or null if not loaded yet. */
private fun formattedPriceFor(details: ProductDetails?, basePlanId: String): String? {
    return details?.subscriptionOfferDetails
        ?.firstOrNull { it.basePlanId == basePlanId }
        ?.pricingPhases
        ?.pricingPhaseList
        ?.firstOrNull()
        ?.formattedPrice
}

/**
 * The upgrade/manage-subscription screen. Shows all three tiers with live
 * pricing pulled from Google Play (via BillingManager), highlights the
 * user's current plan (read from Firestore's subscriptions/{uid}, written
 * only by the verifyPlayPurchase Cloud Function -- see that function's
 * comment for why the client can't just set its own tier), and lets the
 * user buy either paid tier monthly or yearly.
 */
@Composable
fun SubscriptionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val productDetails by BillingManager.productDetails.collectAsState()
    val purchaseStatus by BillingManager.purchaseStatus.collectAsState()
    var currentTier by remember { mutableStateOf("free") }
    var yearlySelected by remember { mutableStateOf(true) }

    suspend fun refreshCurrentTier() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val doc = FirebaseFirestore.getInstance().collection("subscriptions").document(uid).get().await()
            currentTier = if (doc.exists()) (doc.getString("tier") ?: "free") else "free"
        } catch (e: Exception) {
            // Leave currentTier as-is (best-effort display only, doesn't affect entitlement).
        }
    }

    LaunchedEffect(Unit) {
        BillingManager.connect()
        refreshCurrentTier()
    }

    LaunchedEffect(purchaseStatus) {
        if (purchaseStatus == "success") {
            refreshCurrentTier()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plans & Subscription") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SegmentedToggle(
                        yearlySelected = yearlySelected,
                        onChange = { yearlySelected = it }
                    )
                }

                PLANS.forEach { plan ->
                    val details = productDetails[plan.tierId]
                    val basePlanId = if (yearlySelected) PlayBasePlanIds.YEARLY else PlayBasePlanIds.MONTHLY
                    val price = if (plan.tierId == "free") "₹0" else formattedPriceFor(details, basePlanId)
                    val isCurrent = currentTier == plan.tierId

                    PlanCard(
                        plan = plan,
                        price = price,
                        period = if (plan.tierId == "free") "" else if (yearlySelected) "/year" else "/month",
                        isCurrent = isCurrent,
                        canPurchase = plan.tierId != "free" && !isCurrent && activity != null,
                        purchasing = purchaseStatus == "purchasing" || purchaseStatus == "verifying",
                        onPurchase = {
                            if (activity != null) {
                                BillingManager.launchPurchaseFlow(activity, plan.tierId, basePlanId)
                            }
                        }
                    )
                }

                if (purchaseStatus == "verifying") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirming your purchase...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (purchaseStatus.startsWith("error:")) {
                    Text(
                        purchaseStatus.removePrefix("error:"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (purchaseStatus == "success") {
                    Text(
                        "You're on the $currentTier plan.",
                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TextButton(onClick = { BillingManager.restorePurchasesManually() }) {
                    Text("Restore purchases")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                BillingManager.resetStatus()
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SegmentedToggle(yearlySelected: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        listOf("Monthly" to false, "Yearly (save ~30%)" to true).forEach { (label, isYearly) ->
            val selected = yearlySelected == isYearly
            Text(
                text = label,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickableNoRipple { onChange(isYearly) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        indication = null,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        onClick = onClick
    )
)

@Composable
private fun PlanCard(
    plan: PlanInfo,
    price: String?,
    period: String,
    isCurrent: Boolean,
    canPurchase: Boolean,
    purchasing: Boolean,
    onPurchase: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = if (isCurrent) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(plan.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (price != null) "$price$period" else "...",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(plan.monthlyBlurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        plan.features.forEach { feature ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(feature, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when {
            isCurrent -> {
                AssistChip(onClick = {}, enabled = false, label = { Text("Current plan") })
            }
            canPurchase -> {
                Button(
                    onClick = onPurchase,
                    enabled = !purchasing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (purchasing) "Please wait..." else "Upgrade")
                }
            }
        }
    }
}
