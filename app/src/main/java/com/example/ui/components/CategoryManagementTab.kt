package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        // Horizontal icon-tile grid — each category gets its own colored
        // icon (reusing the same CategoryHelper icons/colors shown
        // elsewhere in the app, e.g. the Add Transaction category picker)
        // instead of a plain vertical name-only list.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.categories, key = { it.name }) { category ->
                val categoryColor = remember(category.name) { CategoryHelper.getColor(category.name) }
                val categoryIcon = remember(category.name) { CategoryHelper.getIcon(category.name) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor.copy(alpha = 0.10f))
                        .border(width = 2.dp, color = categoryColor, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("category_tile_${category.name}")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(categoryColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = category.name,
                                tint = categoryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = category.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Delete button pinned to the top-right corner of the tile
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable { viewModel.deleteCategory(category) }
                            .testTag("delete_category_${category.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Delete ${category.name}",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
