package com.example.autotrip.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Global shapes for the entire app (Material 3)
val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),     // chips, small elements
    medium = RoundedCornerShape(16.dp),   // cards, text fields
    large = RoundedCornerShape(22.dp)     // large cards, dialogs, containers
)
