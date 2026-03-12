package com.example.autotrip.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.autotrip.components.AutoTripTopBar
import com.example.autotrip.components.BottomNavigationBar
import com.example.autotrip.ui.theme.AutoTripTheme
import com.example.autotrip.model.EnhancedUserProfile

@Composable
fun EnhancedProfileScreen(navController: NavController) {

    // -----------------------------
    // USER DATA
    // -----------------------------
    var user by remember {
        mutableStateOf(
            EnhancedUserProfile(
                fullName = "John Doe",
                email = "john@example.com",
                phoneNumber = "+91 9876543210",
                ageGroup = "18–25",
                gender = "Male"
            )
        )
    }

    // which segmented tab is selected
    var selectedSection by remember { mutableStateOf(0) }

    // edit panel open?
    var editMode by remember { mutableStateOf(false) }

    // place content inside MainActivity innerPadding
    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentRoute = "profile"
            )
        }
    )
    { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {

                // ------------------------------------------------
                // PROFILE HEADER (BIG + FLOATING + SCROLL SHRINK)
                // ------------------------------------------------
                ProfileHeaderAnimated(user)

                Spacer(Modifier.height(16.dp))

                // ------------------------
                // SEGMENTED NAVIGATION
                // ------------------------
                ProfileSectionTabs(
                    selectedIndex = selectedSection,
                    onSelect = { selectedSection = it }
                )

                Spacer(Modifier.height(16.dp))

                // -------------------------
                // SECTION CONTENT
                // -------------------------

                when (selectedSection) {
                    0 -> PersonalSection(user)
                    1 -> AdditionalSection()
                    2 -> PrivacySection()
                }

                Spacer(Modifier.height(90.dp)) // bottom spacing for FAB
            }

            // -------------------------
            // OVERLAY: FAB + EDIT PANEL
            // -------------------------
            Box(modifier = Modifier.fillMaxSize()) {

                // FAB at bottom-end (conventional position)
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

                // Slide-up edit panel anchored to bottom
                if (selectedSection == 0) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        EditProfileBottomSheet(
                            visible = editMode,
                            user = user,
                            onDismiss = { editMode = false },
                            onSave = {
                                user = it
                                editMode = false
                            }
                        )
                    }
                }
            }

        }
}

/////////////////////////////////////////////////////////////////
//  HEADER — big animated profile header (Option 1A)
/////////////////////////////////////////////////////////////////

@Composable
fun ProfileHeaderAnimated(user: EnhancedUserProfile) {

    // floating avatar animation
    val infinite = rememberInfiniteTransition(label = "")
    val floatOffset by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = ""
    )

    // scale reveal animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = tween(600),
        label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(top = 40.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Avatar
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
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Name
            Text(
                user.fullName,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            // Email
            Text(
                user.email,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp
            )
        }
    }
}

/////////////////////////////////////////////////////////////////
// SEGMENTED NAVIGATION — Option 2A
/////////////////////////////////////////////////////////////////

@Composable
fun ProfileSectionTabs(selectedIndex: Int, onSelect: (Int) -> Unit) {

    val sections = listOf("Personal", "Additional", "Privacy")

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
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text,
                    fontSize = 12.sp,
                    color = if (selectedIndex == index) Color.White else Color.Gray
                )
            }
        }
    }
}

/////////////////////////////////////////////////////////////////
// SECTION CONTENT
/////////////////////////////////////////////////////////////////

@Composable
fun PersonalSection(user: EnhancedUserProfile) {
    Column(Modifier.padding(16.dp)) {

        SectionCard(title = "Basic Info") {
            InfoRow("Full Name", user.fullName)
            InfoRow("Email", user.email)
            InfoRow("Phone", user.phoneNumber)
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Demographics") {
            InfoRow("Age Group", user.ageGroup)
            InfoRow("Gender", user.gender)
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Household") {
            InfoRow("Family Members", "4")
            InfoRow("Children", "1")
            InfoRow("Working Adults", "2")
        }
    }
}

@Composable
fun AdditionalSection() {
    Column(Modifier.padding(16.dp)) {
        SectionCard(title = "Vehicles") {
            InfoRow("Owns Vehicle", "Yes")
            InfoRow("Vehicle Type", "Car")
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Transport Preferences") {
            InfoRow("Primary Commute", "Car")
            InfoRow("WFH Frequency", "Sometimes")
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

/////////////////////////////////////////////////////////////////
// UTILITY COMPOSABLES
/////////////////////////////////////////////////////////////////

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
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/////////////////////////////////////////////////////////////////
// EDIT MODE — Slide-Up Panel (Option 3A)
/////////////////////////////////////////////////////////////////

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditProfileBottomSheet(
    visible: Boolean,
    user: EnhancedUserProfile,
    onDismiss: () -> Unit,
    onSave: (EnhancedUserProfile) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {

            var name by remember { mutableStateOf(user.fullName) }
            var phone by remember { mutableStateOf(user.phoneNumber) }

            Column(Modifier.padding(20.dp)) {

                Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSave(
                                user.copy(
                                    fullName = name,
                                    phoneNumber = phone
                                )
                            )
                        }
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

/////////////////////////////////////////////////////////////////
// PREVIEW
/////////////////////////////////////////////////////////////////

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewEnhancedProfile() {
    AutoTripTheme {
        val nav = rememberNavController()
        EnhancedProfileScreen(nav)
    }
}
