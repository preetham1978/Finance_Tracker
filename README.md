<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/666822c7-9fe5-476c-8452-7ce0862b7b51

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Deploy the backend proxy first (see "Backend proxy" below) - the app needs `BACKEND_BASE_URL` to reach it.
5. Create a file named `.env` in the project directory and set `BACKEND_BASE_URL` in that file to your deployed proxy's URL (see `.env.example`)
6. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
7. Run the app on an emulator or physical device
8. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Backend proxy

The app's Gemini and Groq calls (bill scanning, spend insights, tax
planning) go through a small Firebase Cloud Functions proxy in
`functions/`, instead of the app holding those providers' API keys itself.
An API key baked into a compiled APK is extractable by anyone who installs
the app, so the real keys live only in this backend's secrets; the app
authenticates to it with the signed-in user's Firebase ID token, and the
backend rate-limits each user to a fixed number of Gemini/Groq calls per
day (see `functions/index.js` for the current caps - tune them once you
have real usage to look at). A user who isn't signed in (e.g. via the
app's "skip login" option) doesn't get cloud AI features at all; the app
falls back to on-device OCR for the scanner and offline template text for
insights, same as it already does if a cloud call fails.

**One-time setup**, from the repo root, with the [Firebase CLI](https://firebase.google.com/docs/cli) installed and logged in (`npm install -g firebase-tools && firebase login`):

```bash
firebase use --add                 # pick your Firebase project (writes .firebaserc)
cd functions && npm install && cd ..
firebase functions:secrets:set GEMINI_API_KEY   # paste your Gemini key
firebase functions:secrets:set GROQ_API_KEY     # paste your Groq key
firebase deploy --only functions
```

The deploy output prints each function's URL, of the form
`https://us-central1-<project-id>.cloudfunctions.net/geminiGenerateContent`.
Set `BACKEND_BASE_URL` in your `.env` to the base of that
(`https://us-central1-<project-id>.cloudfunctions.net/`, trailing slash
included) and rebuild the app.

**Redeploying** after changing `functions/index.js` (e.g. tuning the
daily rate limit or swapping the Gemini model id): `firebase deploy --only functions`.

**Rotating a leaked key**: revoke it at the provider (Google AI
Studio / console.groq.com), issue a new one, then
`firebase functions:secrets:set GEMINI_API_KEY` (or `GROQ_API_KEY`) again
and redeploy. No app rebuild or Play Store update needed, since the key
never lives in the app.
