package com.github.damontecres.wholphin.ui.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.util.ConcatStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider

sealed interface PreferredLanguageType {
    val displayString: StringProvider

    data class ServerProfile(
        val name: String?,
    ) : PreferredLanguageType {
        override val displayString: StringProvider
            get() =
                ConcatStringProvider(
                    " - ",
                    buildList {
                        add(ResStringProvider(R.string.use_server_profile))
                        if (name.isNotNullOrBlank()) {
                            add(StringStringProvider(name))
                        }
                    },
                )
    }

    data object AnyLanguage : PreferredLanguageType {
        override val displayString: StringProvider
            get() = ResStringProvider(R.string.any_language)
    }

    data class Language(
        val iso: String,
        val name: String,
    ) : PreferredLanguageType {
        override val displayString: StringProvider
            get() = StringStringProvider(name)
    }
}

data class PreferredLanguage(
    val selectedIndex: Int = 0,
    val options: List<PreferredLanguageType> = emptyList(),
) {
    val selected: PreferredLanguageType get() = options[selectedIndex]
}
