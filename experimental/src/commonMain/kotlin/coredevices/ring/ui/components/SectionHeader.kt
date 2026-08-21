package coredevices.ring.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.theme.IndexTheme

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier.padding(top = 32.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
    trailingContent: @Composable () -> Unit = {}
) {
    SectionHeader(
        modifier = modifier,
        leadingContent = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IndexTheme.colors.primary
            )
        },
        trailingContent = trailingContent
    )
}

@Composable
fun SectionHeader(
    modifier: Modifier = Modifier.padding(top = 32.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        leadingContent()
        Spacer(modifier = Modifier.weight(1f))
        trailingContent()
    }
}