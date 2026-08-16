package com.example.ui.viewmodel
import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Transaction
import com.example.data.Goal
import com.example.data.Budget
import com.example.data.Category
import com.example.data.TransactionRepository
import com.google.firebase.auth.FirebaseAuth
import com.example.data.api.GeminiManager
import com.example.data.api.CloudSyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

data class FinanceUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val totalBalance: Double = 0.0, // Liquid balance (Cash + Bank)
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val creditCardSpending: Double = 0.0, // Separate tracker for CC
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val searchQuery: String = "",
    val merchantQuery: String = "",
    val selectedTypeFilter: String = "ALL", // "ALL", "INCOME", "EXPENSE"
    val selectedCategoryFilter: String = "ALL",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val activeCurrency: String = "INR", // App-wide default currency
    
    // AI Insights states
    val spendInsights: String = "",
    val isAnalyzingSpend: Boolean = false,
    val taxSavingInsights: String = "",
    val isAnalyzingTax: Boolean = false,
    
    // NEW Diagnostics
    val healthScore: Int = 0, // 0-100
    val runwayDays: Int = 0, // Days until liquid balance is 0 based on burn
    
    // NEW
    val goals: List<Goal> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,

    // Subscription leak detection + end-of-month cash flow projection —
    // both computed purely from the local ledger, no new permissions.
    val subscriptions: List<com.example.data.SubscriptionAlert> = emptyList(),
    val cashFlowForecast: com.example.data.CashFlowForecast? = null,

    // Round-up savings not yet swept into a goal (see sweepRoundUpToGoal).
    val pendingRoundUpTotal: Double = 0.0
)

data class AdvisorState(
    val spendInsights: String,
    val isAnalyzingSpend: Boolean,
    val taxSavingInsights: String,
    val isAnalyzingTax: Boolean
)

data class FilterState(
    val query: String,
    val merchantQuery: String,
    val typeFilter: String,
    val categoryFilter: String,
    val startDate: Long?,
    val endDate: Long?,
    val currency: String
)

