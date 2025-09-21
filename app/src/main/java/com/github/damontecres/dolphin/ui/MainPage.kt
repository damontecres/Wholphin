package com.github.damontecres.dolphin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.damontecres.dolphin.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        val api: ApiClient,
    ) : ViewModel() {
        init {
            viewModelScope.launch {
                api.userApi.getCurrentUser().content.configuration?.let {
                    it.orderedViews
                }
            }
        }

        fun todo() {
        }
    }

@Composable
fun MainPage(
    preferences: UserPreferences,
    modifier: Modifier,
    viewModel: MainViewModel = viewModel(),
) {
}
