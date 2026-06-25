package com.github.damontecres.wholphin.ui.preferences

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerPreferencesDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.NavDrawerPinnedItem
import com.github.damontecres.wholphin.data.model.NavPinType
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NavDrawerPin(
    val id: String,
    val title: String,
    val pinned: Boolean,
    val item: NavDrawerItem,
) {
    companion object {
        fun create(
            context: Context,
            items: Map<NavDrawerItem, Boolean>,
        ) {
            items.map { (item, pinned) ->
                NavDrawerPin(item.id, item.name(context), pinned, item)
            }
        }
    }
}

private fun <T> List<T>.move(
    direction: MoveDirection,
    index: Int,
): List<T> =
    toMutableList().apply {
        if (direction == MoveDirection.DOWN) {
            val down = this[index]
            val up = this[index + 1]
            set(index, up)
            set(index + 1, down)
        } else {
            val up = this[index]
            val down = this[index - 1]
            set(index - 1, up)
            set(index, down)
        }
    }

@Composable
fun NavDrawerPreference(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    viewModel: NavDrawerPreferencesViewModel = hiltViewModel(),
) {
    val items by viewModel.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    ClickPreference(
        title = title,
        summary = summary,
        onClick = { showDialog = true },
        interactionSource = interactionSource,
        modifier = modifier,
    )
    if (showDialog) {
        NavDrawerPreferenceDialog(
            items = items,
            onDismissRequest = {
                viewModel.save()
                showDialog = false
            },
            onClick = { index ->
                val newItems =
                    items.toMutableList().apply {
                        set(index, items[index].let { it.copy(pinned = !it.pinned) })
                    }
                viewModel.update(newItems)
            },
            onMoveUp = { index ->
                viewModel.update(items.move(MoveDirection.UP, index))
            },
            onMoveDown = { index ->
                viewModel.update(items.move(MoveDirection.DOWN, index))
            },
        )
    }
}

@Composable
fun NavDrawerPreferenceDialog(
    items: List<NavDrawerPin>,
    onDismissRequest: () -> Unit,
    onClick: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bringIntoViewRequesters = remember { List(items.size) { BringIntoViewRequester() } }
    BasicDialog(
        onDismissRequest = onDismissRequest,
        elevation = 3.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_drawer_pins),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val listState = rememberLazyListState()

            fun ensureVisible(index: Int) {
                val idx = index.coerceIn(items.indices)
                scope.launch {
                    bringIntoViewRequesters[idx].bringIntoView()
                }
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    NavDrawerPreferenceListItem(
                        title = item.title,
                        pinned = item.pinned,
                        moveUpAllowed = index > 0,
                        moveDownAllowed = index < items.lastIndex,
                        onClick = { onClick.invoke(index) },
                        onMoveUp = {
                            onMoveUp.invoke(index)
                            ensureVisible(index - 1)
                        },
                        onMoveDown = {
                            onMoveDown.invoke(index)
                            ensureVisible(index + 1)
                        },
                        modifier =
                            Modifier
                                .animateItem()
                                .bringIntoViewRequester(bringIntoViewRequesters[index]),
                    )
                }
            }
        }
    }
}

@Composable
fun NavDrawerPreferenceListItem(
    title: String,
    pinned: Boolean,
    moveUpAllowed: Boolean,
    moveDownAllowed: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 88.dp),
        ) {
            ListItem(
                selected = false,
                headlineContent = {
                    Text(
                        text = title,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = pinned,
                        onCheckedChange = {
                            onClick.invoke()
                        },
                    )
                },
                onClick = onClick,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth(),
            ) {
                MoveButton(R.string.fa_caret_up, moveUpAllowed, onMoveUp)
                MoveButton(R.string.fa_caret_down, moveDownAllowed, onMoveDown)
            }
        }
    }
}

@Composable
fun MoveButton(
    @StringRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(32.dp),
) {
    Text(
        text = stringResource(icon),
        fontSize = 16.sp,
        fontFamily = FontAwesome,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@PreviewTvSpec
@Composable
fun NavDrawerPreferenceListItemPreview() {
    WholphinTheme {
        NavDrawerPreferenceListItem(
            title = "Movies",
            pinned = true,
            moveUpAllowed = true,
            moveDownAllowed = true,
            onClick = {},
            onMoveUp = {},
            onMoveDown = { },
            modifier = Modifier.width(360.dp),
        )
    }
}

@HiltViewModel
class NavDrawerPreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        private val serverRepository: ServerRepository,
        private val navDrawerService: NavDrawerService,
        private val serverPreferencesDao: ServerPreferencesDao,
        private val seerrServerRepository: SeerrServerRepository,
    ) : ViewModel() {
        val state = MutableStateFlow<List<NavDrawerPin>>(listOf())

        init {
            viewModelScope.launchDefault {
                val state = navDrawerService.state.value
                val user = serverRepository.currentUser
                val seerr = seerrServerRepository.active.firstOrNull()
                if (user == null || seerr == null) {
                    return@launchDefault
                }
                val navDrawerPins =
                    withContext(WholphinDispatchers.IO) {
                        serverPreferencesDao
                            .getNavDrawerPinnedItems(user)
                            .associateBy { it.itemId }
                    }
                val allItems = state.let { it.items + it.moreItems }
                val pins =
                    allItems
                        .sortedBy {
                            navDrawerPins[it.id]?.order?.takeIf { it >= 0 } ?: Int.MAX_VALUE
                        }.mapNotNull {
                            if (!seerr && it is NavDrawerItem.Discover) {
                                null
                            } else {
                                // Assume pinned if unknown
                                val pinned = navDrawerPins[it.id]?.type ?: NavPinType.PINNED
                                NavDrawerPin(
                                    it.id,
                                    it.name(context),
                                    pinned == NavPinType.PINNED,
                                    it,
                                )
                            }
                        }
                this@NavDrawerPreferencesViewModel.state.value = pins
            }
        }

        fun update(items: List<NavDrawerPin>) {
            state.update { items }
        }

        fun save() {
            viewModelScope.launchIO(ExceptionHandler(true)) {
                serverRepository.currentUser?.let { user ->
                    serverRepository.currentUserDto?.let { userDto ->
                        if (user.id == userDto.id) {
                            val toSave =
                                state.value.mapIndexed { index, item ->
                                    NavDrawerPinnedItem(
                                        user.rowId,
                                        item.id,
                                        if (item.pinned) NavPinType.PINNED else NavPinType.UNPINNED,
                                        index,
                                    )
                                }
                            serverPreferencesDao.saveNavDrawerPinnedItems(*toSave.toTypedArray())
                            val discoverActive = seerrServerRepository.active.firstOrNull() == true
                            navDrawerService.updateNavDrawer(user, userDto, discoverActive)
                        } else {
                            throw IllegalStateException("User IDs do not match")
                        }
                    }
                }
            }
        }
    }
