package com.github.damontecres.dolphin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.datastore.core.DataStore
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.ServerRepository
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.ServerLoginPage
import com.github.damontecres.dolphin.ui.theme.DolphinTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var userPreferencesDataStore: DataStore<UserPreferences>

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
                    val preferences by userPreferencesDataStore.data.collectAsState(null)
                    preferences?.let { preferences ->
                        if (preferences.currentServerId.isNotBlank() && preferences.currentUserId.isNotBlank()) {
                            scope.launch {
                                serverRepository.restoreSession(
                                    preferences.currentServerId,
                                    preferences.currentUserId,
                                )
                            }
                        } else {
                            ServerLoginPage(modifier = Modifier.fillMaxSize())
                        }
                        val server = serverRepository.currentServer
                        val user = serverRepository.currentUser
                        if (server != null && user != null) {
                            Text("Logged in as ${user.name} on ${server.url}")
                        }
                    }
                }
            }
        }
    }
}
