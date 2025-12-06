package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.detail.DebugViewModel.Companion.sendAppLogs
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import javax.inject.Inject

@HiltViewModel
class ErrorViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val clientInfo: ClientInfo,
        private val deviceInfo: DeviceInfo,
    ) : ViewModel() {
        fun sendLogs() {
            sendAppLogs(context, api, clientInfo, deviceInfo)
        }
    }

/**
 * Displays an error message and/or exception
 */
@Composable
fun ErrorMessage(
    message: String?,
    exception: Throwable?,
    modifier: Modifier = Modifier,
    viewModel: ErrorViewModel = hiltViewModel(),
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.compose_error_message_title),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(
            stringRes = R.string.send_app_logs,
            onClick = {
                viewModel.sendLogs()
            },
        )
        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        exception?.localizedMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        var cause = exception?.cause
        while (cause != null) {
            cause.localizedMessage?.let {
                Text(
                    text = "Caused by: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            cause = cause.cause
        }
    }
}

@Composable
fun ErrorMessage(
    error: LoadingState.Error,
    modifier: Modifier = Modifier,
) = ErrorMessage(
    message = error.message,
    exception = error.exception,
    modifier = modifier,
)
