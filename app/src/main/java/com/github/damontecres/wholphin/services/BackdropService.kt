package com.github.damontecres.wholphin.services

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.theme.getThemeColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.ImageType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(FlowPreview::class)
class BackdropService
    @Inject
    constructor(
        private val imageUrlService: ImageUrlService,
        private val preferences: DataStore<AppPreferences>,
    ) {
        private val _backdropFlow =
            MutableStateFlow<BackdropRequest>(
                BackdropRequest(
                    null,
                    false,
                    genericPrimary = Color.Unspecified,
                    genericSecondary = Color.Unspecified,
                    genericTertiary = Color.Unspecified,
                ),
            )

        val backdropFlow =
            _backdropFlow
                .map {
                    val prefs =
                        preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                    val theme = getThemeColors(prefs.interfacePreferences.appThemeColors).darkScheme
                    BackdropResult(
                        item = it.item,
                        imageUrl = imageUrlService.getItemImageUrl(it.item, ImageType.BACKDROP),
                        fillWidth = if (it.small) .7f else .85f,
                        fillHeight = if (it.small) .7f else 1f,
                        genericPrimary = theme.surfaceVariant,
                        genericSecondary = theme.surface,
                        genericTertiary = theme.surfaceVariant,
                    )
                }

        suspend fun submit(
            item: BaseItem,
            small: Boolean = true,
        ) {
            val imageUrl = imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
            if (backdropFlow.firstOrNull()?.imageUrl != imageUrl) {
                val prefs =
                    preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                val theme = getThemeColors(prefs.interfacePreferences.appThemeColors).darkScheme
                _backdropFlow.emit(
                    BackdropRequest(
                        null,
                        small,
                        genericPrimary = theme.surfaceVariant,
                        genericSecondary = theme.surface,
                        genericTertiary = theme.surfaceVariant,
                    ),
                )
                extractColors(item, small)
            }
        }

        private suspend fun extractColors(
            item: BaseItem,
            small: Boolean,
        ) {
            delay(500)
            val prefs =
                preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
            val theme = getThemeColors(prefs.interfacePreferences.appThemeColors).darkScheme
            _backdropFlow.emit(
                BackdropRequest(
                    item,
                    small,
                    genericPrimary = theme.surfaceVariant,
                    genericSecondary = theme.surface,
                    genericTertiary = theme.surfaceVariant,
                ),
            )
        }
    }

data class BackdropRequest(
    val item: BaseItem?,
    val small: Boolean,
    val genericPrimary: Color,
    val genericSecondary: Color,
    val genericTertiary: Color,
    val dynamicColorPrimary: Color = genericPrimary.copy(alpha = 0.4f),
    val dynamicColorSecondary: Color = genericSecondary.copy(alpha = 0.4f),
    val dynamicColorTertiary: Color = genericTertiary.copy(alpha = 0.35f),
)

data class BackdropResult(
    val item: BaseItem?,
    val imageUrl: String?,
    val fillWidth: Float,
    val fillHeight: Float,
    val genericPrimary: Color,
    val genericSecondary: Color,
    val genericTertiary: Color,
    val dynamicColorPrimary: Color = genericPrimary.copy(alpha = 0.4f),
    val dynamicColorSecondary: Color = genericSecondary.copy(alpha = 0.4f),
    val dynamicColorTertiary: Color = genericTertiary.copy(alpha = 0.35f),
)
