package com.github.damontecres.wholphin.ui.detail.metadata

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import org.jellyfin.sdk.model.api.ImageInfo
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.RemoteImageInfo
import org.jellyfin.sdk.model.api.RemoteSearchResult

/**
 * The image types that can be managed, with their labels
 */
private val managedImageTypes =
    listOf(
        ImageType.PRIMARY to R.string.image_type_primary,
        ImageType.BACKDROP to R.string.image_type_backdrop,
        ImageType.LOGO to R.string.image_type_logo,
        ImageType.THUMB to R.string.image_type_thumb,
        ImageType.BANNER to R.string.image_type_banner,
        ImageType.ART to R.string.image_type_art,
        ImageType.DISC to R.string.image_type_disc,
    )

private sealed interface Screen {
    data object Main : Screen

    data object Refresh : Screen

    data object Identify : Screen

    data object Images : Screen

    data class ImagesOfType(
        val type: ImageType,
    ) : Screen

    data class FindImages(
        val type: ImageType,
    ) : Screen
}

/**
 * Lets a Jellyfin administrator refresh an item's metadata, fix its match (identify), and choose
 * its images from those offered by the server's metadata providers.
 */
@Composable
fun ManageMetadataDialog(
    item: BaseItem,
    onDismissRequest: () -> Unit,
    viewModel: ManageMetadataViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    LaunchedEffect(item.id) { viewModel.reset() }

    BasicDialog(
        onDismissRequest = {
            // Back goes to the previous screen, and closes from the first one
            screen =
                when (val s = screen) {
                    Screen.Main -> {
                        onDismissRequest.invoke()
                        Screen.Main
                    }

                    Screen.Refresh, Screen.Identify, Screen.Images -> Screen.Main

                    is Screen.ImagesOfType -> Screen.Images

                    is Screen.FindImages -> Screen.ImagesOfType(s.type)
                }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        elevation = 3.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .padding(16.dp)
                    .width(560.dp)
                    .heightIn(max = 480.dp),
        ) {
            Text(
                text = stringResource(R.string.manage_metadata) + " · " + (item.title ?: ""),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (val s = screen) {
                Screen.Main -> {
                    MainScreen(
                        canIdentify = item.type in identifiableTypes,
                        onNavigate = { screen = it },
                    )
                }

                Screen.Refresh -> {
                    RefreshScreen(
                        onRefresh = { option, replaceImages ->
                            viewModel.refresh(item.id, option, replaceImages)
                            onDismissRequest.invoke()
                        },
                    )
                }

                Screen.Identify -> {
                    IdentifyScreen(
                        item = item,
                        results = state.searchResults,
                        onSearch = { name, year -> viewModel.search(item, name, year) },
                        onChoose = { result, replaceImages ->
                            viewModel.applyMatch(item.id, result, replaceImages)
                            onDismissRequest.invoke()
                        },
                    )
                }

                Screen.Images -> {
                    LaunchedEffect(Unit) { viewModel.loadCurrentImages(item.id) }
                    ImagesScreen(
                        currentImages = state.currentImages,
                        onChooseType = { screen = Screen.ImagesOfType(it) },
                    )
                }

                is Screen.ImagesOfType -> {
                    ImagesOfTypeScreen(
                        item = item,
                        type = s.type,
                        currentImages = state.currentImages,
                        onFindImages = {
                            viewModel.loadRemoteImages(item.id, s.type)
                            screen = Screen.FindImages(s.type)
                        },
                        onRemove = { viewModel.deleteImage(item.id, it) },
                    )
                }

                is Screen.FindImages -> {
                    FindImagesScreen(
                        remoteImages = state.remoteImages,
                        onChoose = { url ->
                            viewModel.setImage(item.id, s.type, url)
                            screen = Screen.ImagesOfType(s.type)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    canIdentify: Boolean,
    onNavigate: (Screen) -> Unit,
) {
    LazyColumn {
        item { MenuItem(R.string.refresh_metadata) { onNavigate(Screen.Refresh) } }
        if (canIdentify) {
            item { MenuItem(R.string.identify, R.string.identify_summary) { onNavigate(Screen.Identify) } }
        }
        item { MenuItem(R.string.images) { onNavigate(Screen.Images) } }
    }
}

@Composable
private fun RefreshScreen(onRefresh: (RefreshOption, Boolean) -> Unit) {
    var replaceImages by remember { mutableStateOf(false) }
    LazyColumn {
        item { MenuItem(R.string.refresh_scan) { onRefresh(RefreshOption.SCAN, false) } }
        item { MenuItem(R.string.refresh_missing) { onRefresh(RefreshOption.MISSING, replaceImages) } }
        item { MenuItem(R.string.refresh_replace_all) { onRefresh(RefreshOption.REPLACE_ALL, replaceImages) } }
        item { ReplaceImagesSwitch(replaceImages) { replaceImages = it } }
    }
}

@Composable
private fun IdentifyScreen(
    item: BaseItem,
    results: ListState<RemoteSearchResult>,
    onSearch: (String, Int?) -> Unit,
    onChoose: (RemoteSearchResult, Boolean) -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name ?: "") }
    var year by remember(item.id) { mutableStateOf(item.data.productionYear?.toString() ?: "") }
    // Matches the Jellyfin web UI, which replaces images by default when identifying
    var replaceImages by remember { mutableStateOf(true) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditTextBox(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.weight(1f),
                )
                EditTextBox(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    placeholder = { Text(stringResource(R.string.year)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
                TextButton(
                    stringRes = R.string.search,
                    onClick = { onSearch(name.trim(), year.toIntOrNull()) },
                    enabled = name.isNotBlank(),
                )
            }
        }
        item { ReplaceImagesSwitch(replaceImages) { replaceImages = it } }
        when (results) {
            ListState.NotLoaded -> {}

            ListState.Loading -> item { StatusText(stringResource(R.string.loading)) }

            is ListState.Error -> item { StatusText(stringResource(R.string.error) + ": " + (results.message ?: "")) }

            is ListState.Success -> {
                if (results.items.isEmpty()) {
                    item { StatusText(stringResource(R.string.no_results)) }
                }
                items(results.items) { result ->
                    ListItem(
                        selected = false,
                        onClick = { onChoose(result, replaceImages) },
                        leadingContent = {
                            AsyncImage(
                                model = result.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.height(64.dp).aspectRatio(2f / 3f),
                            )
                        },
                        headlineContent = {
                            Text(listOfNotNull(result.name, result.productionYear?.let { "($it)" }).joinToString(" "))
                        },
                        supportingContent = {
                            Text(
                                text = listOfNotNull(result.searchProviderName, result.overview).joinToString(" · "),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagesScreen(
    currentImages: ListState<ImageInfo>,
    onChooseType: (ImageType) -> Unit,
) {
    val images = (currentImages as? ListState.Success)?.items.orEmpty()
    LazyColumn {
        items(managedImageTypes) { (type, label) ->
            val count = images.count { it.imageType == type }
            ListItem(
                selected = false,
                onClick = { onChooseType(type) },
                headlineContent = { Text(stringResource(label)) },
                supportingContent = {
                    Text(
                        if (count > 0) {
                            pluralStringResource(R.plurals.image_count, count, count)
                        } else {
                            stringResource(R.string.none)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ImagesOfTypeScreen(
    item: BaseItem,
    type: ImageType,
    currentImages: ListState<ImageInfo>,
    onFindImages: () -> Unit,
    onRemove: (ImageInfo) -> Unit,
) {
    val imageUrlService = LocalImageUrlService.current
    val images = (currentImages as? ListState.Success)?.items.orEmpty().filter { it.imageType == type }
    // Removing deletes the image on the server, so confirm first
    var confirmRemove by remember { mutableStateOf<ImageInfo?>(null) }
    confirmRemove?.let { image ->
        BasicDialog(
            onDismissRequest = { confirmRemove = null },
            elevation = 5.dp,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.remove_image_confirm),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        stringRes = R.string.remove,
                        onClick = {
                            onRemove(image)
                            confirmRemove = null
                        },
                    )
                    TextButton(stringRes = R.string.cancel, onClick = { confirmRemove = null })
                }
            }
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { MenuItem(R.string.find_images, R.string.find_images_summary, onClick = onFindImages) }
        items(images) { image ->
            ListItem(
                selected = false,
                onClick = { confirmRemove = image },
                leadingContent = {
                    AsyncImage(
                        model =
                            imageUrlService.getItemImageUrl(
                                itemId = item.id,
                                imageType = type,
                                tag = image.imageTag,
                                imageIndex = image.imageIndex,
                                maxHeight = 160,
                            ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(64.dp).width(112.dp),
                    )
                },
                headlineContent = { Text(stringResource(R.string.remove)) },
                supportingContent = {
                    Text(listOfNotNull(image.width, image.height).joinToString(" × "))
                },
            )
        }
    }
}

@Composable
private fun FindImagesScreen(
    remoteImages: ListState<RemoteImageInfo>,
    onChoose: (String) -> Unit,
) {
    when (remoteImages) {
        ListState.NotLoaded, ListState.Loading -> {
            StatusText(stringResource(R.string.loading))
        }

        is ListState.Error -> {
            StatusText(stringResource(R.string.error) + ": " + (remoteImages.message ?: ""))
        }

        is ListState.Success -> {
            val images = remoteImages.items.filter { it.url != null }
            if (images.isEmpty()) {
                StatusText(stringResource(R.string.no_results))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(images) { image ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Card(
                            onClick = { image.url?.let(onChoose) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        ) {
                            AsyncImage(
                                model = image.thumbnailUrl ?: image.url,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text =
                                listOfNotNull(
                                    image.providerName,
                                    if (image.width != null && image.height != null) "${image.width} × ${image.height}" else null,
                                    image.language,
                                ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    @StringRes title: Int,
    @StringRes summary: Int? = null,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
    )
}

@Composable
private fun ReplaceImagesSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        selected = false,
        onClick = { onCheckedChange(!checked) },
        headlineContent = { Text(stringResource(R.string.replace_existing_images)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(8.dp),
    )
}
