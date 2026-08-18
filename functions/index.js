/**
 * Backend for Vantage Finance's AI proxy + subscription tiers.
 *
 * WHY THIS EXISTS: the Android app used to call Gemini and Groq directly
 * with API keys baked into the compiled APK (BuildConfig.GEMINI_API_KEY /
 * GROQ_API_KEY). Any APK is trivially decompilable, so those keys were
 * extractable by anyone who installed the app -- fine for a personal
 * build shared with nobody, not something to ship publicly. The two AI
 * proxy functions below replace that: the app sends its Firebase Auth ID
 * token instead of an API key, we verify that token and use it to
 * rate-limit per user according to their subscription tier, and only then
 * forward the request to Gemini or Groq using a key that lives
 * exclusively in this function's runtime secrets (never sent to any
 * device).
 *
 * The third function, verifyPlayPurchase, is what actually grants a paid
 * tier: the Android app calls it right after a Google Play purchase
 * completes, we ask Google's server directly whether that purchase is
 * real and active, and only if it is do we write the tier to Firestore.
 * The client never gets to just declare "I'm subscribed" -- see that
 * function's comment for the full flow.
 *
 * DEPLOY:
 *   cd functions && npm install
 *   firebase functions:secrets:set GEMINI_API_KEY
 *   firebase functions:secrets:set GROQ_API_KEY
 *   firebase functions:secrets:set PLAY_SERVICE_ACCOUNT_JSON
 *   firebase deploy --only functions
 * See README.md's "Backend proxy" and "Subscriptions" sections for the
 * full walkthrough.
 */

const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { google } = require("googleapis");

admin.initializeApp();
const db = admin.firestore();

const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");
const GROQ_API_KEY = defineSecret("GROQ_API_KEY");
// Full JSON key of a Google Cloud service account with the "Android
// publisher API" enabled and linked in Play Console under Setup > API
// access, with "View financial data" + "Manage orders and subscriptions"
// permission. See README.md's "Subscriptions" section for how to create
// this. Stored as one secret containing the whole JSON file's contents.
const PLAY_SERVICE_ACCOUNT_JSON = defineSecret("PLAY_SERVICE_ACCOUNT_JSON");

// Must match applicationId in app/build.gradle.kts exactly.
const PLAY_PACKAGE_NAME = "com.aistudio.financetracker.vkqywz";

// Free-tier Gemini model id as of Aug 2026 -- see the matching comment in
// GeminiApiService.kt if Google renames/retires it again.
const GEMINI_MODEL = "gemini-3.1-flash-lite";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

// Monthly, per-user, per-provider call caps by subscription tier. These
// are a starting point based on typical usage for a personal-finance app
// -- adjust freely once you have real usage data; changing these numbers
// only requires redeploying this file, not an app update, since the app
// never sees or enforces these limits itself.
const TIER_LIMITS = {
  free: { gemini: 5, groq: 5 },
  professional: { gemini: 100, groq: 100 },
  professional_plus: { gemini: 500, groq: 500 },
};

// Google Play subscription "Product ID" (as created in Play Console) ->
// the tier it grants. Both products should have "monthly" and "yearly"
// base plans -- the base plan only affects billing period/price, not
// which tier the product maps to, so it isn't listed here.
const PRODUCT_TIER_MAP = {
  professional: "professional",
  professional_plus: "professional_plus",
};

// Play subscription states that mean "the user should currently have
// access" -- see https://developer.android.com/google/play/billing/subscriptions#lifecycle.
// A canceled-but-not-yet-expired subscription still counts as active
// (Google keeps entitlement until the paid period actually ends); a
// grace period (payment failed but Google is still retrying) also still
// counts, so a user isn't cut off the moment a card fails.
const ENTITLED_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
]);

function currentMonthUtc() {
  return new Date().toISOString().slice(0, 7); // "YYYY-MM"
}

/** Verifies the Authorization: Bearer <Firebase ID token> header. Throws on missing/invalid token. */
async function requireFirebaseUser(req) {
  const header = req.get("Authorization") || "";
  const match = header.match(/^Bearer (.+)$/);
  if (!match) {
    const err = new Error("Missing Authorization: Bearer <Firebase ID token> header");
    err.statusCode = 401;
    throw err;
  }
  try {
    return await admin.auth().verifyIdToken(match[1]);
  } catch (e) {
    logger.warn("ID token verification failed", e);
    const err = new Error("Invalid or expired Firebase ID token");
    err.statusCode = 401;
    throw err;
  }
}

/** Reads the user's current tier from Firestore, defaulting to "free" if unset or unrecognized. */
async function getUserTier(uid) {
  const snap = await db.collection("subscriptions").doc(uid).get();
  const tier = snap.exists ? snap.data().tier : null;
  return TIER_LIMITS[tier] ? tier : "free";
}

/**
 * Atomically checks and increments this month's request count for (uid,
 * provider), using the cap for the user's current subscription tier.
 * Throws a 429 error if the cap is already used up. Counts live in a
 * single doc per user (rateLimits/{uid}) with one field per provider plus
 * the month they apply to, reset whenever the month rolls over -- cheap
 * (two doc reads + one transaction per request) and good enough at this
 * app's scale; move to a sharded counter if this ever becomes a hot spot.
 */
