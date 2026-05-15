package locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.english
import coreapp.util.generated.resources.spanish
import coreapp.util.generated.resources.system
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import locale.AppLocale.Companion.asAppLocale
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject

enum class AppLocale(val resource: StringResource, val key: String, val languageTag: String?) {
    System(Res.string.system, "system", null),
    English(Res.string.english, "en", "en"),
    Spanish(Res.string.spanish, "es", "es"),
    ;

    companion object {
        fun String?.asAppLocale(): AppLocale =
            entries.firstOrNull { it.key == this } ?: System
    }
}

interface LocaleProvider {
    val locale: StateFlow<AppLocale>
    fun setLocale(locale: AppLocale)
}

class RealLocaleProvider(
    private val settings: Settings,
) : LocaleProvider {
    private val _locale = MutableStateFlow(getLocale())
    override val locale: StateFlow<AppLocale> = _locale.asStateFlow()

    private fun getLocale(): AppLocale =
        settings.getStringOrNull(LOCALE_SETTINGS_KEY).asAppLocale()

    override fun setLocale(locale: AppLocale) {
        settings.putString(LOCALE_SETTINGS_KEY, locale.key)
        _locale.value = locale
    }

    companion object {
        private const val LOCALE_SETTINGS_KEY = "app_locale"
    }
}

@OptIn(InternalResourceApi::class)
@Composable
fun LocalizedApp(content: @Composable () -> Unit) {
    val localeProvider: LocaleProvider = koinInject()
    val locale by localeProvider.locale.collectAsState()
    val systemEnvironment = LocalComposeEnvironment.current
    val tag = locale.languageTag
    if (tag == null) {
        content()
    } else {
        val overridden = remember(systemEnvironment, tag) {
            object : ComposeEnvironment {
                @Composable
                override fun rememberEnvironment() =
                    systemEnvironment.rememberEnvironment().copy(language = LanguageQualifier(tag))
            }
        }
        CompositionLocalProvider(LocalComposeEnvironment provides overridden) {
            content()
        }
    }
}
