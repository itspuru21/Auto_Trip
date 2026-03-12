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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch

/* =====================================================================
   MAIN SCREEN
===================================================================== */

@Composable
fun EnhancedProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val profileState by profileViewModel.profileState.collectAsState()
    val saveState by profileViewModel.saveState.collectAsState()
    val isDeleting by profileViewModel.isDeleting.collectAsState()

    var selectedSection by remember { mutableStateOf(0) }
    var editMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Save state feedback
    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Saved -> {
                snackbarHostState.showSnackbar("Profile updated successfully!")
                profileViewModel.resetSaveState()
            }
            is SaveState.Error -> {
                snackbarHostState.showSnackbar((saveState as SaveState.Error).message)
                profileViewModel.resetSaveState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigationBar(navController = navController, currentRoute = "profile")
        }
    ) { padding ->

        when (val state = profileState) {

            is ProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            is ProfileUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Failed to load profile", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { profileViewModel.loadProfile() }) { Text("Retry") }
                    }
                }
            }

            is ProfileUiState.Success -> {
                val user = state.profile

                Box(modifier = Modifier.fillMaxSize().padding(padding)) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ProfileHeaderAnimated(user)

                        Spacer(Modifier.height(16.dp))

                        ProfileSectionTabs(
                            selectedIndex = selectedSection,
                            onSelect = {
                                selectedSection = it
                                editMode = false
                            }
                        )

                        Spacer(Modifier.height(16.dp))

                        when (selectedSection) {
                            0 -> PersonalSection(user)
                            1 -> AdditionalSection(user)
                            2 -> SettingsSection(
                                onDeleteAccount = { showDeleteDialog = true }
                            )
                        }

                        Spacer(Modifier.height(100.dp))
                    }

                    // FAB on Personal and Additional tabs
                    if (selectedSection == 0 || selectedSection == 1) {
                        FloatingActionButton(
                            onClick = { editMode = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 20.dp, end = 20.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                        }
                    }

                    // Edit bottom sheet
                    if (selectedSection == 0 || selectedSection == 1) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            EditProfileBottomSheet(
                                visible = editMode,
                                section = selectedSection,
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

    // Delete confirmation dialog
    if (showDeleteDialog) {
        DeleteAccountDialog(
            isDeleting = isDeleting,
            onConfirm = {
                profileViewModel.deleteAccount(
                    onDeleted = {
                        showDeleteDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Account deleted. Please restart the app."
                            )
                        }
                    },
                    onError = { msg ->
                        showDeleteDialog = false
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
            },
            onDismiss = { if (!isDeleting) showDeleteDialog = false }
        )
    }
}


/* =====================================================================
   PROFILE HEADER
===================================================================== */

@Composable
fun ProfileHeaderAnimated(user: EnhancedUserProfile) {
    val infinite = rememberInfiniteTransition(label = "float")
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
            Box(
                modifier = Modifier
                    .scale(scale)
                    .offset(y = floatOffset.dp)
            ) {
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
                user.fullName.ifEmpty { "Complete Your Profile" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                user.email.ifEmpty { "No email" },
                color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp
            )
            if (user.occupation.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    user.occupation,
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
                )
            }
        }
    }
}

/* =====================================================================
   SECTION TABS  (Personal | Additional | Settings)
===================================================================== */

@Composable
fun ProfileSectionTabs(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val sections = listOf("Personal", "Additional", "Settings")
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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

/* =====================================================================
   PERSONAL SECTION  (Tab 0)
===================================================================== */

@Composable
fun PersonalSection(user: EnhancedUserProfile) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionCard(title = "Basic Info", icon = Icons.Default.Person) {
            InfoRow("Full Name", user.fullName.ifEmpty { "—" })
            InfoRow("Email", user.email.ifEmpty { "—" })
            InfoRow("Phone", user.phoneNumber.ifEmpty { "—" })
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Demographics", icon = Icons.Default.People) {
            InfoRow("Age Group", user.ageGroup.ifEmpty { "—" })
            InfoRow("Gender", user.gender.ifEmpty { "—" })
            InfoRow("Occupation", user.occupation.ifEmpty { "—" })
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Residence", icon = Icons.Default.Home) {
            InfoRow("Residence Type", user.residenceType.ifEmpty { "—" })
            InfoRow("Household Size", "${user.householdSize} members")
        }
    }
}

/* =====================================================================
   ADDITIONAL SECTION  (Tab 1)
===================================================================== */

@Composable
fun AdditionalSection(user: EnhancedUserProfile) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionCard(title = "Vehicle Info", icon = Icons.Default.DirectionsCar) {
            InfoRow("Owns Vehicle", if (user.ownsPersonalVehicle) "Yes" else "No")
            InfoRow("Vehicle Type", user.vehicleType.ifEmpty { "—" })
            InfoRow("Number of Vehicles", "${user.numberOfVehicles}")
            InfoRow("Driving License", if (user.hasDrivingLicense) "Yes" else "No")
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Travel Preferences", icon = Icons.Default.Route) {
            InfoRow("Primary Commute Mode", user.primaryCommuteMode.ifEmpty { "—" })
            InfoRow("Work Location", user.workLocationType.ifEmpty { "—" })
        }
    }
}

