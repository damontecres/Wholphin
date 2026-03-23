package com.github.damontecres.wholphin.ui.detail.collection

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsAspectRatio
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsColumns
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsContentScale
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsDetailHeader
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsImageType
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsReset
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsShowTitles
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsSpacing
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.serialization.Serializable

@Composable
fun CollectionViewOptionsDialog(
    viewOptions: CollectionViewOptions,
    onDismissRequest: () -> Unit,
    onViewOptionsChange: (CollectionViewOptions) -> Unit,
    defaultViewOptions: CollectionViewOptions = CollectionViewOptions(),
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val columnState = rememberLazyListState()
    val options =
        if (viewOptions.separateTypes) CollectionViewOptions.SeparateOptions else CollectionViewOptions.MixedOptions
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.END)
            window.setDimAmount(0f)
        }
        Column(
            modifier =
                Modifier
                    .width(256.dp)
                    .heightIn(max = 380.dp)
                    .focusRequester(focusRequester)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.view_options),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                state = columnState,
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    val pref = CollectionViewOptions.SeparateTypes
                    val interactionSource = remember { MutableInteractionSource() }
                    ComposablePreference(
                        preference = pref,
                        value = viewOptions.separateTypes,
                        onNavigate = {},
                        onValueChange = { newValue ->
                            onViewOptionsChange.invoke(pref.setter(viewOptions, newValue))
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier,
                        onClickPreference = {},
                    )
                }
                items(options, key = { it.title }) { pref ->
                    pref as AppPreference<ViewOptions, Any>
                    val interactionSource = remember { MutableInteractionSource() }
                    val value = pref.getter.invoke(viewOptions.cardViewOptions)
                    ComposablePreference(
                        preference = pref,
                        value = value,
                        onNavigate = {},
                        onValueChange = { newValue ->
                            val newCardViewOptions =
                                pref.setter(viewOptions.cardViewOptions, newValue)
                            onViewOptionsChange.invoke(viewOptions.copy(cardViewOptions = newCardViewOptions))
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier.animateItem(),
                        onClickPreference = { pref ->
                            if (pref == ViewOptionsReset) {
                                onViewOptionsChange.invoke(defaultViewOptions)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Serializable
data class CollectionViewOptions(
    val separateTypes: Boolean = false,
    val cardViewOptions: ViewOptions = ViewOptions(),
) {
    companion object {
        val SeparateTypes =
            AppSwitchPreference<CollectionViewOptions>(
                title = R.string.separate_types,
                defaultValue = false,
                getter = { it.separateTypes },
                setter = { vo, value -> vo.copy(separateTypes = value) },
            )

        val MixedOptions =
            listOf(
                ViewOptionsImageType,
                ViewOptionsAspectRatio,
                ViewOptionsDetailHeader,
                ViewOptionsShowTitles,
                ViewOptionsColumns,
                ViewOptionsSpacing,
                ViewOptionsContentScale,
                ViewOptionsReset,
            )

        val SeparateOptions =
            listOf(
                ViewOptionsDetailHeader,
                ViewOptionsShowTitles,
                ViewOptionsReset,
            )
    }
}
