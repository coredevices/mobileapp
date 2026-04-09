package coredevices.ring.ui.components.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.pause
import coreapp.ring.generated.resources.play
import coredevices.ring.ui.viewmodel.MessagePlaybackState
import org.jetbrains.compose.resources.stringResource

@Composable
fun RecordingChatBubble(
    enabled: Boolean,
    playing: Boolean,
    buffering: Boolean,
    playbackPercentage: Double,
    onPlayPause: () -> Unit,
    content: @Composable () -> Unit
) {
    val sliderColors = SliderDefaults.colors(
        inactiveTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
        activeTrackColor = MaterialTheme.colorScheme.onPrimary,
        thumbColor = MaterialTheme.colorScheme.onPrimary
    )

    ChatBubble {
        Column {
            content()
            Row(Modifier.padding(top = 8.dp)) {
                IconButton(
                    onClick = onPlayPause,
                    enabled = enabled
                ) {
                    when {
                        buffering -> CircularProgressIndicator()
                        playing -> Icon(Icons.Outlined.Pause, contentDescription = stringResource(Res.string.pause))
                        else -> Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.play))
                    }
                }
                Slider(
                    value = playbackPercentage.toFloat(),
                    {},
                    colors = sliderColors,
                    thumb = {},
                    track = { state ->
                        SliderDefaults.Track(
                            modifier = Modifier.height(6.dp),
                            colors = sliderColors,
                            enabled = true,
                            sliderState = state,
                            thumbTrackGapSize = 0.dp
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}