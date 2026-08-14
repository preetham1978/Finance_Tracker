package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.Category
import com.example.ui.viewmodel.FinanceViewModel

@Composable
fun CategoryManagementTab(viewModel: FinanceViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var newCategoryName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Manage Categories", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = {
                    newCategoryName = it
                    errorMessage = null
                },
                label = { Text("New Category Name") },
                isError = errorMessage != null,
                modifier = Modifier.weight(1f).testTag("input_new_category_name")
            )
            IconButton(
                onClick = {
                    val trimmedName = newCategoryName.trim()
                    val isDuplicate = uiState.categories.any { it.name.equals(trimmedName, ignoreCase = true) }
                    when {
                        trimmedName.isBlank() -> {
                            errorMessage = "Please enter a category name."
                        }
                        isDuplicate -> {
                            errorMessage = "A category named \"$trimmedName\" already exists."
                        }
                        else -> {
                            viewModel.addCategory(trimmedName)
                            newCategoryName = ""
                            errorMessage = null
                        }
                    }
                },
                modifier = Modifier.testTag("add_category_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Category")
            }
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp).testTag("category_error_message")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(uiState.categories) { category ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.name)
                        IconButton(onClick = { viewModel.deleteCategory(category) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Category")
                        }
                    }
                }
            }
        }
    }
}
