package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

@Composable
fun EditProfileDialog(
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val user = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(user?.uid) {
        if (user != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users").document(user.uid).get().await()
                if (doc.exists()) {
                    name = doc.getString("name") ?: ""
                    mobile = doc.getString("mobile") ?: ""
                }
            } catch (e: Exception) {
                errorMessage = "Failed to load profile."
            }
            isLoading = false
        } else {
            // No signed-in user - stop the spinner instead of leaving Save/Cancel disabled
            // forever. This shouldn't happen in normal use (the dialog is only opened from a
            // screen that requires being logged in), but nothing here enforced that invariant.
            errorMessage = "You're not signed in. Please sign in and try again."
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it
                            errorMessage = null
                            successMessage = null
                        },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { 
                            mobile = it
                            errorMessage = null
                            successMessage = null
                        },
                        label = { Text("Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (successMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(successMessage!!, color = androidx.compose.ui.graphics.Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank() || mobile.isBlank()) {
                        errorMessage = "Fields cannot be empty."
                        return@TextButton
                    }
                    if (!mobile.matches(Regex("^[+]?[0-9]{10,13}\$"))) {
                        errorMessage = "Please enter a valid mobile number."
                        return@TextButton
                    }
                    
                    if (user != null) {
                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val db = FirebaseFirestore.getInstance()
                                db.collection("users").document(user.uid).update(
                                    mapOf(
                                        "name" to name,
                                        "mobile" to mobile
                                    )
                                ).await()
                                
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build()
                                user.updateProfile(profileUpdates).await()
                                
                                isLoading = false
                                successMessage = "Profile updated successfully."
                                kotlinx.coroutines.delay(1000)
                                onDismiss()
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Failed to update profile: ${e.message}"
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}
