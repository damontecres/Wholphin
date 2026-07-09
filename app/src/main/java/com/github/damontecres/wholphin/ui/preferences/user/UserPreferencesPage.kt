package com.github.damontecres.wholphin.ui.preferences.user

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.preferences.AppChoicePreference
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.ServerProfileSetting
import com.github.damontecres.wholphin.preferences.SubtitleModePreference
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.preferences.ChoicePreference
import com.github.damontecres.wholphin.ui.preferences.ClickPreference
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import com.github.damontecres.wholphin.ui.preferences.PreferenceValidation
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import timber.log.Timber

@Composable
fun UserPreferencesPage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        UserPreferencesContent(
            Modifier
                .fillMaxWidth(.4f)
                .fillMaxHeight()
                .align(Alignment.TopEnd),
        )
    }
}

@Composable
fun UserPreferencesContent(
    modifier: Modifier = Modifier,
    viewModel: UserPreferencesViewModel = hiltViewModel(),
    onFocus: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by rememberSaveable { mutableStateOf(Pair(0, 0)) }
    val state = rememberLazyListState()

    val userDto by viewModel.currentUserDto.collectAsState()
    val preferences by viewModel.userAppPreferences.collectAsState()
    val audioLanguagePref by viewModel.audioLanguage.collectAsState()
    val subtitleLanguagePref by viewModel.subtitleLanguage.collectAsState()
    var showPreferredLanguageDialog by remember { mutableStateOf<Boolean?>(null) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Forces the animated to trigger
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            focusRequester.tryRequestFocus()
        }
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        ) {
            Text(
                text = stringResource(R.string.profile_specific_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            )
            LazyColumn(
                state = state,
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier,
            ) {
                ServerProfileSetting.Preferences.forEachIndexed { groupIndex, group ->
                    item {
                        Text(
                            text = stringResource(group.title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    val groupPreferences =
                        group.preferences +
                            group.conditionalPreferences
                                .filter { it.condition.invoke(preferences) }
                                .map { it.preferences }
                                .flatten()
                    groupPreferences.forEachIndexed { prefIndex, pref ->
                        pref as AppPreference<JellyfinUserPreferences, Any>
                        item {
                            val interactionSource = remember { MutableInteractionSource() }
                            val focused = interactionSource.collectIsFocusedAsState().value

                            val modifier =
                                Modifier
                                    .ifElse(
                                        groupIndex == focusedIndex.first && prefIndex == focusedIndex.second,
                                        Modifier.focusRequester(focusRequester),
                                    ).onFocusChanged {
                                        if (it.isFocused) focusedIndex = Pair(groupIndex, prefIndex)
                                    }

                            LaunchedEffect(focused) {
                                if (focused) {
                                    focusedIndex = Pair(groupIndex, prefIndex)
                                    onFocus.invoke(groupIndex, prefIndex)
                                }
                            }
                            when (pref) {
                                ServerProfileSetting.PreferredAudioLang -> {
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = { showPreferredLanguageDialog = true },
                                        summary = audioLanguagePref.selected.displayString.getString(),
                                        interactionSource = interactionSource,
                                        modifier = modifier,
                                    )
                                }

                                ServerProfileSetting.PreferredSubtitleLang -> {
                                    ClickPreference(
                                        title = stringResource(pref.title),
                                        onClick = { showPreferredLanguageDialog = false },
                                        summary = subtitleLanguagePref.selected.displayString.getString(),
                                        interactionSource = interactionSource,
                                        modifier = modifier,
                                    )
                                }

                                ServerProfileSetting.SubtitleModePref -> {
                                    pref as AppChoicePreference<JellyfinUserPreferences, SubtitleModePreference>
                                    val value = preferences.subtitleMode
                                    val values = stringArrayResource(pref.displayValues).toList()
                                    val summary =
                                        when (value) {
                                            SubtitleModePreference.USE_USER_PROFILE -> {
                                                val userMode = userDto?.configuration?.subtitleMode
                                                if (userMode != null) {
                                                    "${values[0]} - ${stringResource(userMode.stringRes)}"
                                                } else {
                                                    values[0]
                                                }
                                            }

                                            else -> {
                                                values[value.ordinal]
                                            }
                                        }
                                    val selectedIndex =
                                        remember(value) { pref.valueToIndex.invoke(value) }
                                    ChoicePreference(
                                        title = stringResource(pref.title),
                                        summary = summary,
                                        possibleValues = values,
                                        selectedIndex = selectedIndex,
                                        onValueChange = { index ->
                                            viewModel.updatePreferences {
                                                it.copy(subtitleMode = SubtitleModePreference.entries[index])
                                            }
                                        },
                                        modifier = modifier,
                                        interactionSource = interactionSource,
                                        subtitleDisplay = { index, _ ->
                                            val userMode = userDto?.configuration?.subtitleMode
                                            if (index == 0 && userMode != null) {
                                                {
                                                    Text(stringResource(userMode.stringRes))
                                                }
                                            } else {
                                                null
                                            }
                                        },
                                    )
                                }

                                else -> {
                                    val value = pref.getter.invoke(preferences)
                                    ComposablePreference(
                                        preference = pref,
                                        value = value,
                                        onNavigate = viewModel.navigationManager::navigateTo,
                                        onValueChange = { newValue ->
                                            val validation = pref.validate(newValue)
                                            when (validation) {
                                                is PreferenceValidation.Invalid -> {
                                                    // TODO?
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            validation.message,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                }

                                                PreferenceValidation.Valid -> {
                                                    viewModel.updatePreferences {
                                                        pref.setter(preferences, newValue)
                                                    }
                                                }
                                            }
                                        },
                                        interactionSource = interactionSource,
                                        modifier = modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    showPreferredLanguageDialog?.let { isAudio ->
        BasicDialog(
            onDismissRequest = { showPreferredLanguageDialog = null },
            elevation = 3.dp,
        ) {
            val lang = if (isAudio) audioLanguagePref else subtitleLanguagePref
            FilteredLanguagePreference(
                title = if (isAudio) R.string.preferred_audio_language else R.string.preferred_subtitle_language,
                selectedOption = lang.selected,
                options = lang.options,
                onClickOption = { option ->
                    val value =
                        when (option) {
                            PreferredLanguageType.AnyLanguage -> ServerProfileSetting.PREFER_ANY_LANGUAGE
                            is PreferredLanguageType.Language -> option.iso
                            is PreferredLanguageType.ServerProfile -> ServerProfileSetting.USE_USER_PROFILE
                            PreferredLanguageType.Divider -> throw IllegalStateException("Cannot click on a divider")
                        }
                    Timber.v("Updating language pref to %s", option)
                    viewModel.updatePreferences {
                        if (isAudio) {
                            it.copy(preferredAudioLanguage = value)
                        } else {
                            it.copy(preferredSubtitleLanguage = value)
                        }
                    }
                    showPreferredLanguageDialog = null
                },
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
            )
        }
    }
}

val SubtitlePlaybackMode.stringRes: Int
    @StringRes get() =
        when (this) {
            SubtitlePlaybackMode.DEFAULT -> R.string.subtitle_mode_default
            SubtitlePlaybackMode.ALWAYS -> R.string.subtitle_mode_always
            SubtitlePlaybackMode.ONLY_FORCED -> R.string.subtitle_mode_only_forced
            SubtitlePlaybackMode.NONE -> R.string.subtitle_mode_none
            SubtitlePlaybackMode.SMART -> R.string.subtitle_mode_smart
        }