/* =====================================================================
   SETTINGS SECTION  (Tab 2)
===================================================================== */

@Composable
fun SettingsSection(onDeleteAccount: () -> Unit) {

    var locationEnabled by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var anonymousDataEnabled by remember { mutableStateOf(true) }
    var autoDetectTrips by remember { mutableStateOf(true) }

    Column(Modifier.padding(horizontal = 16.dp)) {

        // --- Privacy & Data ---
        SectionCard(title = "Privacy & Data", icon = Icons.Default.Shield) {
            SettingsToggleRow(
                title = "Share Anonymous Trip Data",
                subtitle = "Contribute anonymised data to NATPAC research",
                checked = anonymousDataEnabled,
                onCheckedChange = { anonymousDataEnabled = it }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsToggleRow(
                title = "Auto-Detect Trips",
                subtitle = "Automatically detect trip start and end in background",
                checked = autoDetectTrips,
                onCheckedChange = { autoDetectTrips = it }
            )
        }

        Spacer(Modifier.height(12.dp))

        // --- Permissions ---
        SectionCard(title = "Permissions", icon = Icons.Default.Security) {
            SettingsToggleRow(
                title = "Location Access",
                subtitle = "Required for automatic trip detection",
                checked = locationEnabled,
                onCheckedChange = { locationEnabled = it }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsToggleRow(
                title = "Notifications",
                subtitle = "Reminders to complete incomplete trip details",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
        }

        Spacer(Modifier.height(12.dp))

        // --- About ---
        SectionCard(title = "About", icon = Icons.Default.Info) {
            InfoRow("App Version", "1.0.0")
            InfoRow("Data Partner", "NATPAC")
            InfoRow("Research Purpose", "Transportation Planning")
        }

        Spacer(Modifier.height(12.dp))

        // --- Danger Zone ---
        SectionCard(title = "Account", icon = Icons.Default.ManageAccounts) {
            Text(
                "Deleting your account is permanent. All your trip data and profile information will be removed from our servers.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(
                onClick = onDeleteAccount,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Delete My Account")
            }
        }
    }
}

/* =====================================================================
   EDIT BOTTOM SHEET  — handles Personal (section=0) and Additional (section=1)
===================================================================== */

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditProfileBottomSheet(
    visible: Boolean,
    section: Int,
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
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (section == 0) "Edit Personal Info" else "Edit Travel Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(16.dp))

                if (section == 0) {
                    PersonalEditFields(user = user, isSaving = isSaving, onSave = onSave, onDismiss = onDismiss)
                } else {
                    AdditionalEditFields(user = user, isSaving = isSaving, onSave = onSave, onDismiss = onDismiss)
                }
            }
        }
    }
}

