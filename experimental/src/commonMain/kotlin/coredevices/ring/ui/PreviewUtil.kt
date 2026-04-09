package coredevices.ring.ui

import NextBugReportContext
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.KoinApplication
import org.koin.dsl.bind
import org.koin.dsl.module
import theme.AppTheme
import theme.CoreAppTheme
import theme.ThemeProvider

@Composable
fun PreviewWrapper(content: @Composable () -> Unit) {
    KoinApplication(application = {
        modules(module {
            val themeProvider = object : ThemeProvider {
                override val theme: StateFlow<CoreAppTheme> = MutableStateFlow(CoreAppTheme.Light)
                override fun setTheme(theme: CoreAppTheme) {
                }
            }
            single { themeProvider } bind ThemeProvider::class
            single { NextBugReportContext() }
        })
    }) {
        AppTheme {
            content()
        }
    }
}