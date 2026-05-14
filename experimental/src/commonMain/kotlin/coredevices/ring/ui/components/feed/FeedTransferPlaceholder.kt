package coredevices.ring.ui.components.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coredevices.libindex.database.entity.RingTransfer
import coredevices.libindex.database.entity.RingTransferStatus
import coredevices.ring.ui.components.chat.ChatBubble

@Composable
fun FeedTransferPlaceholder(
    transfer: RingTransfer
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        ChatBubble(
            Modifier
                .align(Alignment.End)
                .padding(start = 50.dp)
        ) {
            when (transfer.status) {
                RingTransferStatus.Started, RingTransferStatus.Discarded -> Text("Transferring...")
                RingTransferStatus.Failed -> Text("Transfer Failed")
                RingTransferStatus.Completed -> AnimatedAudioBars()
            }
        }
    }
}