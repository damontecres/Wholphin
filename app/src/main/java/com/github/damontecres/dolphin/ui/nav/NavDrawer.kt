package com.github.damontecres.dolphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.data.ServerRepository
import com.github.damontecres.dolphin.data.model.Library
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.FontAwesome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.DeviceProfile
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        val serverRepository: ServerRepository,
        val api: ApiClient,
    ) : ViewModel() {
        val libraries = MutableLiveData<List<Library>>(listOf())

        init {
            viewModelScope.launch {
                val userViews =
                    api.userViewsApi
                        .getUserViews()
                        .content.items
//                Timber.v("userViews: $userViews")
                libraries.value = userViews.map { Library.fromDto(it, api) }
            }
        }
    }

@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    deviceProfile: DeviceProfile,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel = hiltViewModel(LocalView.current.findViewTreeViewModelStoreOwner()!!),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerFocusRequester = remember { FocusRequester() }
    BackHandler(enabled = (drawerState.currentValue == DrawerValue.Closed && destination == Destination.Main)) {
        drawerState.setValue(DrawerValue.Open)
        drawerFocusRequester.requestFocus()
    }

    val user = viewModel.serverRepository.currentUser
    val libraries by viewModel.libraries.observeAsState(listOf())

    NavigationDrawer(
        modifier =
            modifier
                .focusRequester(drawerFocusRequester),
        drawerState = drawerState,
        drawerContent = {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                            .focusGroup(),
                ) {
                    item {
                        IconNavItem(
                            text = user?.name ?: "",
                            icon = Icons.Default.AccountCircle,
                            onClick = { navigationManager.navigateTo(Destination.Setup) },
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Search",
                            icon = Icons.Default.Search,
                            onClick = { navigationManager.navigateTo(Destination.Search) },
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Home",
                            icon = Icons.Default.Home,
                            onClick = { navigationManager.goToHome() },
                        )
                    }
                    items(libraries) {
                        LibraryNavItem(
                            library = it,
                            onClick = {
                                navigationManager.navigateTo(
                                    Destination.MediaItem(
                                        it.id,
                                        it.type,
                                    ),
                                )
                            },
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Settings",
                            icon = Icons.Default.Settings,
                            onClick = { navigationManager.navigateTo(Destination.Settings) },
                        )
                    }
                }
            }
        },
    ) {
        // Drawer content
        DestinationContent(
            destination = destination,
            preferences = preferences,
            navigationManager = navigationManager,
            deviceProfile = deviceProfile,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun NavigationDrawerScope.IconNavItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
            )
        },
        interactionSource = null,
    ) {
        Text(
            modifier = Modifier,
            text = text,
            maxLines = 1,
        )
    }
}

@Composable
fun NavigationDrawerScope.LibraryNavItem(
    library: Library,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO
    val icon =
        when (library.collectionType) {
            CollectionType.MOVIES -> R.string.fa_film
            CollectionType.TVSHOWS -> R.string.fa_tv
            CollectionType.HOMEVIDEOS -> R.string.fa_video
            CollectionType.LIVETV -> R.string.fa_tv
            CollectionType.MUSIC -> R.string.fa_music
            else -> R.string.fa_film
        }
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        leadingContent = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(icon),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontFamily = FontAwesome,
                )
            }
        },
        interactionSource = null,
    ) {
        Text(
            modifier = Modifier,
            text = library.name ?: library.id.toString(),
            maxLines = 1,
        )
    }
}
