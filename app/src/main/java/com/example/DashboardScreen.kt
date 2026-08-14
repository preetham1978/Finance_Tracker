package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.ui.components.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.FinanceViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    providedViewModel: FinanceViewModel? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as FinanceApplication
    val viewModel = providedViewModel ?: remember(context) {
        val owner = context as? ViewModelStoreOwner
            ?: throw IllegalStateException("Context must be a ViewModelStoreOwner")
        ViewModelProvider(owner, FinanceViewModelFactory(app.repository, app))[FinanceViewModel::class.java]
    }
    val uiState by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    
    // Bottom Tab State
    var selectedTab by remember { mutableStateOf(0) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    val themeMode by viewModel.darkThemeMode.collectAsState()
    
    var showCloudDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    val isCloudLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    var transactionToDelete by remember { mutableStateOf<com.example.data.Transaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<com.example.data.Transaction?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Deletion confirmation dialog
    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this transaction? This will permanently remove the record of '${transactionToDelete?.title}' from your dashboard and update your balance accordingly. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        transactionToDelete?.let { transaction ->
                            viewModel.deleteTransaction(transaction)
                            scope.launch {
                                snackbarHostState.showSnackbar("Transaction '${transaction.title}' deleted")
                            }
                        }
                        transactionToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Minimalist premium geometric brand emblem
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalance,
                                    contentDescription = "Vantage Emblem",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Vantage",
                                fontWeight = FontWeight.Black,
                                fontSize = 21.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        
                        // Display currency fast-switch indicator and Theme switcher in TopBar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Currency Selector
                            Box {
                                TextButton(
                                    onClick = { currencyMenuExpanded = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${uiState.activeCurrency} (${FinanceViewModel.currencySymbols[uiState.activeCurrency]})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "Select Display Currency",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                DropdownMenu(
                                    expanded = currencyMenuExpanded,
                                    onDismissRequest = { currencyMenuExpanded = false }
                                ) {
                                    listOf("INR", "USD", "EUR", "GBP", "JPY").forEach { cur ->
                                        DropdownMenuItem(
                                            text = { Text("$cur (${FinanceViewModel.currencySymbols[cur]})", fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                viewModel.changeActiveCurrency(cur)
                                                currencyMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Google Cloud Backup & Sync status button
                            IconButton(
                                onClick = { showCloudDialog = true },
                                modifier = Modifier.testTag("cloud_sync_button")
                            ) {
                                Icon(
                                    imageVector = if (isCloudLoggedIn) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                                    contentDescription = "Google Cloud Backup Status",
                                    tint = if (isCloudLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }

                            // Theme Selector
                            Box {
                                val themeIcon = when (themeMode) {
                                    "DARK" -> Icons.Filled.DarkMode
                                    "LIGHT" -> Icons.Filled.LightMode
                                    else -> Icons.Filled.SettingsBrightness
                                }

                                IconButton(
                                    onClick = { themeMenuExpanded = true },
                                    modifier = Modifier.testTag("theme_switcher_button")
                                ) {
                                    Icon(
                                        imageVector = themeIcon,
                                        contentDescription = "Switch Theme Mode",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                DropdownMenu(
                                    expanded = themeMenuExpanded,
                                    onDismissRequest = { themeMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.SettingsBrightness, contentDescription = "System")
                                                Text("System Default", fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setThemeMode("SYSTEM")
                                            themeMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.LightMode, contentDescription = "Light")
                                                Text("Light Mode", fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setThemeMode("LIGHT")
                                            themeMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.DarkMode, contentDescription = "Dark")
                                                Text("Dark Mode", fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setThemeMode("DARK")
                                            themeMenuExpanded = false
                                        }
                                    )
                                }
                            }

                            // Profile Button
                            IconButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.testTag("profile_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            // Logout Button
                            IconButton(
                                onClick = {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                },
                                modifier = Modifier.testTag("logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Logout,
                                    contentDescription = "Logout",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Dashboard") },
                    label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Receipt, contentDescription = "AI Bill Scan") },
                    label = { Text("Scan", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.CreditCard, contentDescription = "Cards & Loans") },
                    label = { Text("Cards", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Stars, contentDescription = "Goals") },
                    label = { Text("Goals", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Filled.Calculate, contentDescription = "Tax Planner") },
                    label = { Text("Tax", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Insights") },
                    label = { Text("Advisor", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = selectedTab == 6,
                    onClick = { selectedTab = 6 },
                    icon = { Icon(Icons.Filled.Category, contentDescription = "Categories") },
                    label = { Text("Categories", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    alwaysShowLabel = false
                )
            }
        },
        floatingActionButton = {
            // Only show FAB on Dashboard ledger tab
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        transactionToEdit = null
                        showAddSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_transaction_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Transaction")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    // Ledger Dashboard Tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("dashboard_lazy_column"),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 0. Hero Branding Header with Logo and Punchline
                        item {
                            VantageBrandingHeader()
                        }

                        // 1. Dual Financial Pools (Salary vs Credit)
                        item {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val isWide = maxWidth > 600.dp
                                if (isWide) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Max),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        BalanceCard(
                                            totalBalance = uiState.totalBalance,
                                            totalIncome = uiState.totalIncome,
                                            totalExpense = uiState.totalExpense,
                                            creditCardSpending = uiState.creditCardSpending,
                                            activeCurrency = uiState.activeCurrency,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .testTag("dashboard_balance_card")
                                        )
                                        CreditCardDashboardCard(
                                            creditCardSpending = uiState.creditCardSpending,
                                            activeCurrency = uiState.activeCurrency,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .testTag("dashboard_cc_card")
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        BalanceCard(
                                            totalBalance = uiState.totalBalance,
                                            totalIncome = uiState.totalIncome,
                                            totalExpense = uiState.totalExpense,
                                            creditCardSpending = uiState.creditCardSpending,
                                            activeCurrency = uiState.activeCurrency,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("dashboard_balance_card")
                                        )
                                        CreditCardDashboardCard(
                                            creditCardSpending = uiState.creditCardSpending,
                                            activeCurrency = uiState.activeCurrency,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("dashboard_cc_card")
                                        )
                                    }
                                }
                            }
                        }

                        // Monthly Summary Card
                        item {
                            MonthlySummaryCard(
                                transactions = uiState.transactions,
                                activeCurrency = uiState.activeCurrency
                            )
                        }

                        // NEW: Budgeting Section
                        if (uiState.budgets.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Active Budgets",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    uiState.budgets.forEach { b ->
                                        val spent = uiState.categoryBreakdown[b.category] ?: 0.0
                                        BudgetCard(
                                            category = b.category,
                                            limit = b.monthlyLimit,
                                            spent = spent,
                                            activeCurrency = uiState.activeCurrency
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Category Distribution Donut Chart
                        item {
                            DonutChart(
                                categoryBreakdown = uiState.categoryBreakdown,
                                activeCurrency = uiState.activeCurrency,
                                modifier = Modifier.testTag("dashboard_donut_chart")
                            )
                        }

                        // Vantage Tools & Statement Parser Card
                        item {
                            StatementAnalyzerCard(viewModel = viewModel)
                        }

                        // 3. Search and Filter Sticky controls
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Modern Search Bar
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    placeholder = { Text("Search title, category, notes, bank...", fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                                    },
                                    trailingIcon = {
                                        if (uiState.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("search_bar")
                                )

                                // Dedicated Real-Time Merchant Name Search Input Field
                                OutlinedTextField(
                                    value = uiState.merchantQuery,
                                    onValueChange = { viewModel.updateMerchantQuery(it) },
                                    placeholder = { Text("Search by merchant name...", fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Store, contentDescription = "Merchant Search", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    },
                                    trailingIcon = {
                                        if (uiState.merchantQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.updateMerchantQuery("") }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Clear Merchant", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("merchant_search_bar")
                                )

                                // Custom Capsule Filter Tabs
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("ALL", "INCOME", "EXPENSE").forEach { filterType ->
                                        val isSelected = uiState.selectedTypeFilter == filterType
                                        val backgroundColor = if (isSelected) {
                                            when (filterType) {
                                                "INCOME" -> Color(0xFFE8F5E9)
                                                "EXPENSE" -> Color(0xFFFFEBEE)
                                                else -> MaterialTheme.colorScheme.primaryContainer
                                            }
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                        val contentColor = if (isSelected) {
                                            when (filterType) {
                                                "INCOME" -> Color(0xFF2E7D32)
                                                "EXPENSE" -> Color(0xFFC62828)
                                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                                            }
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(backgroundColor)
                                                .clickable { viewModel.updateTypeFilter(filterType) }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .testTag("filter_chip_$filterType"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = filterType,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = contentColor
                                            )
                                        }
                                    }
                                }
                                
                                // Category Filters
                                val availableCategories = when (uiState.selectedTypeFilter) {
                                    "INCOME" -> listOf("ALL") + CategoryHelper.incomeCategories
                                    "EXPENSE" -> listOf("ALL") + CategoryHelper.expenseCategories
                                    else -> listOf("ALL") + CategoryHelper.incomeCategories + CategoryHelper.expenseCategories
                                }.distinct()

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(availableCategories) { category ->
                                        val isSelected = uiState.selectedCategoryFilter == category
                                        
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.updateCategoryFilter(category) },
                                            label = { 
                                                Text(
                                                    text = category,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                ) 
                                            },
                                            leadingIcon = if (category != "ALL") {
                                                {
                                                    Icon(
                                                        imageVector = CategoryHelper.getIcon(category),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            } else null,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            border = if (!isSelected) {
                                                FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = false,
                                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                )
                                            } else null,
                                            modifier = Modifier.testTag("category_filter_$category")
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Section Header for List
                        item {
                            Text(
                                text = "Transactions History",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // 5. Empty State / Transactions List
                        if (uiState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (uiState.filteredTransactions.isEmpty()) {
                            item {
                                EmptyState(
                                    text = if (uiState.searchQuery.isNotEmpty() || uiState.merchantQuery.isNotEmpty()) "No results found. Try searching for another keyword or merchant." else "Tap '+' to add your first transaction!"
                                )
                            }
                        } else {
                            items(
                                items = uiState.filteredTransactions,
                                key = { it.id }
                            ) { transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    onDelete = { transactionToDelete = transaction },
                                    onEdit = {
                                        transactionToEdit = transaction
                                        showAddSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> ScannerTab(viewModel)
                2 -> CardsLoansTab(viewModel)
                3 -> GoalsTab(
                    uiState = uiState,
                    onAddGoal = { name, target, deadline, icon -> viewModel.addGoal(name, target, deadline, icon) },
                    onUpdateProgress = { goal, saved -> viewModel.updateGoalProgress(goal, saved) },
                    onDeleteGoal = { goal -> viewModel.deleteGoal(goal) }
                )
                4 -> TaxPlannerTab(viewModel)
                5 -> AIAdvisorTab(viewModel)
                6 -> CategoryManagementTab(viewModel)
            }
        }
    }

    // Modal Sheet for adding/editing transactions
    AnimatedVisibility(
        visible = showAddSheet,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        AddTransactionSheet(
            onDismiss = { 
                showAddSheet = false
                transactionToEdit = null
            },
            isEdit = transactionToEdit != null,
            initialTitle = transactionToEdit?.title ?: "",
            initialAmount = transactionToEdit?.amount ?: 0.0,
            initialCurrency = transactionToEdit?.currency ?: uiState.activeCurrency,
            initialCategory = transactionToEdit?.category,
            initialNotes = transactionToEdit?.notes ?: "",
            initialType = transactionToEdit?.type ?: "EXPENSE",
            initialPaymentMethod = transactionToEdit?.paymentMethod ?: "UPI",
            initialCreditCardBank = transactionToEdit?.creditCardBank,
            initialIsRecurring = transactionToEdit?.isRecurring ?: false,
            initialScheduledDay = transactionToEdit?.scheduledDayOfMonth,
            onSave = { title, amount, category, type, notes, method, cardBank, isRec, cur, scheduledDay ->
                val currentEdit = transactionToEdit
                if (currentEdit != null) {
                    viewModel.updateTransaction(
                        id = currentEdit.id,
                        title = title,
                        amount = amount,
                        category = category,
                        type = type,
                        notes = notes,
                        paymentMethod = method,
                        creditCardBank = cardBank,
                        isRecurring = isRec,
                        currency = cur,
                        scheduledDay = scheduledDay,
                        timestamp = currentEdit.timestamp
                    )
                } else {
                    viewModel.addTransaction(
                        title = title,
                        amount = amount,
                        category = category,
                        type = type,
                        notes = notes,
                        paymentMethod = method,
                        creditCardBank = cardBank,
                        isRecurring = isRec,
                        currency = cur,
                        scheduledDay = scheduledDay
                    )
                }
                showAddSheet = false
                transactionToEdit = null
            }
        )
    }

    // Google Cloud Sync & Auth Dialog
    if (showCloudDialog) {
        GoogleCloudDialog(
            viewModel = viewModel,
            onDismiss = { showCloudDialog = false }
        )
    }

    // Edit Profile Dialog
    if (showProfileDialog) {
        EditProfileDialog(
            onDismiss = { showProfileDialog = false }
        )
    }
}
