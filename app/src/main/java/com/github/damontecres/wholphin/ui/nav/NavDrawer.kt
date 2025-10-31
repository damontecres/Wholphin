package com.github.damontecres.wholphin.ui.nav

import android.content.Context
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.spacedByWithFooter
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.DeviceProfile
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        private val navDrawerItemRepository: NavDrawerItemRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private var all: List<NavDrawerItem>? = null
        val moreLibraries = MutableLiveData<List<NavDrawerItem>>(null)
        val libraries = MutableLiveData<List<NavDrawerItem>>(listOf())
        val selectedIndex = MutableLiveData(-1)
        val showMore = MutableLiveData(false)

        fun init() {
            viewModelScope.launchIO {
                val all = all ?: navDrawerItemRepository.getNavDrawerItems()
                this@NavDrawerViewModel.all = all
                val libraries = navDrawerItemRepository.getFilteredNavDrawerItems(all)
                val moreLibraries = all.toMutableList().apply { removeAll(libraries) }

                withContext(Dispatchers.Main) {
                    this@NavDrawerViewModel.moreLibraries.value = moreLibraries
                    this@NavDrawerViewModel.libraries.value = libraries
                }
                val asDestinations =
                    (libraries + listOf(NavDrawerItem.More) + moreLibraries).map {
                        if (it is ServerNavDrawerItem) {
                            it.destination
                        } else if (it is NavDrawerItem.Favorites) {
                            Destination.Favorites
                        } else {
                            null
                        }
                    }

                val backstack = navigationManager.backStack.toList().reversed()
                for (i in 0..<backstack.size) {
                    val key = backstack[i]
                    if (key is Destination) {
                        val index =
                            if (key is Destination.Home) {
                                -1
                            } else if (key is Destination.Search) {
                                -2
                            } else {
                                val idx = asDestinations.indexOf(key)
                                if (idx >= 0) {
                                    idx
                                } else {
                                    null
                                }
                            }
                        Timber.v("Found $index => $key")
                        if (index != null) {
                            selectedIndex.setValueOnMain(index)
                            break
                        }
                    }
                }
            }
        }

        fun setIndex(index: Int) {
            selectedIndex.value = index
        }

        fun setShowMore(value: Boolean) {
            showMore.value = value
        }
    }

sealed interface NavDrawerItem {
    val id: String

    fun name(context: Context): String

    object Favorites : NavDrawerItem {
        override val id: String
            get() = "a_favorites"

        override fun name(context: Context): String = context.getString(R.string.favorites)
    }

    object More : NavDrawerItem {
        override val id: String
            get() = "a_more"

        override fun name(context: Context): String = context.getString(R.string.more)
    }
}

