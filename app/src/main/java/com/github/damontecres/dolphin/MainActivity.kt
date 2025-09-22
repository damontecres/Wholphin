package com.github.damontecres.dolphin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.github.damontecres.dolphin.data.ServerRepository
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.ServerLoginPage
import com.github.damontecres.dolphin.ui.nav.ApplicationContent
import com.github.damontecres.dolphin.ui.theme.DolphinTheme
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var userPreferencesDataStore: DataStore<UserPreferences>

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            DolphinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                ) {
                    CoilConfig(serverRepository, okHttpClient, false)

                    var isRestoringSession by remember { mutableStateOf(true) }
                    val preferences by userPreferencesDataStore.data.collectAsState(null)
                    preferences?.let { preferences ->
                        LaunchedEffect(Unit) {
                            if (preferences.currentServerId.isNotBlank() && preferences.currentUserId.isNotBlank()) {
                                serverRepository.restoreSession(
                                    preferences.currentServerId,
                                    preferences.currentUserId,
                                )
                            }
                            isRestoringSession = false
                        }
                        val server = serverRepository.currentServer
                        val user = serverRepository.currentUser
                        if (server != null && user != null) {
                            ApplicationContent(
                                preferences = preferences,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (isRestoringSession) {
                            Box(
                                modifier = Modifier.size(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.border,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        } else {
                            ServerLoginPage(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
