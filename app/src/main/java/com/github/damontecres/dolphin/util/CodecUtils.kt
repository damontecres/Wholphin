package com.github.damontecres.dolphin.util

import androidx.media3.common.MimeTypes
import com.github.damontecres.dolphin.util.profile.Codec

val subtitleMimeTypes =
    mapOf(
        Codec.Subtitle.ASS to MimeTypes.TEXT_SSA,
        Codec.Subtitle.DVBSUB to MimeTypes.APPLICATION_VOBSUB,
        Codec.Subtitle.DVBSUB to MimeTypes.APPLICATION_VOBSUB,
        Codec.Subtitle.PGS to MimeTypes.APPLICATION_PGS,
        Codec.Subtitle.PGSSUB to MimeTypes.APPLICATION_PGS,
        Codec.Subtitle.SRT to MimeTypes.APPLICATION_SUBRIP,
        Codec.Subtitle.SSA to MimeTypes.TEXT_SSA,
        Codec.Subtitle.SUBRIP to MimeTypes.APPLICATION_SUBRIP,
        Codec.Subtitle.TTML to MimeTypes.APPLICATION_TTML,
        Codec.Subtitle.VTT to MimeTypes.TEXT_VTT,
        Codec.Subtitle.WEBVTT to MimeTypes.TEXT_VTT,
    )
