package coredevices.coreapp.ring

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.util.AudioEncoding
import coredevices.ring.firestoreModule
import coredevices.experimentalModule
import coredevices.ring.util.AudioPlayer
import coredevices.ring.viewmodelModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.math.exp
import kotlin.time.Duration.Companion.seconds

class AudioPlayerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().context
        stopKoin()
        startKoin {
            modules(
                module {
                    androidContext(context)
                },
                firestoreModule,
                experimentalModule,
                viewmodelModule
            )
        }
    }

    @Test
    fun testAudioPlayback() = runBlocking {
        val player = AudioPlayer()
        val source = context.assets.open("test-22050-pcm16.wav").asSource().buffered()
        source.skip(44) // WAV header
        player.playRaw(source, 22050, AudioEncoding.PCM_16BIT)
        delay(5.seconds) // playRaw does not suspend during playback
        player.close()
    }
}