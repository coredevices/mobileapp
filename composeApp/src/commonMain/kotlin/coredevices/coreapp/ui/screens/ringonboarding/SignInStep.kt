package coredevices.coreapp.ui.screens.ringonboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.ui.SignInButtons

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SignInStep(
    userEmail: String?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    val palette = LocalPalette.current
    val signedIn = userEmail != null
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBarRow(onLeading = onBack, leadingIsClose = false, onTrailingClose = onExit)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(palette.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (signedIn) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = palette.primary,
                )
            }
            Spacer(Modifier.height(40.dp))
            Text(
                text = "Sign in to continue",
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
                color = palette.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Index 01 needs an account for backup and agent processing. " +
                        "Sign in to finish setting up your ring.",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = palette.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (signedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = palette.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Signed in as $userEmail",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface,
                    )
                }
            } else {
                // onDismiss is a no-op: we react to the auth state observed by the
                // parent, which re-renders this step into its signed-in state.
                SignInButtons(
                    onDismiss = {},
                    primaryColor = true,
                    skipAccountSwitchConfirmation = true,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        ) {
            PrimaryFilledButton(text = "Continue", onClick = onContinue, enabled = signedIn)
        }
    }
}
