package coredevices.ring.service

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InferenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val lp = attributes
            lp.width = 1
            lp.height = 1
            lp.dimAmount = 0f
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = lp
        }

        lifecycleScope.launch {
            if (!InferenceForegroundService.hasActiveSessions()) {
                finish()
                return@launch
            }
            InferenceForegroundService.running.first { it }
            InferenceForegroundService.running.first { !it }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!InferenceForegroundService.hasActiveSessions()) {
            finish()
            return
        }
        InferenceForegroundService.onActivityRunning(true)
    }

    override fun onDestroy() {
        InferenceForegroundService.onActivityRunning(false)
        super.onDestroy()
    }
}
