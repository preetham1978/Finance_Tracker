package com.example.data.api

import android.util.Log
import com.example.data.Transaction
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.ui.components.CategoryHelper

object GeminiManager {
    private const val TAG = "GeminiManager"

    // Cloud AI calls now go through our Firebase Cloud Functions proxy
    // (functions/index.js) instead of hitting Gemini directly with an API
    // key embedded in the app -- the real key lives server-side only now,
    // and the backend rate-limits per signed-in Firebase user. That means
    // "is cloud AI available" is now "is someone signed in", not "is a key
    // present"; a bypass-login/local-only user falls straight through to
    // on-device OCR / offline templates, same as if the network call had
    // failed. See README.md's "Backend proxy" section for the deploy steps.
    fun isApiKeyAvailable(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    /**
     * Fresh Firebase ID token to authenticate with the backend proxy, or
     * null if signed out or the token fetch itself failed (e.g. no
     * network) -- callers treat null the same as any other failed call and
     * fall through to their existing Groq/offline/OCR fallback.
     */
    private suspend fun currentIdToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch Firebase ID token for backend proxy call", e)
            null
        }
    }

    suspend fun getSpendInsights(transactions: List<Transaction>): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext getOfflineSpendInsights(transactions)
        }

        val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val categoryBreakdown = transactions
            .filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }

        val txListString = transactions.take(15).joinToString("\n") {
            "- ${it.title} (${it.category}): ${it.amount} ${it.currency} [${it.type}]"
        }

        val systemPrompt = "You are a professional financial advisor specializing in personal finance and budgeting for Indian users."
        val prompt = """
            You are an expert personal financial advisor in India. Analyze the following financial status and recent transactions:
            - Total Income: $totalIncome INR
            - Total Expense: $totalExpense INR
            - Category Breakdown: $categoryBreakdown

            Recent Transactions:
            $txListString

            Please provide:
            1. An objective analysis of my monthly spends and financial health.
            2. Specific, actionable savings recommendations based on my top categories.
            3. A short, highly encouraging summary of how I can optimize my financial habits this month.

            Format your response clearly using bullet points and neat paragraphs. Focus on Indian context. Keep it professional, helpful, and concise.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val token = currentIdToken() ?: throw java.io.IOException("Not signed in to Firebase; cloud AI proxy requires an ID token")
            val response = RetrofitClient.service.generateContent("Bearer $token", request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No insights could be generated. Please try again later."
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching spend insights from Gemini", e)
            // Gemini failed (e.g. depleted prepay billing) -- Groq is a
            // separate free-tier text provider unaffected by that outage,
            // so try it before giving up and showing the generic offline
            // template. See GroqApiService.kt for why Groq specifically.
            val groqResult = GroqManager.generateText(systemPrompt, prompt)
            groqResult ?: (getOfflineSpendInsights(transactions) + "\n\n(Note: Exhibiting offline insights due to connection error: ${e.localizedMessage})")
        }
    }

    suspend fun getTaxSavingInsights(
        income: Double,
        deductions80C: Double,
        healthInsurance: Double,
        otherDeductions: Double
    ): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext getOfflineTaxInsights(income, deductions80C, healthInsurance)
        }

        val systemPrompt = "You are a certified Indian tax advisor and chartered accountant."
        val prompt = """
            You are a certified tax planner in India. A user has provided the following financial details for tax calculation:
            - Gross Annual Income: $income INR
            - Current Section 80C Investments (PPF, ELSS, EPF, etc.): $deductions80C INR (limit is 1.5 Lakhs)
            - Section 80D Health Insurance: $healthInsurance INR
            - Other standard or custom deductions: $otherDeductions INR

            Please provide:
            1. A highly tailored tax-saving roadmap under the Indian Income Tax Act.
            2. Actionable suggestions to maximize savings (e.g., investing in ELSS, NPS under 80CCD(1B) up to 50k, PPF, or purchasing Health Insurance).
            3. A brief explanation of the major differences they should consider between Old vs New Tax Regimes based on their specific bracket.

            Format your response clearly with scannable headers and bullet points. Keep it professional, precise, and practical.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val token = currentIdToken() ?: throw java.io.IOException("Not signed in to Firebase; cloud AI proxy requires an ID token")
            val response = RetrofitClient.service.generateContent("Bearer $token", request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Unable to generate AI tax plan. Please try again."
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tax insights from Gemini", e)
            val groqResult = GroqManager.generateText(systemPrompt, prompt)
            groqResult ?: (getOfflineTaxInsights(income, deductions80C, healthInsurance) + "\n\n(Note: Exhibiting offline tax suggestions due to connection error: ${e.localizedMessage})")
        }
    }

    suspend fun analyzeBill(billBase64: String, billFileName: String): ParsedBill? = withContext(Dispatchers.IO) {
        if (billBase64.isBlank()) {
            return@withContext ParsedBill(
                title = "Unknown Transaction",
                amount = 0.0,
                category = "Other",
                currency = "INR",
                notes = "Empty image"
            )
        }
        if (!isApiKeyAvailable()) {
            Log.d(TAG, "Gemini API key is not available, trying on-device OCR before falling back to mock")
            OcrManager.recognizeTextFromBase64(billBase64)?.takeIf { it.isNotBlank() }?.let {
                return@withContext OcrManager.parseReceiptText(it, billFileName)
            }
            return@withContext getMockBillAnalysis(billFileName)
        }

        Log.d(TAG, "Attempting to analyze bill via Gemini: $billFileName")

        val prompt = """
            Analyze the provided image which is a bill, receipt, or payment screenshot.

            Strictly extract the following data:
            1. Merchant/Store Name -- the business's brand/shop name, usually the
               most prominent logo/heading text at the very top of the receipt
               (e.g. "MedPlus", "Reliance Fresh", "Starbucks"), NOT the full
               registered legal entity name if one is separately printed in
               parentheses (e.g. prefer "MedPlus" over "(A Unit of Optiven
               Health Solutions Pvt Ltd)"). NEVER use any of the following as
               the merchant name, even if blank/empty/just a dash "-": a GSTIN,
               a DL (drug license) number, an FSSAI number, an invoice/bill/
               order/serial number, a barcode, a customer/patient ID, a doctor's
               name or registration number, or any other registration/reference
               code. These are usually a mix of letters, digits and slashes
               (e.g. "TN/TRW/21B/01072", "GSTIN: 33AAAAA0000A1Z5", "FSSAI No: -")
               printed as a labeled field near the header, and are NEVER the
               store's name even though they appear close to it or the label
               itself contains letters that look name-like.
            2. Total Amount (Number) -- the FINAL amount actually paid. Indian
               retail/pharmacy invoices label this in several ways -- look for
               "Total", "Grand Total", "Net Payable", "Amount Paid", "Total
               Amount", or "Total Invoice Value" (this exact phrase is common
               and easy to miss). This figure is almost always near the very
               bottom of the receipt, often immediately followed by the same
               amount spelled out in words (e.g. "One hundred Twenty two Rupees
               Forty Paise") -- if that words line is present, use it to double
               check your numeric answer matches it exactly, since it's the
               most reliable confirmation available.
               Do NOT use, even if they appear earlier or more prominently in
               the image: a subtotal before discount/tax, the MRP or price of
               any single line item, a "Total MRP Value" or "Total Savings"
               figure, a tax/GST/CGST/SGST breakdown figure, or any number next
               to a non-amount label such as "Invoice No", "Bill No", "DL No",
               "GSTIN", "Order ID", "Store ID", "Cust ID", or a phone number
               (phone numbers are long digit strings with no currency symbol or
               decimal point -- never mistake one, or any part of one, for a
               price). If several total-like numbers appear, use the LAST one
               near the bottom -- that's what the customer actually paid.
            3. Category (Must be one of: "Food", "Shopping", "Bills & Utilities", "Entertainment", "Travel & Transport", "Health & Fitness", "Personal Loan", "Other")
            4. Currency -- the currency actually shown on the receipt. A "₹"
               symbol, "Rs.", or "INR" all mean Indian Rupees -- output "INR"
               for those, not "USD". Only use a different currency code if the
               receipt clearly shows a different symbol or explicit code (e.g.
               "$" or "USD" for US Dollars). When genuinely unsure, default to
               "INR" rather than guessing "USD".
            5. Brief Summary of items or transaction purpose

            Output ONLY valid JSON matching this structure:
            {
                "title": "Merchant Name",
                "amount": 0.0,
                "category": "One of the listed categories",
                "currency": "Currency code",
                "notes": "Brief summary"
            }
            Do not include any other text, explanations, or markdown.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(
                Part(text = prompt),
                Part(inlineData = InlineData(mimeType = "image/jpeg", data = billBase64))
            ))),
            // temperature = 0.0 -- receipts/statements have one correct reading, so we want
            // Gemini's most likely answer every time, not creative sampling. Without this,
            // scanning the exact same photo twice could return slightly different merchant
            // names, totals, or categories, which is confusing and looked like a bug.
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.0)
        )

        try {
            val token = currentIdToken() ?: throw java.io.IOException("Not signed in to Firebase; cloud AI proxy requires an ID token")
            val response = RetrofitClient.service.generateContent("Bearer $token", request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val startIndex = jsonText.indexOf('{')
                val endIndex = jsonText.lastIndexOf('}')
                val cleanJson = if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    jsonText.substring(startIndex, endIndex + 1)
                } else {
                    jsonText.trim().removeSurrounding("```json", "```").trim()
                }
                val gson = com.google.gson.Gson()
                gson.fromJson(cleanJson, ParsedBill::class.java)
            } else {
                getMockBillAnalysis(billFileName)
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "HTTP Error analyzing bill via Gemini: ${e.code()} - $errorBody", e)
            // Gemini failed (e.g. the ongoing prepay-billing-depletion issue)
            // -- try on-device OCR next. Unlike a second cloud provider, it
            // can't be rate-limited or billing-gated, so it's a genuinely
            // reliable fallback rather than just a second thing that can
            // also fail. See OcrManager.kt.
            OcrManager.recognizeTextFromBase64(billBase64)?.takeIf { it.isNotBlank() }?.let {
                return@withContext OcrManager.parseReceiptText(it, billFileName)
            }
            val fallback = getMockBillAnalysis(billFileName)
            return@withContext fallback.copy(
                notes = "${fallback.notes} (Fallback due to HTTP ${e.code()}, on-device OCR found no text either)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing bill via Gemini", e)
            OcrManager.recognizeTextFromBase64(billBase64)?.takeIf { it.isNotBlank() }?.let {
                return@withContext OcrManager.parseReceiptText(it, billFileName)
            }
            val fallback = getMockBillAnalysis(billFileName)
            return@withContext fallback.copy(
                notes = "${fallback.notes} (Fallback due to Connection Error, on-device OCR found no text either)"
            )
        }
    }

    suspend fun analyzeBillText(rawText: String): ParsedBill? = withContext(Dispatchers.IO) {
        if (rawText.isBlank()) {
            return@withContext ParsedBill(
                title = "Unknown Transaction",
                amount = 0.0,
                category = "Other",
                currency = "INR",
                notes = "Empty input"
            )
        }
        if (!isApiKeyAvailable()) {
            return@withContext getMockTextAnalysis(rawText)
        }

        val prompt = """
            Analyze this manual receipt text, SMS, or transaction info:
            "$rawText"

            Extract the following information as a clean JSON block:
            - Merchant/Store Name: the payee/beneficiary/merchant this transaction is
              with. NEVER use the transaction amount, a masked account number
              (e.g. "XX1234"), a date, or a reference/transaction ID as the title.
              If no merchant name is present anywhere in the text, use a short
              generic description of the transaction instead (e.g. "Bank Transfer",
              "ATM Withdrawal", "Account Credit") — never a number.
            - Total Amount
            - Category (Choose one: "Food", "Shopping", "Bills & Utilities", "Entertainment", "Travel & Transport", "Health & Fitness", "Personal Loan", "Other")
            - Currency (e.g. INR, USD, EUR, GBP)
            - Notes/Summary of items or transaction details

            Your response MUST be ONLY the valid JSON object with the following keys:
            "title", "amount", "category", "currency", "notes"
            Do not include any markdown format tags or extra text.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            // temperature = 0.0 -- receipts/statements have one correct reading, so we want
            // Gemini's most likely answer every time, not creative sampling. Without this,
            // scanning the exact same photo twice could return slightly different merchant
            // names, totals, or categories, which is confusing and looked like a bug.
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.0)
        )

        try {
            val token = currentIdToken() ?: throw java.io.IOException("Not signed in to Firebase; cloud AI proxy requires an ID token")
            val response = RetrofitClient.service.generateContent("Bearer $token", request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleanJson = jsonText.trim().removeSurrounding("```json", "```").trim()
                val gson = com.google.gson.Gson()
                gson.fromJson(cleanJson, ParsedBill::class.java)
            } else {
                getMockTextAnalysis(rawText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing raw text via Gemini", e)
            getMockTextAnalysis(rawText)
        }
    }

    private fun getMockTextAnalysis(rawText: String): ParsedBill {
        val normalized = rawText.lowercase()
        // Try to find an amount like ₹1200, rs 500, 350.50, $40, 20 eur
        val amountRegex = """(?i)(?:rs|inr|₹|\$|€|£)?\s*(\d+(?:\.\d{1,2})?)""".toRegex()
        val amountMatch = amountRegex.findAll(normalized).mapNotNull { it.groupValues[1].toDoubleOrNull() }.firstOrNull() ?: 150.0

        // Try to guess category and title
        val category: String
        val title: String
        when {
            normalized.contains("coffee") || normalized.contains("starbucks") || normalized.contains("cafe") || normalized.contains("tea") -> {
                title = "Starbucks Coffee"
                category = "Food"
            }
            normalized.contains("food") || normalized.contains("restaurant") || normalized.contains("haldiram") || normalized.contains("swiggy") || normalized.contains("zomato") || normalized.contains("dinner") || normalized.contains("lunch") || normalized.contains("breakfast") -> {
                title = "Haldiram's Restaurant"
                category = "Food"
            }
            normalized.contains("electricity") || normalized.contains("bescom") || normalized.contains("bill") || normalized.contains("water") || normalized.contains("recharge") || normalized.contains("gas") || normalized.contains("utility") -> {
                title = "BESCOM Electricity Bill"
                category = "Bills & Utilities"
            }
            normalized.contains("zara") || normalized.contains("cloth") || normalized.contains("shopping") || normalized.contains("amazon") || normalized.contains("myntra") || normalized.contains("flipkart") -> {
                title = "Amazon Shopping"
                category = "Shopping"
            }
            normalized.contains("uber") || normalized.contains("ola") || normalized.contains("travel") || normalized.contains("petrol") || normalized.contains("fuel") || normalized.contains("cab") || normalized.contains("auto") -> {
                title = "Uber India"
                category = "Travel & Transport"
            }
            normalized.contains("loan") || normalized.contains("emi") -> {
                title = "HDFC Personal Loan EMI"
                category = "Personal Loan"
            }
            normalized.contains("gym") || normalized.contains("fitness") || normalized.contains("health") || normalized.contains("hospital") || normalized.contains("doctor") || normalized.contains("medicine") -> {
                title = "Apollo Pharmacy"
                category = "Health & Fitness"
            }
            normalized.contains("movie") || normalized.contains("netflix") || normalized.contains("pvr") || normalized.contains("entertainment") || normalized.contains("spotify") -> {
                title = "PVR Cinemas"
                category = "Entertainment"
            }
            else -> {
                // Bank SMS almost always leads with the amount ("Rs.499.00
                // debited..."), so blindly taking the first word (the old
                // behaviour) picked the amount itself as the title. Instead:
                // First, look for a merchant right after "to"/"at"/"towards"/
                // "in favour of" (how most debit SMS name the payee). Then
                // fall back to the first word that isn't an amount, a masked
                // account number, or a common bank-SMS stopword.
                // Never let a number end up as the title.
                val stopWords = setOf(
                    "rs", "inr", "usd", "eur", "gbp", "debited", "credited", "credit", "debit",
                    "from", "to", "at", "towards", "for", "in", "of", "favour", "favor",
                    "a/c", "ac", "acct", "account", "avl", "bal", "balance", "on", "via",
                    "upi", "imps", "neft", "rtgs", "info", "your", "you", "is", "was",
                    "has", "been", "with", "the", "and", "txn", "id", "ref", "no", "dt", "date", "bank"
                )
                val amountTokenRegex = Regex("^(rs\\.?|inr|₹|\\$|€|£)?\\.?\\d[\\d,]*(\\.\\d+)?$", RegexOption.IGNORE_CASE)
                val maskedAccountRegex = Regex("^x*\\d+$", RegexOption.IGNORE_CASE)

                fun usableToken(raw: String): String? {
                    val clean = raw.trim('.', ',', ':', ';', '/')
                    if (clean.length < 2 || !clean.any { it.isLetter() }) return null
                    val lower = clean.lowercase()
                    if (lower in stopWords) return null
                    if (amountTokenRegex.matches(lower) || maskedAccountRegex.matches(lower)) return null
                    return clean
                }

                val merchantRegex = Regex(
                    "(?:to|at|towards|in favou?r of)\\s+([A-Za-z][A-Za-z0-9&.'\\-]{1,30})",
                    RegexOption.IGNORE_CASE
                )
                val merchantCandidate = merchantRegex.findAll(rawText)
                    .mapNotNull { usableToken(it.groupValues[1]) }
                    .firstOrNull()

                val wordCandidate = rawText.split(Regex("[\\s,]+"))
                    .mapNotNull { usableToken(it) }
                    .firstOrNull()

                title = (merchantCandidate ?: wordCandidate)
                    ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    ?: "Bank Transaction"
                category = "Other"
            }
        }

        return ParsedBill(
            title = title,
            amount = amountMatch,
            category = category,
            currency = if (normalized.contains("$") || normalized.contains("usd")) "USD" else if (normalized.contains("€") || normalized.contains("eur")) "EUR" else if (normalized.contains("£") || normalized.contains("gbp")) "GBP" else "INR",
            notes = "Parsed manually: $rawText"
        )
    }

    // --- High Quality Offline / Mock Fallbacks ---

    private fun getOfflineSpendInsights(transactions: List<Transaction>): String {
        val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        if (totalExpense == 0.0) {
            return """
                ### 📊 Spend Insights (Offline Mode)

                You have not registered any expenses yet!

                **Getting Started Tips:**
                - Tap the **+** button below to record your first transaction.
                - Try adding categories like **Food**, **Shopping**, or **Bills & Utilities**.
                - Once you add transactions, we will provide a comprehensive breakdown and budget optimization insights here!
            """.trimIndent()
        }

        val topCategory = transactions
            .filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .maxByOrNull { it.value }

        return """
            ### 📊 Spend Insights (Offline Mode)

            Here is an offline analysis of your spending:
            - **Top Spend Category**: ${topCategory?.key ?: "N/A"} (${String.format("%.2f", topCategory?.value ?: 0.0)} INR)
            - **Total Expense**: ${String.format("%.2f", totalExpense)} INR

            **Actionable Savings Recommendations:**
            1. **Set limits for ${topCategory?.key ?: "top spending"}**: You are spending a significant amount here. Consider budgeting a fixed amount at the start of the month.
            2. **Identify subscription leaks**: Review your **Bills & Utilities** category. Cancel any recurring subscriptions or plans that you haven't used in the past 30 days.
            3. **Follow the 50/30/20 Rule**: Dedicate 50% of your income to Needs (Bills, Rent, Loan EMIs), 30% to Wants (Entertainment, Dining), and 20% to Savings or Debt repayment.

            *(Tip: Configure a valid Gemini or Groq API Key in the AI Studio Secrets panel to get deep, hyper-personalized AI Spend Analysis!)*
        """.trimIndent()
    }

    private fun getOfflineTaxInsights(income: Double, deductions80C: Double, healthInsurance: Double): String {
        val standardDeduction = 75000.0 // Under New Regime (FY 2024-25)
        val taxableIncome = maxOf(0.0, income - standardDeduction)
        return """
            ### 🇮🇳 Indian Tax Savings Roadmap (Offline Mode)

            Based on your Gross Income of **₹${String.format("%,.2f", income)}**:

            **Tax Optimization Tips:**
            1. **Maximize Section 80C (Old Regime Only)**: You have declared **₹${String.format("%,.2f", deductions80C)}** under 80C. You can invest up to **₹1,50,000** in ELSS tax-saving mutual funds (gives higher equity returns), PPF (risk-free 7.1%), or National Savings Certificates (NSC).
            2. **Avail Section 80D (Health Insurance)**: Purchasing a health insurance policy for yourself, spouse, and kids can save taxes up to **₹25,000**. Adding parents premium can save an additional **₹50,000** if they are senior citizens.
            3. **Explore National Pension System (NPS Section 80CCD(1B))**: You can invest an extra **₹50,000** in NPS which is completely over and above the ₹1.5L limit of 80C, offering massive additional tax exemptions.
            4. **Opt for the New Tax Regime**: For most salaried individuals without significant home loans or high HRA, the **New Regime** offers lower tax slabs and a higher zero-tax limit (taxable income up to ₹7 Lakhs has 100% tax rebate under Section 87A).

            *(Tip: Add your Gemini or Groq API Key in AI Studio to get a fully customized, professional CA-grade tax plan tailored to your income bracket!)*
        """.trimIndent()
    }

    private fun getMockBillAnalysis(fileName: String): ParsedBill {
        val name = fileName.lowercase(Locale.getDefault())
        return when {
            name.contains("medplus") || name.contains("med") || name.contains("medical") ||
            name.contains("pharmacy") || name.contains("medicine") || name.contains("health") ||
            name.contains("hospital") || name.contains("doctor") -> ParsedBill(
                title = "MedPlus Pharmacy",
                amount = 450.00,
                category = "Health & Fitness",
                currency = "INR",
                notes = "Extracted: Paracetamol, Multivitamins, Cough Syrup, Antiseptic liquid."
            )
            name.contains("amazon") || name.contains("flipkart") || name.contains("myntra") ||
            name.contains("shopping") || name.contains("retail") || name.contains("store") -> ParsedBill(
                title = "Amazon Shopping",
                amount = 1599.00,
                category = "Shopping",
                currency = "INR",
                notes = "Extracted: Wireless Mouse, USB Cable, Laptop Sleeve, Electronics purchase."
            )
            name.contains("starbucks") || name.contains("coffee") || name.contains("cafe") ||
            name.contains("tea") || name.contains("chai") -> ParsedBill(
                title = "Starbucks Coffee",
                amount = 320.00,
                category = "Food",
                currency = "INR",
                notes = "Extracted: Java Chip Frappuccino, Paneer Tikka Sandwich."
            )
            name.contains("grocery") || name.contains("mart") || name.contains("bazaar") ||
            name.contains("supermarket") || name.contains("reliance") -> ParsedBill(
                title = "Reliance Smart Bazaar",
                amount = 1250.00,
                category = "Food",
                currency = "INR",
                notes = "Extracted: Atta 5kg, Rice 2kg, Cooking Oil, Fresh Veggies, Biscuits."
            )
            name.contains("restaurant") || name.contains("food") || name.contains("diner") ||
            name.contains("swiggy") || name.contains("zomato") || name.contains("haldiram") -> ParsedBill(
                title = "Haldiram's Restaurant",
                amount = 890.00,
                category = "Food",
                currency = "INR",
                notes = "Extracted: Special Thali, Chole Bhature, Lassi, Rasmalai, GST & Service Charge included."
            )
            name.contains("fuel") || name.contains("petrol") || name.contains("diesel") ||
            name.contains("gas") || name.contains("hp") || name.contains("bpcl") ||
            name.contains("shell") -> ParsedBill(
                title = "HP Petrol Pump",
                amount = 1500.00,
                category = "Travel & Transport",
                currency = "INR",
                notes = "Extracted: Unleaded Petrol 14.5 Litres."
            )
            name.contains("zara") || name.contains("clothes") || name.contains("clothing") ||
            name.contains("h&m") || name.contains("pantaloons") || name.contains("fashion") -> ParsedBill(
                title = "Zara Clothing (SC Credit Card)",
                amount = 4500.00,
                category = "Shopping",
                currency = "INR",
                notes = "Extracted: Summer Jacket, Linen Trousers, Standard Chartered Card Payment."
            )
            name.contains("bill") || name.contains("utility") || name.contains("electricity") ||
            name.contains("water") || name.contains("bescom") || name.contains("power") -> ParsedBill(
                title = "BESCOM Electricity Bill",
                amount = 2450.00,
                category = "Bills & Utilities",
                currency = "INR",
                notes = "Extracted: Consumer No 5429184, Usage 210 Units, Due date payment."
            )
            name.contains("travel") || name.contains("bus") || name.contains("train") ||
            name.contains("ticket") || name.contains("ksrtc") || name.contains("irctc") ||
            name.contains("uber") || name.contains("ola") -> ParsedBill(
                title = "KSRTC Bus Ticket",
                amount = 350.00,
                category = "Travel & Transport",
                currency = "INR",
                notes = "Extracted: Bus travel ticket."
            )
            name.contains("movie") || name.contains("pvr") || name.contains("show") ||
            name.contains("entertainment") || name.contains("cinema") || name.contains("netflix") -> ParsedBill(
                title = "PVR Cinemas",
                amount = 680.00,
                category = "Entertainment",
                currency = "INR",
                notes = "Extracted: 2x Movie Tickets, Salted Popcorn, Cola."
            )
            name.contains("phone") || name.contains("jio") || name.contains("airtel") ||
            name.contains("recharge") || name.contains("broadband") || name.contains("internet") -> ParsedBill(
                title = "Jio Prepaid Recharge",
                amount = 299.00,
                category = "Bills & Utilities",
                currency = "INR",
                notes = "Extracted: Monthly Unlimited Plan 1.5GB/day."
            )
            else -> ParsedBill(
                title = "DMart Supermarket",
                amount = 1850.00,
                category = "Food",
                currency = "INR",
                notes = "Mock Scan Fallback: Packaged foods, organic pulses, dairy products, and cleaning essentials."
            )
        }
    }

    suspend fun suggestCategory(merchantName: String): String? = withContext(Dispatchers.IO) {
        if (merchantName.isBlank()) return@withContext null

        if (!isApiKeyAvailable()) {
            return@withContext getOfflineCategorySuggestion(merchantName)
        }

        val prompt = """
            Identify the most appropriate expense category for the merchant name: "$merchantName".
            The available categories are:
            - Food
            - Shopping
            - Bills & Utilities
            - Entertainment
            - Travel & Transport
            - Health & Fitness
            - Personal Loan
            - Other

            Respond with ONLY the exact name of the matched category from the list above. Do not include any other text, explanation, or punctuation.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = "You are an assistant that classifies merchants into finance categories.")))
        )

        try {
            val token = currentIdToken() ?: throw java.io.IOException("Not signed in to Firebase; cloud AI proxy requires an ID token")
            val response = RetrofitClient.service.generateContent("Bearer $token", request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (!responseText.isNullOrBlank()) {
                val matched = CategoryHelper.expenseCategories.find {
                    it.equals(responseText, ignoreCase = true)
                }
                matched ?: getOfflineCategorySuggestion(merchantName)
            } else {
                getOfflineCategorySuggestion(merchantName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error suggesting category via Gemini", e)
            getOfflineCategorySuggestion(merchantName)
        }
    }

    fun getOfflineCategorySuggestion(merchantName: String): String {
        val normalized = merchantName.lowercase(Locale.getDefault())
        return when {
            normalized.contains("coffee") || normalized.contains("starbucks") || normalized.contains("cafe") ||
            normalized.contains("tea") || normalized.contains("chai") || normalized.contains("swiggy") ||
            normalized.contains("zomato") || normalized.contains("restaurant") || normalized.contains("food") ||
            normalized.contains("diner") || normalized.contains("haldiram") || normalized.contains("mcdonald") ||
            normalized.contains("pizza") || normalized.contains("burger") || normalized.contains("eat") -> "Food"

            normalized.contains("zara") || normalized.contains("cloth") || normalized.contains("shopping") ||
            normalized.contains("amazon") || normalized.contains("myntra") || normalized.contains("flipkart") ||
            normalized.contains("mall") || normalized.contains("dmart") || normalized.contains("retail") ||
            normalized.contains("store") -> "Shopping"

            normalized.contains("electricity") || normalized.contains("bescom") || normalized.contains("bill") ||
            normalized.contains("water") || normalized.contains("recharge") || normalized.contains("gas") ||
            normalized.contains("utility") || normalized.contains("broadband") || normalized.contains("phone") ||
            normalized.contains("jio") || normalized.contains("airtel") || normalized.contains("internet") ||
            normalized.contains("power") -> "Bills & Utilities"

            normalized.contains("movie") || normalized.contains("netflix") || normalized.contains("pvr") ||
            normalized.contains("entertainment") || normalized.contains("cinema") || normalized.contains("spotify") ||
            normalized.contains("show") || normalized.contains("ticket") || normalized.contains("hotstar") ||
            normalized.contains("game") || normalized.contains("play") -> "Entertainment"

            normalized.contains("uber") || normalized.contains("ola") || normalized.contains("travel") ||
            normalized.contains("petrol") || normalized.contains("fuel") || normalized.contains("cab") ||
            normalized.contains("auto") || normalized.contains("bus") || normalized.contains("train") ||
            normalized.contains("ksrtc") || normalized.contains("irctc") || normalized.contains("shell") ||
            normalized.contains("hp petrol") || normalized.contains("bpcl") -> "Travel & Transport"

            normalized.contains("gym") || normalized.contains("fitness") || normalized.contains("health") ||
            normalized.contains("hospital") || normalized.contains("doctor") || normalized.contains("medicine") ||
            normalized.contains("apollo") || normalized.contains("pharmacy") || normalized.contains("medplus") ||
            normalized.contains("clinic") -> "Health & Fitness"

            normalized.contains("loan") || normalized.contains("emi") || normalized.contains("hdfc") ||
            normalized.contains("icici") || normalized.contains("sbi") || normalized.contains("mortgage") ||
            normalized.contains("bank") -> "Personal Loan"

            else -> "Other"
        }
    }
}

data class ParsedBill(
    val title: String,
    val amount: Double,
    val category: String,
    val currency: String,
    val notes: String
)
