package coredevices.coreapp.ui.screens.onboarding

import BugReportButton
import CoreNav
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coredevices.util.auth.GoogleAuthUtil
import coredevices.util.rememberUiContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.AuthResult
import dev.gitlive.firebase.auth.auth
import org.koin.compose.koinInject

@Composable
fun SignIn(onContinue: () -> Unit, coreNav: CoreNav) {
    val context = rememberUiContext()
    val googleAuthUtil = koinInject<GoogleAuthUtil>()
    val (result, setResult) = remember { mutableStateOf<AuthResult?>(null) }
    val (error, setError) = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val credential = try {
            googleAuthUtil.signInGoogle(context!!) ?: return@LaunchedEffect
        } catch (e: Exception) {
            setError(e.message ?: "Unknown error")
            return@LaunchedEffect
        }
        setResult(Firebase.auth.signInWithCredential(credential))
    }
    LaunchedEffect(result) {
        if (result?.user != null) {
            onContinue()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Sign In")
                },
                actions = {
                    BugReportButton(
                        coreNav = coreNav,
                        pebble = false,
                        screenContext = mapOf("screen" to "SignIn"),
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding), contentAlignment = Alignment.Center) {
            when {
                result == null -> {
                    Text("Signing in...")
                }

                result.user == null || error != null -> {
                    Text("Sign in failed")
                }

                else -> {
                    Text("Sign in successful")
                }
            }
        }
    }
}