data class ServerNavDrawerItem(
    val itemId: UUID,
    val name: String,
    val destination: Destination,
    val type: CollectionType,
) : NavDrawerItem {
    override val id: String = "s_" + itemId.toServerString()

    override fun name(context: Context): String = name
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
    val focusRequester = remember { FocusRequester() }

    // If the user presses back while on the home page, open the nav drawer, another back press will quit the app
    BackHandler(enabled = (drawerState.currentValue == DrawerValue.Closed && destination is Destination.Home)) {
        drawerState.setValue(DrawerValue.Open)
        drawerFocusRequester.requestFocus()
    }
    val moreLibraries by viewModel.moreLibraries.observeAsState(listOf())
    val libraries by viewModel.libraries.observeAsState(listOf())
    LaunchedEffect(Unit) { viewModel.init() }

    val showMore by viewModel.showMore.observeAsState(false)
//    val libraries = if (showPinnedOnly) pinnedLibraries else allLibraries
    // A negative index is a built in page, >=0 is a library
    val selectedIndex by viewModel.selectedIndex.observeAsState(-1)
    var focusedIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val derivedFocusedIndex by remember { derivedStateOf { focusedIndex } }

    fun setShowMore(value: Boolean) {
        viewModel.setShowMore(value)
    }

    BackHandler(enabled = showMore && drawerState.currentValue == DrawerValue.Open) {
        setShowMore(false)
    }

    val onClick = { index: Int, item: NavDrawerItem ->
        when (item) {
            NavDrawerItem.Favorites -> {
                viewModel.setIndex(index)
                viewModel.navigationManager.navigateToFromDrawer(
                    Destination.Favorites,
                )
            }

            NavDrawerItem.More -> {
                setShowMore(!showMore)
            }

            is ServerNavDrawerItem -> {
                viewModel.setIndex(index)
                viewModel.navigationManager.navigateToFromDrawer(item.destination)
            }
        }
    }
    if (preferences.appPreferences.interfacePreferences.navDrawerSwitchOnFocus) {
        LaunchedEffect(derivedFocusedIndex) {
            val index = derivedFocusedIndex
            delay(600)
            if (index != selectedIndex) {
                if (index == -1) {
                    viewModel.setIndex(-1)
                    viewModel.navigationManager.goToHome()
                } else if (index in libraries.indices) {
                    if (moreLibraries.isEmpty() || index != libraries.lastIndex) {
                        libraries.getOrNull(index)?.let {
                            onClick.invoke(index, it)
                        }
                    }
                } else {
                    val newIndex = libraries.size - index + 1
                    if (newIndex in moreLibraries.indices) {
                        moreLibraries.getOrNull(newIndex)?.let {
                            onClick.invoke(index, it)
                        }
                    }
                }
            }
        }
    }

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
                        // Even though some must be clicked, focusing on it should clear other focused items
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        LaunchedEffect(focused) { if (focused) focusedIndex = Int.MIN_VALUE }
                        IconNavItem(
                            text = user?.name ?: "",
                            subtext = server?.name ?: server?.url,
                            icon = Icons.Default.AccountCircle,
                            selected = false,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.navigationManager.navigateTo(Destination.UserList)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        LaunchedEffect(focused) { if (focused) focusedIndex = -2 }
                        IconNavItem(
                            text = "Search",
                            icon = Icons.Default.Search,
                            selected = selectedIndex == -2,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.setIndex(-2)
                                viewModel.navigationManager.navigateToFromDrawer(Destination.Search)
                            },
                            modifier =
                                Modifier
                                    .ifElse(
                                        selectedIndex == -2,
                                        Modifier.focusRequester(focusRequester),
                                    ).animateItem(),
                        )
                    }
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        LaunchedEffect(focused) { if (focused) focusedIndex = -1 }
                        IconNavItem(
                            text = "Home",
                            icon = Icons.Default.Home,
                            selected = selectedIndex == -1,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.setIndex(-1)
                                if (destination is Destination.Home) {
                                    viewModel.navigationManager.reloadHome()
                                } else {
                                    viewModel.navigationManager.goToHome()
                                }
                            },
                            modifier =
                                Modifier
                                    .ifElse(
                                        selectedIndex == -1,
                                        Modifier.focusRequester(focusRequester),
                                    ).animateItem(),
                        )
                    }
                    itemsIndexed(libraries) { index, it ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        LaunchedEffect(focused) { if (focused) focusedIndex = index }
                        NavItem(
                            library = it,
                            selected = selectedIndex == index,
                            moreExpanded = showMore,
                            interactionSource = interactionSource,
                            onClick = {
                                onClick.invoke(index, it)
                                if (it !is NavDrawerItem.More) setShowMore(false)
                            },
                            modifier =
                                Modifier
                                    .ifElse(
                                        selectedIndex == index,
                                        Modifier.focusRequester(focusRequester),
                                    ).animateItem(),
                        )
                    }
                    if (showMore) {
                        itemsIndexed(moreLibraries) { index, it ->
                            val adjustedIndex = (index + libraries.size + 1)
                            val interactionSource = remember { MutableInteractionSource() }
                            val focused by interactionSource.collectIsFocusedAsState()
                            LaunchedEffect(focused) { if (focused) focusedIndex = adjustedIndex }
                            NavItem(
                                library = it,
                                selected = selectedIndex == adjustedIndex,
                                moreExpanded = showMore,
                                onClick = { onClick.invoke(adjustedIndex, it) },
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                interactionSource = interactionSource,
                                modifier =
                                    Modifier
                                        .ifElse(
                                            selectedIndex == adjustedIndex,
                                            Modifier.focusRequester(focusRequester),
                                        ).animateItem(),
                            )
                        }
                    }
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        LaunchedEffect(focused) { if (focused) focusedIndex = Int.MIN_VALUE }
                        IconNavItem(
                            text = "Settings",
                            icon = Icons.Default.Settings,
                            selected = false,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.navigationManager.navigateTo(
                                    Destination.Settings(
                                        PreferenceScreenOption.BASIC,
                                    ),
                                )
                            },
                            modifier = Modifier.animateItem(),
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
                    selected -> MaterialTheme.colorScheme.border
                    isFocused -> LocalContentColor.current
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
fun NavigationDrawerScope.NavItem(
    library: NavDrawerItem,
    onClick: () -> Unit,
    selected: Boolean,
    moreExpanded: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    containerColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val useFont = library !is ServerNavDrawerItem || library.type != CollectionType.LIVETV
    val icon =
        when (library) {
            NavDrawerItem.Favorites -> R.string.fa_heart
            NavDrawerItem.More -> R.string.fa_ellipsis

            is ServerNavDrawerItem ->
                when (library.type) {
                    CollectionType.MOVIES -> R.string.fa_film
                    CollectionType.TVSHOWS -> R.string.fa_tv
                    CollectionType.HOMEVIDEOS -> R.string.fa_video
                    CollectionType.LIVETV -> R.drawable.gf_dvr
                    CollectionType.MUSIC -> R.string.fa_music
                    CollectionType.BOXSETS -> R.string.fa_open_folder
                    CollectionType.PLAYLISTS -> R.string.fa_list_ul
                    else -> R.string.fa_film
                }
        }
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        colors =
            NavigationDrawerItemDefaults.colors(
                containerColor = containerColor,
            ),
        leadingContent = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (useFont) {
                    Text(
                        text = stringResource(icon),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontFamily = FontAwesome,
                        color = if (selected) MaterialTheme.colorScheme.border else LocalContentColor.current,
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.border else LocalContentColor.current,
                    )
                }
            }
        },
        trailingContent = {
            if (library is NavDrawerItem.More) {
                Icon(
                    imageVector = if (moreExpanded) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowLeft,
                    contentDescription = null,
                )
            }
        },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = library.name(context),
            maxLines = 1,
        )
    }
}
