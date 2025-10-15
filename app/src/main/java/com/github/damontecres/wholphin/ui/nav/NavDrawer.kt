package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.JellyfinServer
import com.github.damontecres.wholphin.data.JellyfinUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.spacedByWithFooter
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.supportedCollectionTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.DeviceProfile
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        val serverRepository: ServerRepository,
        val api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val libraries = MutableLiveData<List<BaseItem>>(listOf())
        val selectedIndex = MutableLiveData<Int>(-1)

        init {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler(true)) {
                val userViews =
                    api.userViewsApi
                        .getUserViews()
                        .content.items
                val libraries =
                    userViews
                        .filter { it.collectionType in supportedCollectionTypes }
                        .map { BaseItem.from(it, api) }
                Timber.d("Got ${userViews.size} user views filtered to ${libraries.size}")
                withContext(Dispatchers.Main) {
                    this@NavDrawerViewModel.libraries.value = libraries
                }
            }
        }

        fun setIndex(index: Int) {
            selectedIndex.value = index
        }
    }

/**
 * Display the left side navigation drawer with [DestinationContent] on the right
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser?,
    server: JellyfinServer?,
    deviceProfile: DeviceProfile,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server?.id}_${user?.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerFocusRequester = remember { FocusRequester() }

    // If the user presses back while on the home page, open the nav drawer, another back press will quit the app
    BackHandler(enabled = (drawerState.currentValue == DrawerValue.Closed && destination is Destination.Home)) {
        drawerState.setValue(DrawerValue.Open)
        drawerFocusRequester.requestFocus()
    }
    val libraries by viewModel.libraries.observeAsState(listOf())

    // A negative index is a built in page, >=0 is a library
    val selectedIndex by viewModel.selectedIndex.observeAsState(-1)
    val focusRequester = remember { FocusRequester() }

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
                    verticalArrangement = Arrangement.spacedByWithFooter(8.dp),
                    modifier =
                        Modifier
                            .focusGroup()
                            .focusProperties {
                                onEnter = {
                                    focusRequester.tryRequestFocus()
                                }
                            }.fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                ) {
                    item {
                        IconNavItem(
                            text = user?.name ?: "",
                            subtext = server?.name ?: server?.url,
                            icon = Icons.Default.AccountCircle,
                            selected = false,
                            onClick = {
                                viewModel.navigationManager.navigateTo(Destination.UserList)
                            },
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Search",
                            icon = Icons.Default.Search,
                            selected = selectedIndex == -2,
                            onClick = {
                                viewModel.setIndex(-2)
                                viewModel.navigationManager.navigateToFromDrawer(Destination.Search)
                            },
                            modifier =
                                Modifier.ifElse(
                                    selectedIndex == -2,
                                    Modifier.focusRequester(focusRequester),
                                ),
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Home",
                            icon = Icons.Default.Home,
                            selected = selectedIndex == -1,
                            onClick = {
                                viewModel.setIndex(-1)
                                if (destination is Destination.Home) {
                                    viewModel.navigationManager.reloadHome()
                                } else {
                                    viewModel.navigationManager.goToHome()
                                }
                            },
                            modifier =
                                Modifier.ifElse(
                                    selectedIndex == -1,
                                    Modifier.focusRequester(focusRequester),
                                ),
                        )
                    }
                    itemsIndexed(libraries) { index, it ->
                        LibraryNavItem(
                            library = it,
                            selected = selectedIndex == index,
                            onClick = {
                                viewModel.setIndex(index)
                                viewModel.navigationManager.navigateToFromDrawer(it.destination())
                            },
                            modifier =
                                Modifier.ifElse(
                                    selectedIndex == index,
                                    Modifier.focusRequester(focusRequester),
                                ),
                        )
                    }
                    item {
                        IconNavItem(
                            text = "Settings",
                            icon = Icons.Default.Settings,
                            selected = false,
                            onClick = {
                                viewModel.navigationManager.navigateTo(
                                    Destination.Settings(
                                        PreferenceScreenOption.BASIC,
                                    ),
                                )
                            },
                            modifier = Modifier,
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
    selected: Boolean,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        leadingContent = {
            val color =
                when {
                    isFocused -> LocalContentColor.current
                    selected -> MaterialTheme.colorScheme.border
                    else -> LocalContentColor.current
                }
            Icon(
                icon,
                contentDescription = null,
                tint = color,
            )
        },
        supportingContent =
            subtext?.let {
                {
                    Text(
                        text = it,
                        maxLines = 1,
                    )
                }
            },
        interactionSource = interactionSource,
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
    library: BaseItem,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val icon =
        when (library.data.collectionType) {
            CollectionType.MOVIES -> R.string.fa_film
            CollectionType.TVSHOWS -> R.string.fa_tv
            CollectionType.HOMEVIDEOS -> R.string.fa_video
            CollectionType.LIVETV -> R.string.fa_tv
            CollectionType.MUSIC -> R.string.fa_music
            CollectionType.BOXSETS -> R.string.fa_open_folder
            CollectionType.PLAYLISTS -> R.string.fa_list_ul
            else -> R.string.fa_film
        }
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val color =
        when {
            isFocused -> Color.Unspecified
            selected -> MaterialTheme.colorScheme.border
            else -> Color.Unspecified
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
                    color = color,
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
