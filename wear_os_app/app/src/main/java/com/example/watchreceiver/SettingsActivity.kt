package com.example.watchreceiver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val displayMode = prefs.getString("display_mode", "BOTH") ?: "BOTH"
        val letterSize = prefs.getInt("letter_size", 120)
        val colorSize = prefs.getString("color_size", "FULLSCREEN") ?: "FULLSCREEN"

        setContent {
            SettingsScreen(
                initialDisplayMode = displayMode,
                initialLetterSize = letterSize,
                initialColorSize = colorSize,
                onSave = { mode, size, colorSizeValue ->
                    prefs.edit().apply {
                        putString("display_mode", mode)
                        putInt("letter_size", size)
                        putString("color_size", colorSizeValue)
                        apply()
                    }
                    finish()
                }
            )
        }
    }
}

@Composable
fun SettingsScreen(
    initialDisplayMode: String,
    initialLetterSize: Int,
    initialColorSize: String,
    onSave: (String, Int, String) -> Unit
) {
    var displayMode by remember { mutableStateOf(initialDisplayMode) }
    var letterSize by remember { mutableStateOf(initialLetterSize) }
    var colorSize by remember { mutableStateOf(initialColorSize) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Impostazioni",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Display Mode
        Text(
            text = "Modalità visualizzazione",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        DisplayModeSelector(
            selectedMode = displayMode,
            onModeSelected = { displayMode = it }
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        // Letter Size
        Text(
            text = "Dimensione lettera",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        LetterSizeSelector(
            selectedSize = letterSize,
            onSizeSelected = { letterSize = it }
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        // Color Size
        Text(
            text = "Dimensione colore",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        ColorSizeSelector(
            selectedSize = colorSize,
            onSizeSelected = { colorSize = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = { onSave(displayMode, letterSize, colorSize) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("Salva")
        }
    }
}

@Composable
fun DisplayModeSelector(selectedMode: String, onModeSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsOption(
            label = "Lettera e Colore",
            isSelected = selectedMode == "BOTH",
            onClick = { onModeSelected("BOTH") }
        )
        SettingsOption(
            label = "Solo Lettera",
            isSelected = selectedMode == "LETTER_ONLY",
            onClick = { onModeSelected("LETTER_ONLY") }
        )
        SettingsOption(
            label = "Solo Colore",
            isSelected = selectedMode == "COLOR_ONLY",
            onClick = { onModeSelected("COLOR_ONLY") }
        )
    }
}

@Composable
fun LetterSizeSelector(selectedSize: Int, onSizeSelected: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsOption(
            label = "Grande (200sp)",
            isSelected = selectedSize == 200,
            onClick = { onSizeSelected(200) }
        )
        SettingsOption(
            label = "Media (120sp)",
            isSelected = selectedSize == 120,
            onClick = { onSizeSelected(120) }
        )
        SettingsOption(
            label = "Piccola (60sp)",
            isSelected = selectedSize == 60,
            onClick = { onSizeSelected(60) }
        )
    }
}

@Composable
fun ColorSizeSelector(selectedSize: String, onSizeSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsOption(
            label = "Schermo intero",
            isSelected = selectedSize == "FULLSCREEN",
            onClick = { onSizeSelected("FULLSCREEN") }
        )
        SettingsOption(
            label = "Cerchio Grande",
            isSelected = selectedSize == "CIRCLE_LARGE",
            onClick = { onSizeSelected("CIRCLE_LARGE") }
        )
        SettingsOption(
            label = "Cerchio Medio",
            isSelected = selectedSize == "CIRCLE_MEDIUM",
            onClick = { onSizeSelected("CIRCLE_MEDIUM") }
        )
        SettingsOption(
            label = "Cerchio Piccolo",
            isSelected = selectedSize == "CIRCLE_SMALL",
            onClick = { onSizeSelected("CIRCLE_SMALL") }
        )
    }
}

@Composable
fun SettingsOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
