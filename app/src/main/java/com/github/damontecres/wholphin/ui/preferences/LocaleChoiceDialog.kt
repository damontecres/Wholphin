package com.github.damontecres.wholphin.ui.preferences

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopupContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocaleChoiceViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        fun changeLocale(locale: Locale) {
            viewModelScope.launchDefault {
                serverRepository.current.value?.let {
                    Timber.i("Updating user's locale to %s", locale.toLanguageTag())
                    val newUser = it.user.copy(uiLanguage = locale.toLanguageTag())
                    serverRepository.changeUser(it.server, newUser)
                }
            }
        }

        private val _state = MutableStateFlow(LocaleChoiceState())
        val state: StateFlow<LocaleChoiceState> = _state

        init {
            viewModelScope.launchDefault {
                serverRepository.currentUser?.let {
                    val availableLocales = extractAvailableLocales()
                    Timber.v("availableLocales=%s", availableLocales)
                    _state.update {
                        it.copy(
                            loading = LoadingState.Success,
                            availableLocales = availableLocales,
                        )
                    }
                }
            }
            viewModelScope.launchDefault {
                serverRepository.currentUserFlow.collectLatest { user ->
                    val userLocale = user?.uiLanguage?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
                    _state.update { it.copy(userLocale = userLocale) }
                }
            }
        }

        /**
         * Get the available locales that can be used.
         *
         * Defaults to `context.assets.locales` (all locales) if can't find app specific ones
         */
        private fun extractAvailableLocales(): List<Locale> {
            val id =
                context.resources.getIdentifier(
                    "_generated_res_locale_config",
                    "xml",
                    context.packageName,
                )
            return if (id != 0) {
                try {
                    val locales = mutableListOf<Locale>()
                    // This is kind of a hack, the generated locale config is not available,
                    // programmatically, so this code parses the raw XML
                    context.resources.getXml(id).use { parser ->
                        var eventType: Int = parser.eventType
                        while (eventType != XmlResourceParser.END_DOCUMENT) {
                            if (eventType == XmlResourceParser.START_TAG) {
                                val tagName: String? = parser.name

                                // Check for a specific tag name
                                if ("locale" == tagName) {
                                    val attr =
                                        parser.getAttributeValue(
                                            "http://schemas.android.com/apk/res/android",
                                            "name",
                                        )
                                    attr?.let { locales.add(Locale.forLanguageTag(it)) }
                                }
                            }
                            eventType = parser.next()
                        }
                    }
                    locales
                } catch (ex: Exception) {
                    Timber.w(ex, "Exception while parsing generated available locales")
                    context.assets.locales.map { Locale.forLanguageTag(it) }
                }
            } else {
                context.assets.locales.map { Locale.forLanguageTag(it) }
            }
        }
    }

data class LocaleChoiceState(
    val loading: LoadingState = LoadingState.Pending,
    val availableLocales: List<Locale> = emptyList(),
    val userLocale: Locale = Locale.getDefault(),
)

@Composable
fun LocaleChoiceDialog(
    onDismissRequest: () -> Unit,
    viewModel: LocaleChoiceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(),
    ) {
        when (val st = state.loading) {
            is LoadingState.Error -> {
                ErrorMessage(st)
            }

            LoadingState.Loading,
            LoadingState.Pending,
            -> {
                LoadingPage()
            }

            LoadingState.Success -> {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                val dialogItems =
                    remember(state.userLocale, state.availableLocales) {
                        val userLanguage = state.userLocale.toLanguageTag()
                        state.availableLocales.map { locale ->
                            val tag = locale.toLanguageTag()
                            DialogItem(
                                selected = tag == userLanguage,
                                leadingContent = {
                                    Box {
                                        SelectedLeadingContent(tag == userLanguage)
                                    }
                                },
                                headlineContent = {
                                    Text(locale.getDisplayName(locale))
                                },
                                supportingContent = {
                                    Text(locale.getDisplayName(Locale.ENGLISH))
                                },
                                dismissOnClick = true,
                                onClick = {
                                    viewModel.changeLocale(locale)
                                },
                            )
                        }
                    }
                DialogPopupContent(
                    title = stringResource(R.string.user_interface_language),
                    dialogItems = dialogItems,
                    waiting = false,
                    onDismissRequest = onDismissRequest,
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
        }
    }
}
