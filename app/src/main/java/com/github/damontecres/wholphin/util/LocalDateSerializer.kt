package com.github.damontecres.wholphin.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor
        get() = SerialDescriptor("LocalDate", String.serializer().descriptor)

    override fun serialize(
        encoder: Encoder,
        value: LocalDate,
    ) {
        encoder.encodeString(DateTimeFormatter.ISO_LOCAL_DATE.format(value))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        decoder.decodeString().let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
}