async function checkAndIncrementRateLimit(uid, field) {
  const tier = await getUserTier(uid);
  const limitKey = field === "geminiCount" ? "gemini" : "groq";
  const limit = TIER_LIMITS[tier][limitKey];

  const ref = db.collection("rateLimits").doc(uid);
  const period = currentMonthUtc();

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.exists ? snap.data() : {};
    const isStale = data.period !== period;
    const currentCount = isStale ? 0 : (data[field] || 0);

    if (currentCount >= limit) {
      const err = new Error(
        `Monthly limit reached for your ${tier} plan (${limit}/month). ` +
          `Upgrade for more, or it resets next month.`
      );
      err.statusCode = 429;
      throw err;
    }

    tx.set(
      ref,
      {
        period,
        // Reset the *other* provider's count too when the period rolls
        // over, so a stale field from last month doesn't linger.
        ...(isStale ? { geminiCount: 0, groqCount: 0 } : {}),
        [field]: currentCount + 1,
      },
      { merge: true }
    );
  });
}

function sendError(res, e) {
  const statusCode = e.statusCode || 500;
  if (statusCode >= 500) {
    logger.error(e);
  }
  res.status(statusCode).json({ error: e.message || "Internal error" });
}

exports.geminiGenerateContent = onRequest(
  { secrets: [GEMINI_API_KEY], cors: false, region: "us-central1" },
  async (req, res) => {
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Use POST" });
    }
    try {
      const decoded = await requireFirebaseUser(req);
      await checkAndIncrementRateLimit(decoded.uid, "geminiCount");

      const upstream = await fetch(`${GEMINI_URL}?key=${GEMINI_API_KEY.value()}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(req.body),
      });
      const text = await upstream.text();
      res.status(upstream.status).set("Content-Type", "application/json").send(text);
    } catch (e) {
      sendError(res, e);
    }
  }
);

exports.groqChatCompletion = onRequest(
  { secrets: [GROQ_API_KEY], cors: false, region: "us-central1" },
  async (req, res) => {
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Use POST" });
    }
    try {
      const decoded = await requireFirebaseUser(req);
      await checkAndIncrementRateLimit(decoded.uid, "groqCount");

      const upstream = await fetch(GROQ_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${GROQ_API_KEY.value()}`,
        },
        body: JSON.stringify(req.body),
      });
      const text = await upstream.text();
      res.status(upstream.status).set("Content-Type", "application/json").send(text);
    } catch (e) {
      sendError(res, e);
    }
  }
);

/**
 * Called by the Android app right after a Google Play purchase completes
 * (see BillingManager.kt). Takes the productId + purchaseToken the Play
 * Billing Library handed the app, asks Google's server-to-server API
 * whether that purchase is real, currently paid for, and belongs to this
 * package -- and only if all of that checks out does it write the
 * corresponding tier to subscriptions/{uid}.
 *
 * This step is what makes the whole subscription system trustworthy: a
 * purchase token by itself is just a string the app receives locally, and
 * a modified/patched client could send a fake one claiming to have
 * bought "professional_plus". By verifying directly against Google's API
 * with server credentials the client never sees, a fake token simply
 * fails verification and grants nothing.
 */
exports.verifyPlayPurchase = onRequest(
  { secrets: [PLAY_SERVICE_ACCOUNT_JSON], cors: false, region: "us-central1" },
  async (req, res) => {
    if (req.method !== "POST") {
      return res.status(405).json({ error: "Use POST" });
    }
    try {
      const decoded = await requireFirebaseUser(req);
      const { productId, purchaseToken } = req.body || {};
      if (!productId || !purchaseToken) {
        const err = new Error("productId and purchaseToken are required");
        err.statusCode = 400;
        throw err;
      }
      if (!PRODUCT_TIER_MAP[productId]) {
        const err = new Error(`Unrecognized productId: ${productId}`);
        err.statusCode = 400;
        throw err;
      }

      const credentials = JSON.parse(PLAY_SERVICE_ACCOUNT_JSON.value());
      const auth = new google.auth.GoogleAuth({
        credentials,
        scopes: ["https://www.googleapis.com/auth/androidpublisher"],
      });
      const androidpublisher = google.androidpublisher({ version: "v3", auth });

      const response = await androidpublisher.purchases.subscriptionsv2.get({
        packageName: PLAY_PACKAGE_NAME,
        token: purchaseToken,
      });

      const subscriptionState = response.data.subscriptionState;
      const lineItems = response.data.lineItems || [];
      const purchasedProductIds = lineItems.map((item) => item.productId);

      if (!ENTITLED_STATES.has(subscriptionState)) {
        logger.info(`Purchase not in an entitled state for uid=${decoded.uid}: ${subscriptionState}`);
        const err = new Error(`Subscription is not active (state: ${subscriptionState})`);
        err.statusCode = 402;
        throw err;
      }
      if (!purchasedProductIds.includes(productId)) {
        logger.warn(
          `Product mismatch for uid=${decoded.uid}: claimed ${productId}, token actually covers ${purchasedProductIds.join(", ")}`
        );
        const err = new Error("Purchase token does not match the claimed product");
        err.statusCode = 400;
        throw err;
      }

      const tier = PRODUCT_TIER_MAP[productId];
      await db.collection("subscriptions").doc(decoded.uid).set({
        tier,
        productId,
        subscriptionState,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      logger.info(`Granted tier=${tier} to uid=${decoded.uid}`);
      res.status(200).json({ tier, subscriptionState });
    } catch (e) {
      sendError(res, e);
    }
  }
);
