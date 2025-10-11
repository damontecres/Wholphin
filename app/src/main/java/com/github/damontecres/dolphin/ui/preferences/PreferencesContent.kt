package com.github.damontecres.dolphin.ui.preferences

import android.widget.Toast
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.preferences.AppPreference
import com.github.damontecres.dolphin.preferences.AppPreferences
import com.github.damontecres.dolphin.preferences.advancedPreferences
import com.github.damontecres.dolphin.preferences.basicPreferences
import com.github.damontecres.dolphin.preferences.uiPreferences
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.playOnClickSound
import com.github.damontecres.dolphin.ui.playSoundOnFocus
import com.github.damontecres.dolphin.ui.setup.UpdateViewModel
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ExceptionHandler
import kotlinx.coroutines.launch

@Composable
fun PreferencesContent(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
    updateVM: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by rememberSaveable { mutableStateOf(Pair(0, 0)) }
    val state = rememberLazyListState()
    var preferences by remember { mutableStateOf(initialPreferences) }
    LaunchedEffect(Unit) {
        viewModel.preferenceDataStore.data.collect {
            preferences = it
        }
    }

    val release by updateVM.release.observeAsState(null)
    LaunchedEffect(Unit) {
        if (preferences.autoCheckForUpdates) {
            updateVM.init(preferences.updateUrl)
        }
    }

    val movementSounds = true
    val installedVersion = updateVM.currentVersion
    val updateAvailable = release?.version?.isGreaterThan(installedVersion) ?: false

    val prefList =
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC -> basicPreferences
            PreferenceScreenOption.ADVANCED -> advancedPreferences
            PreferenceScreenOption.USER_INTERFACE -> uiPreferences
        }
    val screenTitle =
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC -> "Preferences"
            PreferenceScreenOption.ADVANCED -> "Advanced Preferences"
            PreferenceScreenOption.USER_INTERFACE -> "User Interface Preferences"
        }

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
        LazyColumn(
            state = state,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        ) {
            stickyHeader {
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                )
            }
            if (preferenceScreenOption == PreferenceScreenOption.BASIC &&
                preferences.autoCheckForUpdates &&
                updateAvailable
            ) {
                item {
                    val updateFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (focusedIndex.first == 0 && focusedIndex.second == 0) {
                            // Only re-focus if the user hasn't moved
                            updateFocusRequester.tryRequestFocus()
                        }
                    }
                    ClickPreference(
                        title = stringResource(R.string.install_update),
                        onClick = {
                            if (movementSounds) playOnClickSound(context)
                            viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                        },
                        summary = release?.version?.toString(),
                        modifier =
                            Modifier
                                .focusRequester(updateFocusRequester)
                                .playSoundOnFocus(movementSounds),
                    )
                }
            }
            prefList.forEachIndexed { groupIndex, group ->
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
                group.preferences.forEachIndexed { prefIndex, pref ->
                    pref as AppPreference<Any>
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused = interactionSource.collectIsFocusedAsState().value
                        LaunchedEffect(focused) {
                            if (focused) {
                                focusedIndex = Pair(groupIndex, prefIndex)
                                if (movementSounds) playOnClickSound(context)
                            }
                        }
                        when (pref) {
                            AppPreference.InstalledVersion -> {
                                var clickCount by remember { mutableIntStateOf(0) }
                                ClickPreference(
                                    title = stringResource(R.string.installed_version),
                                    onClick = {
                                        if (movementSounds) playOnClickSound(context)
                                        if (clickCount++ >= 2) {
                                            clickCount = 0
                                            // navigationManager.navigateTo(Destination.Debug)
                                        }
                                    },
                                    summary = installedVersion.toString(),
                                    interactionSource = interactionSource,
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                groupIndex == focusedIndex.first && prefIndex == focusedIndex.second,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }

                            AppPreference.Update -> {
                                ClickPreference(
                                    title =
                                        if (release != null && updateAvailable) {
                                            stringResource(R.string.install_update)
                                        } else if (!preferences.autoCheckForUpdates && release == null) {
                                            stringResource(R.string.check_for_updates)
                                        } else {
                                            stringResource(R.string.no_update_available)
                                        },
                                    onClick = {
                                        if (movementSounds) playOnClickSound(context)
                                        if (release != null && updateAvailable) {
                                            release?.let {
                                                viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                                            }
                                        } else {
                                            updateVM.init(preferences.updateUrl)
                                        }
                                    },
                                    onLongClick = {
                                        if (movementSounds) playOnClickSound(context)
                                        viewModel.navigationManager.navigateTo(Destination.UpdateApp)
                                    },
                                    summary =
                                        if (updateAvailable) {
                                            release?.version?.toString()
                                        } else {
                                            null
                                        },
                                    interactionSource = interactionSource,
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                groupIndex == focusedIndex.first && prefIndex == focusedIndex.second,
                                                Modifier.focusRequester(focusRequester),
                                            ),
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
                                                scope.launch(ExceptionHandler()) {
                                                    preferences =
                                                        viewModel.preferenceDataStore.updateData { prefs ->
                                                            pref.setter(prefs, newValue)
                                                        }
                                                }
                                            }
                                        }
                                    },
                                    interactionSource = interactionSource,
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                groupIndex == focusedIndex.first && prefIndex == focusedIndex.second,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreferencesPage(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        PreferencesContent(
            initialPreferences,
            preferenceScreenOption,
            Modifier
                .fillMaxWidth(.4f)
                .fillMaxHeight()
                .align(Alignment.TopEnd),
        )
    }
}
