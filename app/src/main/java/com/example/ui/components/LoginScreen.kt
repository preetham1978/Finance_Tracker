package com.example.ui.components

import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    
    val diagnosticLogs = remember { mutableStateListOf<String>() }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var showResetConfirmationDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUp) "Create Account" else "Vantage Finance Login",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isSignUp) {
            OutlinedTextField(
                value = name,
                onValueChange = { 
                    name = it
                    errorMessage = null
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
                },
                label = { Text("Mobile") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                errorMessage = null 
            },
            label = { Text("Email Address") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    val trimmedEmail = email.trim()
                    if (isSignUp) {
                        if (name.isBlank()) {
                            errorMessage = "Please enter your name."
                            return@Button
                        }
                        if (mobile.isBlank()) {
                            errorMessage = "Please enter your mobile number."
                            return@Button
                        }
                        if (!mobile.matches(Regex("^[+]?[0-9]{10,13}\$"))) {
                            errorMessage = "Please enter a valid mobile number."
                            return@Button
                        }
                    }
                    if (trimmedEmail.isBlank()) {
                        errorMessage = "Please enter an email address."
                        return@Button
                    }
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                        errorMessage = "Please enter a valid email address format (e.g. user@example.com)."
                        return@Button
                    }
                    if (password.isBlank()) {
                        errorMessage = "Please enter a password."
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters long."
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = null
                    
                    val auth = FirebaseAuth.getInstance()
                    val task = if (isSignUp) {
                        auth.createUserWithEmailAndPassword(trimmedEmail, password)
                    } else {
                        auth.signInWithEmailAndPassword(trimmedEmail, password)
                    }
                    task.addOnCompleteListener { taskResult ->
                        if (taskResult.isSuccessful) {
                            if (isSignUp) {
                                val user = taskResult.result.user
                                if (user != null) {
                                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build()
                                    user.updateProfile(profileUpdates)
                                    
                                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    val userData = hashMapOf(
                                        "name" to name,
                                        "email" to email,
                                        "mobile" to mobile
                                    )
                                    db.collection("users").document(user.uid).set(userData)
                                }
                                android.widget.Toast.makeText(context, "Account created successfully.", android.widget.Toast.LENGTH_SHORT).show()
                                isLoading = false
                                coroutineScope.launch {
                                    delay(1500)
                                    onLoginSuccess()
                                }
                            } else {
                                isLoading = false
                                onLoginSuccess()
                            }
                        } else {
                            isLoading = false
                            val exception = taskResult.exception
                            val stackTrace = exception?.stackTraceToString() ?: "No stack trace available"
                            diagnosticLogs.add(0, "Error: ${exception?.message}\n\nStack Trace:\n$stackTrace")
                            errorMessage = exception?.message ?: "Operation failed."
                            showDiagnosticDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (isSignUp) "Sign Up" else "Login")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(onClick = { 
                isSignUp = !isSignUp 
                errorMessage = null
            }) {
                Text(if (isSignUp) "Already have an account? Login" else "Don't have an account? Sign Up")
            }
            
            if (!isSignUp) {
                TextButton(onClick = {
                    val trimmedEmail = email.trim()
                    if (trimmedEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                        errorMessage = "Please enter a valid email address to reset password."
                        return@TextButton
                    }
                    isLoading = true
                    FirebaseAuth.getInstance().sendPasswordResetEmail(trimmedEmail)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                showResetConfirmationDialog = true
                            } else {
                                val exception = task.exception
                                val stackTrace = exception?.stackTraceToString() ?: "No stack trace available"
                                diagnosticLogs.add(0, "Reset Error: ${exception?.message}\n\nStack Trace:\n$stackTrace")
                                errorMessage = "Reset failed: ${exception?.message}"
                                showDiagnosticDialog = true
                            }
                        }
                }) {
                    Text("Forgot Password?")
                }
            }
        }
        
        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        if (diagnosticLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { showDiagnosticDialog = true }) {
                Text("View Diagnostic Logs (${diagnosticLogs.size})", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    
    if (showDiagnosticDialog) {
        Dialog(onDismissRequest = { showDiagnosticDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Diagnostic Logs", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(diagnosticLogs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showDiagnosticDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showResetConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmationDialog = false },
            title = { Text("Email Sent") },
            text = { Text("A password reset email has been sent to ${email.trim()}. Please check your inbox.") },
            confirmButton = {
                TextButton(onClick = { showResetConfirmationDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
