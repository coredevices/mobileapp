package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotionOAuthResult(success: Boolean, onContinue: () -> Unit) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(100.dp, alignment = Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (success) {
                Text("Notion Linked", style = MaterialTheme.typography.headlineMedium)
            } else {
                Text("Notion Link Failed", style = MaterialTheme.typography.headlineMedium)
            }
            Button(onClick = onContinue) {
                Text("Continue")
            }
        }
    }
}