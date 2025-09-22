package com.github.damontecres.dolphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.unit.dp
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
import com.github.damontecres.dolphin.data.ServerRepository
import com.github.damontecres.dolphin.data.model.Library
import com.github.damontecres.dolphin.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
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
                Timber.v("userViews: ${userViews.map { it.type }}")
                libraries.value = userViews.map { Library.fromDto(it, api) }
            }
        }
    }

@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    navigationManager: NavigationManager,
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
                            onClick = {},
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Home",
                            icon = Icons.Default.Home,
                            onClick = {},
                        )
                    }
                    items(libraries) {
                        LibraryNavItem(
                            library = it,
                            onClick = { navigationManager.navigateTo(Destination.MediaItem(it.id)) },
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Settings",
                            icon = Icons.Default.Settings,
                            onClick = {},
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
        when (library.type) {
            CollectionType.MOVIES -> Icons.Default.Email
            CollectionType.TVSHOWS -> Icons.Default.DateRange
            CollectionType.HOMEVIDEOS -> Icons.Default.ShoppingCart
            else -> Icons.Default.Info
        }
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
            text = library.name ?: library.id.toString(),
            maxLines = 1,
        )
    }
}
