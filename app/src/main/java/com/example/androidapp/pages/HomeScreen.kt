package com.example.androidapp.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
fun HomeScreen() {
    Column(
        // This centers all elements
        horizontalAlignment = Alignment.CenterHorizontally,
        // Makes the column fill the whole screen
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("Home Screen", textAlign = TextAlign.Center)
    }
}