class FinanceViewModel(
    private val repository: TransactionRepository,
    private val application: Application
) : ViewModel() {

    private val prefs = application.getSharedPreferences("vantage_prefs", Context.MODE_PRIVATE)

    private val _darkThemeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val darkThemeMode = _darkThemeMode.asStateFlow()

    // Tax country preference — defaults to the device's region (ISO 3166-1
    // alpha-2, e.g. "IN", "US", "GB") so a user outside India doesn't
    // silently see Indian tax law applied to their numbers. User can
    // override it; persisted the same way theme_mode is.
    private val _taxCountry = MutableStateFlow(
        prefs.getString("tax_country", null) ?: java.util.Locale.getDefault().country.ifBlank { "IN" }
    )
    val taxCountry = _taxCountry.asStateFlow()

    fun setTaxCountry(countryCode: String) {
        _taxCountry.value = countryCode
        prefs.edit().putString("tax_country", countryCode).apply()
    }

    // App-level gate for notification-based auto-capture (see
    // TransactionNotificationListener). Defaults OFF — this only ever
    // turns on when the user explicitly flips it, even if OS-level
    // Notification access is already granted for some other reason.
    private val _notifCaptureEnabled = MutableStateFlow(prefs.getBoolean("notif_capture_enabled", false))
    val notifCaptureEnabled = _notifCaptureEnabled.asStateFlow()

    fun setNotifCaptureEnabled(enabled: Boolean) {
        _notifCaptureEnabled.value = enabled
        prefs.edit().putBoolean("notif_capture_enabled", enabled).apply()
    }

    // Google Cloud Synchronization and Auth States
    private val _isUserLoggedIn = MutableStateFlow(CloudSyncManager.isLoggedIn(application))
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _userEmail = MutableStateFlow(CloudSyncManager.getUserEmail(application))
    val userEmail = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow(CloudSyncManager.getUserName(application))
    val userName = _userName.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(CloudSyncManager.getLastSyncTime(application))
    val lastSyncTime = _lastSyncTime.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage = _syncStatusMessage.asStateFlow()

    private val isInitialSyncCompleted = MutableStateFlow(false)

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val firebaseUser = firebaseAuth.currentUser
        val loggedIn = firebaseUser != null
        _isUserLoggedIn.value = loggedIn
        _userEmail.value = firebaseUser?.email
        _userName.value = firebaseUser?.displayName ?: firebaseUser?.email?.substringBefore("@")
        
        if (loggedIn) {
            viewModelScope.launch {
                isInitialSyncCompleted.value = false
                _isSyncing.value = true
                _syncStatusMessage.value = "Connecting to Google Cloud..."
                val cloudTransactions = CloudSyncManager.restoreFromCloud(application)
                if (cloudTransactions != null) {
                    if (cloudTransactions.isNotEmpty()) {
                        cloudTransactions.forEach { tx ->
                            repository.insert(tx)
                        }
                        _syncStatusMessage.value = "Restored ${cloudTransactions.size} transactions from Google Cloud."
                    } else {
                        _syncStatusMessage.value = "Synced with Google Cloud."
                    }
                } else {
                    _syncStatusMessage.value = "Connected to Google Cloud."
                }
                isInitialSyncCompleted.value = true
                _isSyncing.value = false
            }
        } else {
            isInitialSyncCompleted.value = false
        }
    }

    init {
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        
        // Observe all transactions and automatically back up to cloud if user is logged in
        viewModelScope.launch {
            delay(1000)
            _isLoading.value = false
        }
        viewModelScope.launch {
            repository.allTransactions.collect { transactions ->
                if (_isUserLoggedIn.value && isInitialSyncCompleted.value) {
                    CloudSyncManager.backupToCloud(application, transactions)
                }
                // Single choke point for the home-screen widget: fires on
                // every insert/update/delete regardless of which ViewModel
                // function triggered it, so the widget stays fresh without
                // needing a refresh call sprinkled into every mutation path.
                com.example.widget.BalanceWidgetProvider.refreshAll(application)
            }
        }
        
        viewModelScope.launch {
            val categories = repository.allCategories.first()
            if (categories.isEmpty() && !prefs.getBoolean("default_categories_populated", false)) {
                populateDefaultCategories()
                prefs.edit().putBoolean("default_categories_populated", true).apply()
            }
        }
    }

    private fun populateDefaultCategories() {
        viewModelScope.launch {
            repository.insertCategory(Category(name = "Salary", iconName = "💰"))
            repository.insertCategory(Category(name = "Bills", iconName = "🏠"))
            repository.insertCategory(Category(name = "Food", iconName = "☕"))
            repository.insertCategory(Category(name = "Shopping", iconName = "🛍️"))
            repository.insertCategory(Category(name = "Transport", iconName = "🚗"))
            repository.insertCategory(Category(name = "Entertainment", iconName = "🍿"))
        }
    }

    override fun onCleared() {
        super.onCleared()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }

    fun setThemeMode(mode: String) {
        if (mode in listOf("SYSTEM", "LIGHT", "DARK")) {
            _darkThemeMode.value = mode
            prefs.edit().putString("theme_mode", mode).apply()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _merchantQuery = MutableStateFlow("")
    val merchantQuery = _merchantQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("ALL")
    val selectedTypeFilter = _selectedTypeFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("ALL")
    val selectedCategoryFilter = _selectedCategoryFilter.asStateFlow()

    private val _startDateFilter = MutableStateFlow<Long?>(null)
    val startDateFilter = _startDateFilter.asStateFlow()

    private val _endDateFilter = MutableStateFlow<Long?>(null)
    val endDateFilter = _endDateFilter.asStateFlow()

    private val _activeCurrency = MutableStateFlow("INR")
    val activeCurrency = _activeCurrency.asStateFlow()

    private val _spendInsights = MutableStateFlow("")
    val spendInsights = _spendInsights.asStateFlow()

    private val _isAnalyzingSpend = MutableStateFlow(false)
    val isAnalyzingSpend = _isAnalyzingSpend.asStateFlow()

    private val _taxSavingInsights = MutableStateFlow("")
    val taxSavingInsights = _taxSavingInsights.asStateFlow()

    private val _isAnalyzingTax = MutableStateFlow(false)
    val isAnalyzingTax = _isAnalyzingTax.asStateFlow()

    // Multi-currency rates relative to USD (Predefined and robust)
    companion object {
        val currencySymbols = mapOf(
            "INR" to "₹",
            "USD" to "$",
            "EUR" to "€",
            "GBP" to "£",
            "JPY" to "¥"
        )

        fun convert(amount: Double, from: String, to: String): Double {
            if (from == to) return amount
            val usdRate = when (from) {
                "USD" -> 1.0
                "INR" -> 83.5
                "EUR" -> 0.92
                "GBP" -> 0.79
                "JPY" -> 158.0
                else -> 1.0
            }
            val amountInUsd = amount / usdRate
            val targetRate = when (to) {
                "USD" -> 1.0
                "INR" -> 83.5
                "EUR" -> 0.92
                "GBP" -> 0.79
                "JPY" -> 158.0
                else -> 1.0
            }
            return amountInUsd * targetRate
        }
    }

    val searchAndAdvisorState = combine(
        combine(
            _searchQuery,
            _merchantQuery,
            _selectedTypeFilter,
            _selectedCategoryFilter,
            combine(_activeCurrency, combine(_startDateFilter, _endDateFilter) { start, end -> Pair(start, end) }) { c, dates -> Pair(c, dates) }
        ) { q, mq, f, cat, rest ->
            val c = rest.first
            val dates = rest.second
            FilterState(q, mq, f, cat, dates.first, dates.second, c)
        },
        combine(_spendInsights, _isAnalyzingSpend, _taxSavingInsights, _isAnalyzingTax) { s, isS, t, isT ->
            AdvisorState(s, isS, t, isT)
        }
    ) { search, advisor -> Pair(search, advisor) }

    val searchAndAdvisorAndLoading = combine(searchAndAdvisorState, _isLoading) { sa, loading -> Pair(sa, loading) }

    val uiState: StateFlow<FinanceUiState> = combine(
        repository.allTransactions,
        repository.allGoals,
        repository.allBudgets,
        repository.allCategories,
        searchAndAdvisorAndLoading
    ) { rawTransactions, goals, budgets, categories, sal ->
        val searchState = sal.first.first
        val advisorState = sal.first.second
        val isLoading = sal.second
        
        val (query, merchantQuery, filter, categoryFilter, startDate, endDate, currency) = searchState
        val (insights, isS, taxIns, isT) = advisorState


        // Convert all transactions to active currency for summaries
        val convertedIncome = rawTransactions.filter { it.type == "INCOME" }.sumOf { 
            convert(it.amount, it.currency, currency)
        }
        val convertedExpense = rawTransactions.filter { it.type == "EXPENSE" }.sumOf { 
            convert(it.amount, it.currency, currency)
        }
        
        // Calculate Credit Card spending separately
        val creditCardSpending = rawTransactions
            .filter { it.type == "EXPENSE" && it.paymentMethod == "CREDIT_CARD" }
            .sumOf { convert(it.amount, it.currency, currency) }

        // Liquid balance excludes Credit Card expenses (as requested)
        val totalBalance = convertedIncome - (convertedExpense - creditCardSpending)

        // Category breakdown in active currency
        val categoryBreakdown = rawTransactions
            .filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { convert(it.amount, it.currency, currency) } }

        // Filter transactions (keep raw currencies but check query against title, category, notes, bank, etc.)
        val filtered = rawTransactions.filter { transaction ->
            val matchesFilter = when (filter) {
                "INCOME" -> transaction.type == "INCOME"
                "EXPENSE" -> transaction.type == "EXPENSE"
                else -> true
            }
            
            val matchesCategory = if (categoryFilter == "ALL") true else transaction.category == categoryFilter

            val matchesQuery = transaction.title.contains(query, ignoreCase = true) ||
                    transaction.category.contains(query, ignoreCase = true) ||
                    transaction.notes.contains(query, ignoreCase = true) ||
                    (transaction.creditCardBank != null && transaction.creditCardBank.contains(query, ignoreCase = true))

            val matchesMerchant = merchantQuery.isBlank() || transaction.title.contains(merchantQuery, ignoreCase = true)

            val matchesDate = (startDate == null || transaction.timestamp >= startDate) &&
                              (endDate == null || transaction.timestamp <= endDate)

            matchesFilter && matchesCategory && matchesQuery && matchesMerchant && matchesDate
        }

        // NEW: Financial Health Score & Runway Calculation
        val monthlyExpense = convertedExpense // Simplified to total for now, ideally per month
        val dailyBurn = monthlyExpense / 30.0
        val runway = if (dailyBurn > 0) (totalBalance / dailyBurn).toInt().coerceAtMost(365) else 365
        
        // Health score heuristic: Savings Rate + Runway Factor + Debt Ratio
        val savingsRate = if (convertedIncome > 0) (convertedIncome - convertedExpense) / convertedIncome else 0.0
        val savingsScore = (savingsRate * 100).coerceIn(0.0, 40.0) // Max 40 points for savings
        val runwayScore = (runway / 90.0 * 30.0).coerceIn(0.0, 30.0) // Max 30 points for 3 months runway
        val debtRatio = if (convertedIncome > 0) creditCardSpending / convertedIncome else 0.0
        val debtScore = (30.0 - (debtRatio * 30.0)).coerceIn(0.0, 30.0) // Max 30 points for no CC debt
        
        val totalHealthScore = (savingsScore + runwayScore + debtScore).toInt().coerceIn(0, 100)

        // Subscription Watch + Cash Flow Forecast — pure local-ledger analysis,
        // no network calls or new permissions required.
        val nowMillis = System.currentTimeMillis()
        val subscriptionAlerts = com.example.data.SubscriptionDetector.detect(rawTransactions, nowMillis)
        val cashFlowForecast = com.example.data.CashFlowForecaster.forecast(
            transactions = rawTransactions,
            currentBalance = totalBalance,
            activeCurrency = currency,
            nowMillis = nowMillis,
            convert = { amount, from, to -> convert(amount, from, to) }
        )

        // Round-Up Savings: round every expense up to the nearest unit
        // (₹10 for INR, since whole-rupee amounts are common and a
        // nearest-₹1 round-up would net near zero; nearest 1 unit for
        // other currencies), minus whatever's already been swept to a
        // goal (tracked in prefs, since Goal itself has no "source" ledger).
        val roundUpBase = if (currency == "INR") 10.0 else 1.0
        val totalRoundUp = rawTransactions.filter { it.type == "EXPENSE" }.sumOf { txn ->
            val amt = convert(txn.amount, txn.currency, currency)
            val remainder = amt % roundUpBase
            if (remainder <= 0.0001) 0.0 else roundUpBase - remainder
        }
        val sweptRoundUp = prefs.getString("swept_roundup_total", "0")?.toDoubleOrNull() ?: 0.0
        val pendingRoundUp = (totalRoundUp - sweptRoundUp).coerceAtLeast(0.0)

        FinanceUiState(
            transactions = rawTransactions,
            filteredTransactions = filtered,
            totalBalance = totalBalance,
            totalIncome = convertedIncome,
            totalExpense = convertedExpense,
            creditCardSpending = creditCardSpending,
            categoryBreakdown = categoryBreakdown,
            searchQuery = query,
            merchantQuery = merchantQuery,
            selectedTypeFilter = filter,
            selectedCategoryFilter = categoryFilter,
            startDate = startDate,
            endDate = endDate,
            activeCurrency = currency,
            spendInsights = insights,
            isAnalyzingSpend = isS,
            taxSavingInsights = taxIns,
            isAnalyzingTax = isT,
            healthScore = totalHealthScore,
            runwayDays = runway,
            goals = goals,
            budgets = budgets,
            categories = categories,
            isLoading = isLoading,
            subscriptions = subscriptionAlerts,
            cashFlowForecast = cashFlowForecast,
            pendingRoundUpTotal = pendingRoundUp
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateMerchantQuery(query: String) {
        _merchantQuery.value = query
    }

    fun updateTypeFilter(filter: String) {
        _selectedTypeFilter.value = filter
        // Reset category filter when switching between income/expense if necessary
        // Or keep it "ALL"
        _selectedCategoryFilter.value = "ALL"
    }

    fun updateCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun updateStartDate(date: Long?) {
        _startDateFilter.value = date
    }

    fun updateEndDate(date: Long?) {
        _endDateFilter.value = date
    }

    fun changeActiveCurrency(newCurrency: String) {
        _activeCurrency.value = newCurrency
    }

    // Comprehensive transaction insertion with all advanced properties
    fun addTransaction(
        title: String,
        amount: Double,
        category: String,
        type: String,
        notes: String = "",
        paymentMethod: String = "CASH",
        creditCardBank: String? = null,
        isRecurring: Boolean = false,
        currency: String = "INR",
        scheduledDay: Int? = null
    ): Boolean {
        if (title.trim().isEmpty() || amount <= 0) {
            return false
        }
        viewModelScope.launch {
            val transaction = Transaction(
                title = title.trim(),
                amount = amount,
                category = category,
                timestamp = System.currentTimeMillis(),
                type = type,
                notes = notes.trim(),
                paymentMethod = paymentMethod,
                creditCardBank = creditCardBank,
                isRecurring = isRecurring,
                recurrenceInterval = if (isRecurring) "MONTHLY" else null,
                currency = currency,
                scheduledDayOfMonth = scheduledDay
            )
            repository.insert(transaction)
            
            // Trigger automatic cloud backup if logged in
            if (_isUserLoggedIn.value) {
                try {
                    val all = repository.allTransactions.first()
                    val success = CloudSyncManager.backupToCloud(application, all)
                    if (success) {
                        _lastSyncTime.value = CloudSyncManager.getLastSyncTime(application)
                    } else {
                        _syncStatusMessage.value = "Auto-backup failed."
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Auto-sync failed: ${e.message}")
                    _syncStatusMessage.value = "Auto-backup error: ${e.message}"
                }
            }
        }
        return true
    }

    fun updateTransaction(
        id: Int,
        title: String,
        amount: Double,
        category: String,
        type: String,
        notes: String = "",
        paymentMethod: String = "CASH",
        creditCardBank: String? = null,
        isRecurring: Boolean = false,
        currency: String = "INR",
        scheduledDay: Int? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        if (title.trim().isEmpty() || amount <= 0) {
            return false
        }
        viewModelScope.launch {
            val transaction = Transaction(
                id = id,
                title = title.trim(),
                amount = amount,
                category = category,
                timestamp = timestamp,
                type = type,
                notes = notes.trim(),
                paymentMethod = paymentMethod,
                creditCardBank = creditCardBank,
                isRecurring = isRecurring,
                recurrenceInterval = if (isRecurring) "MONTHLY" else null,
                currency = currency,
                scheduledDayOfMonth = scheduledDay
            )
            repository.insert(transaction)
            
            // Trigger automatic cloud backup if logged in
            if (_isUserLoggedIn.value) {
                try {
                    val all = repository.allTransactions.first()
                    val success = CloudSyncManager.backupToCloud(application, all)
                    if (success) {
                        _lastSyncTime.value = CloudSyncManager.getLastSyncTime(application)
                    } else {
                        _syncStatusMessage.value = "Auto-backup failed."
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Auto-sync failed: ${e.message}")
                    _syncStatusMessage.value = "Auto-backup error: ${e.message}"
                }
            }
        }
        return true
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.delete(transaction)
            
            // Trigger automatic cloud backup if logged in
            if (_isUserLoggedIn.value) {
                try {
                    val all = repository.allTransactions.first()
                    val success = CloudSyncManager.backupToCloud(application, all)
                    if (success) {
                        _lastSyncTime.value = CloudSyncManager.getLastSyncTime(application)
                    } else {
                        _syncStatusMessage.value = "Auto-backup failed."
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FinanceViewModel", "Auto-sync failed: ${e.message}")
                    _syncStatusMessage.value = "Auto-backup error: ${e.message}"
                }
            }
        }
    }

    // NEW Goal Methods
    fun addGoal(name: String, target: Double, deadline: Long?, icon: String = "🎯") {
        viewModelScope.launch {
            repository.insertGoal(Goal(name = name, targetAmount = target, deadlineTimestamp = deadline, iconEmoji = icon))
        }
    }

    fun updateGoalProgress(goal: Goal, saved: Double) {
        viewModelScope.launch {
            repository.updateGoal(goal.copy(savedAmount = saved))
        }
    }

    // Sweeps the current pendingRoundUpTotal into the chosen goal's saved
    // amount, then records the swept amount in prefs so it isn't offered
    // again (pendingRoundUpTotal is always "total round-up minus already
    // swept", recomputed live in the uiState combine chain above).
    fun sweepRoundUpToGoal(goal: Goal) {
        val pending = uiState.value.pendingRoundUpTotal
        if (pending <= 0.0) return
        viewModelScope.launch {
            repository.updateGoal(goal.copy(savedAmount = goal.savedAmount + pending))
            val current = prefs.getString("swept_roundup_total", "0")?.toDoubleOrNull() ?: 0.0
            prefs.edit().putString("swept_roundup_total", (current + pending).toString()).apply()
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }

    // NEW Budget Methods
    fun setBudget(category: String, limit: Double) {
        viewModelScope.launch {
            // Check if budget for category already exists
            val existing = uiState.value.budgets.find { it.category == category }
            if (existing != null) {
                repository.insertBudget(existing.copy(monthlyLimit = limit))
            } else {
                repository.insertBudget(Budget(category = category, monthlyLimit = limit, currency = uiState.value.activeCurrency))
            }
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    fun addCategory(name: String, icon: String = "ic_default") {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, iconName = icon))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun signInUser(email: String, name: String, password: String = "") {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Signing in & connecting to Google Cloud..."
            
            try {
                val auth = FirebaseAuth.getInstance()
                var firebaseUser = auth.currentUser
                if (firebaseUser == null || firebaseUser.email != email) {
                    try {
                        val authResult = auth.signInWithEmailAndPassword(email, password).await()
                        firebaseUser = authResult.user
                    } catch (e: Exception) {
                        try {
                            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                            firebaseUser = authResult.user
                            
                            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build()
                            firebaseUser?.updateProfile(profileUpdates)?.await()
                        } catch (signUpException: Exception) {
                            throw signUpException
                        }
                    }
                }
                
                if (firebaseUser != null) {
                    CloudSyncManager.setLoggedIn(application, email, name)
                    _isUserLoggedIn.value = true
                    _userEmail.value = email
                    _userName.value = name

                    isInitialSyncCompleted.value = false

                    val cloudTransactions = CloudSyncManager.restoreFromCloud(application)
                    if (cloudTransactions != null) {
                        if (cloudTransactions.isNotEmpty()) {
                            cloudTransactions.forEach { tx ->
                                repository.insert(tx)
                            }
                            _syncStatusMessage.value = "Welcome back, $name! Restored ${cloudTransactions.size} transactions from Google Cloud."
                        } else {
                            val localTransactions = repository.allTransactions.first()
                            if (localTransactions.isNotEmpty()) {
                                val success = CloudSyncManager.backupToCloud(application, localTransactions)
                                if (success) {
                                    _syncStatusMessage.value = "Account linked! Current local ledger (${localTransactions.size} items) backed up to Cloud."
                                } else {
                                    _syncStatusMessage.value = "Account linked! (Backup failed - will retry automatically)"
                                }
                            } else {
                                _syncStatusMessage.value = "Welcome! You're now connected to Google Cloud for automatic backups."
                            }
                        }
                        _lastSyncTime.value = CloudSyncManager.getLastSyncTime(application)
                    } else {
                        _syncStatusMessage.value = "Connected to Google Cloud."
                    }
                } else {
                    _syncStatusMessage.value = "Authentication failed. Please check your credentials."
                }
            } catch (e: Exception) {
                _syncStatusMessage.value = "Error connecting: ${e.message}"
            }
            isInitialSyncCompleted.value = true
            _isSyncing.value = false
        }
    }

    fun signOutUser() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "Clearing secure local session..."
            
            CloudSyncManager.setLoggedOut(application)
            _isUserLoggedIn.value = false
            _userEmail.value = null
            _userName.value = null
            _lastSyncTime.value = 0L
            isInitialSyncCompleted.value = false
            
            _syncStatusMessage.value = "Logged out successfully. All local session data cleared."
            _isSyncing.value = false
        }
    }

    fun forceBackupAndSync() {
        viewModelScope.launch {
            if (!_isUserLoggedIn.value) {
                _syncStatusMessage.value = "Please sign in to sync."
                return@launch
            }
            _isSyncing.value = true
            _syncStatusMessage.value = "Uploading database to Google Cloud..."
            
            val localTransactions = repository.allTransactions.first()
            val success = CloudSyncManager.backupToCloud(application, localTransactions)
            if (success) {
                _syncStatusMessage.value = "Cloud Backup Successful: ${localTransactions.size} items synced."
                _lastSyncTime.value = CloudSyncManager.getLastSyncTime(application)
            } else {
                _syncStatusMessage.value = "Cloud Backup Failed. Check your network or credentials."
            }
            _isSyncing.value = false
        }
    }

    fun forceRestoreFromCloud() {
        viewModelScope.launch {
            if (!_isUserLoggedIn.value) {
                _syncStatusMessage.value = "Please sign in to restore."
                return@launch
            }
            _isSyncing.value = true
            _syncStatusMessage.value = "Restoring ledger from Google Cloud..."
            
            val cloudTransactions = CloudSyncManager.restoreFromCloud(application)
            if (cloudTransactions != null) {
                // Merge cloud data into local
                cloudTransactions.forEach { tx ->
                    repository.insert(tx)
                }
                _syncStatusMessage.value = "Cloud Restore Successful: Retrieved ${cloudTransactions.size} items."
                _lastSyncTime.value = CloudSyncManager.getLastSyncTime(application)
            } else {
                _syncStatusMessage.value = "Restore Failed. Database might be empty or unreachable."
            }
            _isSyncing.value = false
        }
    }

    fun clearSyncStatusMessage() {
        _syncStatusMessage.value = null
    }

    // Trigger AI Spend Insights
    fun generateSpendInsights() {
        val currentTransactions = uiState.value.transactions
        viewModelScope.launch {
            _isAnalyzingSpend.value = true
            val response = GeminiManager.getSpendInsights(currentTransactions)
            _spendInsights.value = response
            _isAnalyzingSpend.value = false
        }
    }

    // Trigger AI Tax Planning
    fun generateTaxSavingInsights(
        income: Double,
        deductions80C: Double,
        healthInsurance: Double,
        otherDeductions: Double
    ) {
        viewModelScope.launch {
            _isAnalyzingTax.value = true
            val response = GeminiManager.getTaxSavingInsights(
                income = income,
                deductions80C = deductions80C,
                healthInsurance = healthInsurance,
                otherDeductions = otherDeductions
            )
            _taxSavingInsights.value = response
            _isAnalyzingTax.value = false
        }
    }

    // Quick QR scan parsing for UPI (India payment standard)
    fun scanQrCode(qrData: String, onParsed: (title: String, amount: Double, currency: String, category: String) -> Unit): Boolean {
        return try {
            // UPI URIs format: upi://pay?pa=address&pn=Merchant%20Name&am=Amount&cu=INR&tn=Notes
            if (qrData.startsWith("upi://", ignoreCase = true)) {
                val cleanedUri = qrData.substringAfter("upi://pay?")
                val params = cleanedUri.split("&").associate { 
                    val parts = it.split("=")
                    parts.getOrNull(0) to java.net.URLDecoder.decode(parts.getOrNull(1) ?: "", "UTF-8")
                }
                
                val merchantName = params["pn"] ?: params["pa"] ?: "UPI Merchant"
                val amount = params["am"]?.toDoubleOrNull() ?: 0.0
                val currency = params["cu"] ?: "INR"
                val notes = params["tn"] ?: "UPI Payment"
                
                // Smart category guesser
                val category = when {
                    merchantName.contains("coffee", true) || merchantName.contains("starbucks", true) || merchantName.contains("food", true) || merchantName.contains("restaurant", true) -> "Food"
                    merchantName.contains("power", true) || merchantName.contains("electricity", true) || merchantName.contains("water", true) || merchantName.contains("bescom", true) -> "Bills & Utilities"
                    merchantName.contains("loan", true) || merchantName.contains("bank", true) || merchantName.contains("emi", true) -> "Personal Loan"
                    merchantName.contains("amazon", true) || merchantName.contains("flipkart", true) || merchantName.contains("zara", true) || merchantName.contains("mall", true) -> "Shopping"
                    else -> "Other"
                }

                onParsed(merchantName, amount, currency, category)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

class FinanceViewModelFactory(
    private val repository: TransactionRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