/* ---- Personal edit fields ---- */
@Composable
private fun PersonalEditFields(
    user: EnhancedUserProfile,
    isSaving: Boolean,
    onSave: (Map<String, Any>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(user) { mutableStateOf(user.fullName) }
    var phone by remember(user) { mutableStateOf(user.phoneNumber) }
    var ageGroup by remember(user) { mutableStateOf(user.ageGroup) }
    var gender by remember(user) { mutableStateOf(user.gender) }
    var occupation by remember(user) { mutableStateOf(user.occupation) }
    var residenceType by remember(user) { mutableStateOf(user.residenceType) }
    var householdSize by remember(user) { mutableStateOf(user.householdSize) }

    val ageGroups = listOf("Under 18", "18–25", "26–35", "36–45", "46–55", "56–65", "65+")
    val genders = listOf("Male", "Female", "Other", "Prefer not to say")
    val residenceTypes = listOf("Urban", "Semi-Urban", "Rural")

    OutlinedTextField(
        value = name, onValueChange = { name = it },
        label = { Text("Full Name") },
        modifier = Modifier.fillMaxWidth(), enabled = !isSaving
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = phone, onValueChange = { phone = it },
        label = { Text("Phone Number") },
        modifier = Modifier.fillMaxWidth(), enabled = !isSaving
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = occupation, onValueChange = { occupation = it },
        label = { Text("Occupation") },
        modifier = Modifier.fillMaxWidth(), enabled = !isSaving
    )
    Spacer(Modifier.height(14.dp))

    ChipGroupField(
        label = "Age Group",
        options = ageGroups,
        selected = ageGroup,
        onSelect = { ageGroup = it }
    )
    Spacer(Modifier.height(14.dp))

    ChipGroupField(
        label = "Gender",
        options = genders,
        selected = gender,
        onSelect = { gender = it }
    )
    Spacer(Modifier.height(14.dp))

    ChipGroupField(
        label = "Residence Type",
        options = residenceTypes,
        selected = residenceType,
        onSelect = { residenceType = it }
    )
    Spacer(Modifier.height(14.dp))

    // Household size stepper
    Text("Household Size", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedIconButton(onClick = { if (householdSize > 1) householdSize-- }, enabled = !isSaving) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Text("$householdSize", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        OutlinedIconButton(onClick = { householdSize++ }, enabled = !isSaving) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
    Spacer(Modifier.height(20.dp))

    EditSheetButtons(
        isSaving = isSaving,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                mapOf(
                    "fullName" to name,
                    "phoneNumber" to phone,
                    "occupation" to occupation,
                    "ageGroup" to ageGroup,
                    "gender" to gender,
                    "residenceType" to residenceType,
                    "householdSize" to householdSize,
                )
            )
        }
    )
    Spacer(Modifier.height(8.dp))
}

/* ---- Additional edit fields ---- */
@Composable
private fun AdditionalEditFields(
    user: EnhancedUserProfile,
    isSaving: Boolean,
    onSave: (Map<String, Any>) -> Unit,
    onDismiss: () -> Unit
) {
    var ownsVehicle by remember(user) { mutableStateOf(user.ownsPersonalVehicle) }
    var vehicleType by remember(user) { mutableStateOf(user.vehicleType) }
    var numVehicles by remember(user) { mutableStateOf(user.numberOfVehicles) }
    var hasLicense by remember(user) { mutableStateOf(user.hasDrivingLicense) }
    var commuteMode by remember(user) { mutableStateOf(user.primaryCommuteMode) }
    var workLocation by remember(user) { mutableStateOf(user.workLocationType) }

    val vehicleTypes = listOf("None", "Two-Wheeler", "Car", "Auto-Rickshaw", "Electric Vehicle")
    val commuteModes = listOf("Walk", "Bike", "Car", "Bus", "Train", "Metro", "Auto")
    val workLocations = listOf("Office", "Remote / WFH", "Hybrid", "Field Work", "Student")

    // Owns Vehicle toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Owns Personal Vehicle", style = MaterialTheme.typography.titleSmall)
        Switch(checked = ownsVehicle, onCheckedChange = { ownsVehicle = it }, enabled = !isSaving)
    }
    Spacer(Modifier.height(14.dp))

    if (ownsVehicle) {
        ChipGroupField(
            label = "Vehicle Type",
            options = vehicleTypes.drop(1), // exclude "None"
            selected = vehicleType,
            onSelect = { vehicleType = it }
        )
        Spacer(Modifier.height(14.dp))

        Text("Number of Vehicles", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedIconButton(onClick = { if (numVehicles > 0) numVehicles-- }, enabled = !isSaving) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Text("$numVehicles", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedIconButton(onClick = { numVehicles++ }, enabled = !isSaving) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Has Driving License", style = MaterialTheme.typography.titleSmall)
            Switch(checked = hasLicense, onCheckedChange = { hasLicense = it }, enabled = !isSaving)
        }
        Spacer(Modifier.height(14.dp))
    }

    ChipGroupField(
        label = "Primary Commute Mode",
        options = commuteModes,
        selected = commuteMode,
        onSelect = { commuteMode = it }
    )
    Spacer(Modifier.height(14.dp))

    ChipGroupField(
        label = "Work / Study Location",
        options = workLocations,
        selected = workLocation,
        onSelect = { workLocation = it }
    )
    Spacer(Modifier.height(20.dp))

    EditSheetButtons(
        isSaving = isSaving,
        onDismiss = onDismiss,
        onSave = {
            val updates = mutableMapOf<String, Any>(
                "ownsPersonalVehicle" to ownsVehicle,
                "vehicleType" to if (ownsVehicle) vehicleType else "None",
                "numberOfVehicles" to if (ownsVehicle) numVehicles else 0,
                "hasDrivingLicense" to if (ownsVehicle) hasLicense else false,
                "primaryCommuteMode" to commuteMode,
                "workLocationType" to workLocation,
            )
            onSave(updates)
        }
    )
    Spacer(Modifier.height(8.dp))
}

/* =====================================================================
   SHARED SMALL COMPONENTS
===================================================================== */

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ChipGroupField(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = selected == option
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(option, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun EditSheetButtons(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onDismiss, enabled = !isSaving) {
            Text("Cancel")
        }
        Button(onClick = onSave, enabled = !isSaving) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
            } else {
                Text("Save Changes")
            }
        }
    }
}

/* =====================================================================
   DELETE ACCOUNT CONFIRMATION DIALOG
===================================================================== */

@Composable
fun DeleteAccountDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Delete Account?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This action is permanent and cannot be undone.\n\n" +
                        "All your trip records and personal data will be permanently removed from NATPAC servers.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text("Delete Permanently")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancel")
            }
        }
    )
}

/* =====================================================================
   PREVIEW
===================================================================== */

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewEnhancedProfile() {
    AutoTripTheme {
        EnhancedProfileScreen(rememberNavController())
    }
}