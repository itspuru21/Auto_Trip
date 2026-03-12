package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.BottomNavigationBar
import com.example.autotrip.model.EnhancedUserProfile
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.viewmodel.ProfileUiState
import com.example.autotrip.viewmodel.ProfileViewModel
import com.example.autotrip.viewmodel.SaveState

@Composable
fun EnhancedProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profileState by profileViewModel.profileState.collectAsState()
    val saveState by profileViewModel.saveState.collectAsState()

    var selectedSection by remember { mutableStateOf(0) }
    var editMode by remember { mutableStateOf(false) }

    // Show snackbar when save succeeds
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            snackbarHostState.showSnackbar("Profile updated successfully!")
            profileViewModel.resetSaveState()
        } else if (saveState is SaveState.Error) {
            snackbarHostState.showSnackbar((saveState as SaveState.Error).message)
            profileViewModel.resetSaveState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigationBar(navController = navController, currentRoute = "profile")
        }
    ) { padding ->

        when (profileState) {

            is ProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ProfileUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Failed to load profile",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { profileViewModel.loadProfile() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is ProfileUiState.Success -> {
                val user = (profileState as ProfileUiState.Success).profile

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProfileHeaderAnimated(user)

                    Spacer(Modifier.height(16.dp))

                    ProfileSectionTabs(
                        selectedIndex = selectedSection,
                        onSelect = { selectedSection = it }
                    )

                    Spacer(Modifier.height(16.dp))

                    when (selectedSection) {
                        0 -> PersonalSection(user)
                        1 -> AdditionalSection(user)
                        2 -> PrivacySection()
                    }

                    Spacer(Modifier.height(90.dp))
                }

                // FAB + Edit Panel overlay
                Box(modifier = Modifier.fillMaxSize()) {

                    if (selectedSection == 0) {
                        FloatingActionButton(
                            onClick = { editMode = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(20.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                        }
                    }

                    if (selectedSection == 0) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            EditProfileBottomSheet(
                                visible = editMode,
                                user = user,
                                isSaving = saveState is SaveState.Saving,
                                onDismiss = { editMode = false },
                                onSave = { updates ->
                                    profileViewModel.saveProfile(updates)
                                    editMode = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------
// PROFILE HEADER (unchanged from original)
// -------------------------------------------------------

@Composable
fun ProfileHeaderAnimated(user: EnhancedUserProfile) {
    val infinite = rememberInfiniteTransition(label = "")
    val floatOffset by infinite.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = tween(600), label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(top = 40.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.scale(scale).offset(y = floatOffset.dp)) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(60.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                // Show first name or full name, fallback to "User"
                user.fullName.ifEmpty { "User" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                user.email.ifEmpty { "No email" },
                color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp
            )
        }
    }
}

// -------------------------------------------------------
// SECTION TABS (unchanged)
// -------------------------------------------------------

@Composable
fun ProfileSectionTabs(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val sections = listOf("Personal", "Additional", "Privacy")
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        sections.forEachIndexed { index, text ->
            SegmentedButton(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, sections.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text, fontSize = 12.sp,
                    color = if (selectedIndex == index) Color.White else Color.Gray
                )
            }
        }
    }
}

// -------------------------------------------------------
// SECTION CONTENT — now uses real data
// -------------------------------------------------------

@Composable
fun PersonalSection(user: EnhancedUserProfile) {
    Column(Modifier.padding(16.dp)) {
        SectionCard(title = "Basic Info") {
            InfoRow("Full Name", user.fullName.ifEmpty { "Not set" })
            InfoRow("Email", user.email.ifEmpty { "Not set" })
            InfoRow("Phone", user.phoneNumber.ifEmpty { "Not set" })
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Demographics") {
            InfoRow("Age Group", user.ageGroup.ifEmpty { "Not set" })
            InfoRow("Gender", user.gender.ifEmpty { "Not set" })
            InfoRow("Occupation", user.occupation.ifEmpty { "Not set" })
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Household") {
            InfoRow("Family Members", user.householdSize.toString())
            InfoRow("Children", user.numberOfChildren.toString())
            InfoRow("Working Adults", user.numberOfWorkingAdults.toString())
        }
    }
}

@Composable
fun AdditionalSection(user: EnhancedUserProfile) {
    Column(Modifier.padding(16.dp)) {
        SectionCard(title = "Vehicles") {
            InfoRow("Owns Vehicle", if (user.ownsPersonalVehicle) "Yes" else "No")
            InfoRow("Vehicle Type", user.vehicleType.ifEmpty { "Not set" })
            InfoRow("Number of Vehicles", user.numberOfVehicles.toString())
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Transport Preferences") {
            InfoRow("Primary Commute", user.primaryCommuteMode.ifEmpty { "Not set" })
            InfoRow("WFH Frequency", user.workFromHomeFrequency.ifEmpty { "Not set" })
            InfoRow("Work Location", user.workLocationType.ifEmpty { "Not set" })
        }
    }
}

@Composable
fun PrivacySection() {
    Column(Modifier.padding(16.dp)) {
        SectionCard(title = "Privacy Settings") {
            InfoRow("Data Collection", "Enabled")
            InfoRow("Location Access", "Always")
        }
    }
}

// -------------------------------------------------------
// UTILITY COMPOSABLES (unchanged)
// -------------------------------------------------------

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

// -------------------------------------------------------
// EDIT BOTTOM SHEET — now saves to Firestore via ViewModel
// -------------------------------------------------------

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditProfileBottomSheet(
    visible: Boolean,
    user: EnhancedUserProfile,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(420.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            var name by remember(user) { mutableStateOf(user.fullName) }
            var phone by remember(user) { mutableStateOf(user.phoneNumber) }
            var ageGroup by remember(user) { mutableStateOf(user.ageGroup) }
            var gender by remember(user) { mutableStateOf(user.gender) }
            var occupation by remember(user) { mutableStateOf(user.occupation) }

            Column(Modifier.padding(20.dp)) {
                Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isSaving
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isSaving
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageGroup, onValueChange = { ageGroup = it },
                        label = { Text("Age Group") },
                        modifier = Modifier.weight(1f), enabled = !isSaving
                    )
                    OutlinedTextField(
                        value = gender, onValueChange = { gender = it },
                        label = { Text("Gender") },
                        modifier = Modifier.weight(1f), enabled = !isSaving
                    )
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = occupation, onValueChange = { occupation = it },
                    label = { Text("Occupation") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isSaving
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            // Only pass changed fields to Firestore
                            onSave(
                                mapOf(
                                    "fullName" to name,
                                    "phoneNumber" to phone,
                                    "ageGroup" to ageGroup,
                                    "gender" to gender,
                                    "occupation" to occupation
                                )
                            )
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewEnhancedProfile() {
    AutoTripTheme {
        EnhancedProfileScreen(rememberNavController())
    }
}