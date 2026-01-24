package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun HomeRowSettings(
    title: String,
    viewOptions: HomeRowViewOptions,
    onViewOptionsChange: (HomeRowViewOptions) -> Unit,
    onApplyApplyAll: () -> Unit,
    modifier: Modifier = Modifier,
    defaultViewOptions: HomeRowViewOptions = HomeRowViewOptions(),
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyColumn {
            itemsIndexed(HomeRowViewOptions.OPTIONS) { index, pref ->
                pref as AppPreference<HomeRowViewOptions, Any>
                val interactionSource = remember { MutableInteractionSource() }
                val value = pref.getter.invoke(viewOptions)
                ComposablePreference(
                    preference = pref,
                    value = value,
                    onNavigate = {},
                    onValueChange = { newValue ->
                        onViewOptionsChange.invoke(pref.setter(viewOptions, newValue))
                    },
                    interactionSource = interactionSource,
                    onClickPreference = { pref ->
                        if (pref == HomeRowViewOptions.ViewOptionsReset) {
                            onViewOptionsChange.invoke(defaultViewOptions)
                        } else if (pref == HomeRowViewOptions.ViewOptionsApplyAll) {
                            onApplyApplyAll.invoke()
                        }
                    },
                    modifier =
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(
                                    5.dp,
                                ),
                            ).ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}
