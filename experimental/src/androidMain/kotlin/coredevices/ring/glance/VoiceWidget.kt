package coredevices.ring.glance

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.material3.ColorProviders
import coredevices.ring.R
import theme.greyScheme
import theme.lightScheme

class VoiceWidget: GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val launchIntent = Intent(context, Class.forName("coredevices.coreapp.MainActivity")).apply {
            data = Uri.parse("voiceapp://listen")
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        provideContent {
            GlanceTheme(ColorProviders(light = lightScheme, dark = greyScheme)) {
                Content(launchIntent)
            }
        }
    }

    @Composable
    private fun Content(launchIntent: Intent) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.primary)
                .clickable(actionStartActivity(launchIntent)),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_mic), contentDescription = null)
        }
    }

}