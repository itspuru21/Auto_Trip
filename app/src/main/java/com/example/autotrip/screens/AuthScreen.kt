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
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.R
import com.example.autotrip.ui.theme.AutoTripTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit,
               onSignupSelected: () -> Unit) {

    var isLogin by remember { mutableStateOf(true) }

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

            // ---------------- LOGO ----------------
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

            // ---------------- WHITE CARD ----------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    AuthToggle(isLogin = isLogin) { isLogin = !isLogin }

                    Spacer(Modifier.height(24.dp))

                    if (isLogin) {
                        LoginForm(
                            onLogin = {
                                onLoginSuccess()
//                                navController.navigate("home") {
//                                    popUpTo("auth") { inclusive = true }
//                                }
                            },
                            onMoveToSignup = { isLogin = false }
                        )
                    } else {
                        SignupForm(
                            onSignup = {
                                onSignupSelected()
//                                navController.navigate("permissions") {
//                                    popUpTo("auth") { inclusive = true }
//                                }
                            },
                            onMoveToLogin = { isLogin = true }
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
        ) {
            Text("Login")
        }

        Button(
            onClick = { if (isLogin) onToggle() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!isLogin) MaterialTheme.colorScheme.primary else Color.LightGray
            )
        ) {
            Text("Sign Up")
        }
    }
}

@Composable
fun LoginForm(onLogin: () -> Unit, onMoveToSignup: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
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
        onClick = {
            scope.launch {
                loading = true
                delay(1000)
                loading = false
                onLogin()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = email.isNotEmpty() && password.isNotEmpty()
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp)
            )
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
fun SignupForm(onSignup: () -> Unit, onMoveToLogin: () -> Unit) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Full Name") },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        modifier = Modifier.fillMaxWidth(),
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
        onClick = {
            scope.launch {
                loading = true
                delay(1000)
                loading = false
                onSignup()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = fullName.isNotEmpty()
                && email.isNotEmpty()
                && password.isNotEmpty()
                && confirmPassword == password
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text("Create Account")
        }
    }

    Spacer(Modifier.height(16.dp))

    TextButton(onClick = onMoveToLogin, modifier = Modifier.fillMaxWidth()) {
        Text("Already have an account? Login", textAlign = TextAlign.Center )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AuthPreview() {
    AutoTripTheme {
        AuthScreen(onLoginSuccess = {}, onSignupSelected = {})
    }
}
