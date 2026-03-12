package com.example.autotrip.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.R
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.AuthUiState
import com.example.autotrip.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    onSignupSelected: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var isLogin by remember { mutableStateOf(true) }
    val uiState by authViewModel.uiState.collectAsState()

    // React to success state — navigate away
    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                if (isLogin) onLoginSuccess() else onSignupSelected()
                authViewModel.resetState()
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(50.dp))

            // Logo
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.auto_trip_logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Auto Trip",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (isLogin) "Welcome back!" else "Create an account",
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(Modifier.height(30.dp))

            // White Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    AuthToggle(isLogin = isLogin) { isLogin = !isLogin }

                    Spacer(Modifier.height(24.dp))

                    // Show error if any
                    if (uiState is AuthUiState.Error) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = (uiState as AuthUiState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    val isLoading = uiState is AuthUiState.Loading

                    if (isLogin) {
                        LoginForm(
                            isLoading = isLoading,
                            onLogin = { email, password ->
                                authViewModel.login(email, password)
                            },
                            onMoveToSignup = {
                                authViewModel.resetState()
                                isLogin = false
                            }
                        )
                    } else {
                        SignupForm(
                            isLoading = isLoading,
                            onSignup = { fullName, email, password ->
                                authViewModel.signUp(fullName, email, password)
                            },
                            onMoveToLogin = {
                                authViewModel.resetState()
                                isLogin = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthToggle(isLogin: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = { if (!isLogin) onToggle() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLogin) MaterialTheme.colorScheme.primary else Color.LightGray
            )
        ) { Text("Login") }

        Button(
            onClick = { if (isLogin) onToggle() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!isLogin) MaterialTheme.colorScheme.primary else Color.LightGray
            )
        ) { Text("Sign Up") }
    }
}

@Composable
fun LoginForm(
    isLoading: Boolean,
    onLogin: (email: String, password: String) -> Unit,
    onMoveToSignup: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { onLogin(email, password) },
        modifier = Modifier.fillMaxWidth(),
        enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text("Login")
        }
    }

    Spacer(Modifier.height(16.dp))

    TextButton(onClick = onMoveToSignup, modifier = Modifier.fillMaxWidth()) {
        Text("Don't have an account? Sign Up", textAlign = TextAlign.Center)
    }
}

@Composable
fun SignupForm(
    isLoading: Boolean,
    onSignup: (fullName: String, email: String, password: String) -> Unit,
    onMoveToLogin: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    val passwordsMatch = password == confirmPassword || confirmPassword.isEmpty()

    OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Full Name") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null
                )
            }
        }
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it },
        label = { Text("Confirm Password") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        isError = !passwordsMatch,
        supportingText = {
            if (!passwordsMatch) Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
        },
        visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { confirmVisible = !confirmVisible }) {
                Icon(
                    if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null
                )
            }
        }
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { onSignup(fullName, email, password) },
        modifier = Modifier.fillMaxWidth(),
        enabled = fullName.isNotEmpty()
                && email.isNotEmpty()
                && password.isNotEmpty()
                && confirmPassword == password
                && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text("Create Account")
        }
    }

    Spacer(Modifier.height(16.dp))

    TextButton(onClick = onMoveToLogin, modifier = Modifier.fillMaxWidth()) {
        Text("Already have an account? Login", textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AuthPreview() {
    AutoTripTheme {
        AuthScreen(onLoginSuccess = {}, onSignupSelected = {})
    }
}