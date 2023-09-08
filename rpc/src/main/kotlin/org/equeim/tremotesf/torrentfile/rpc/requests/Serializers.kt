// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests

import android.util.SparseArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.threeten.bp.Instant
import org.threeten.bp.LocalTime
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal object SecondsToDurationSerializer : KSerializer<Duration> {
    override val descriptor =
        PrimitiveSerialDescriptor(SecondsToDurationSerializer::class.qualifiedName!!, PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Duration = decoder.decodeLong().seconds
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeLong(value.inWholeSeconds)
}

internal object OptionalSecondsToDurationSerializer : KSerializer<Duration?> {
    override val descriptor =
        PrimitiveSerialDescriptor(OptionalSecondsToDurationSerializer::class.qualifiedName!!, PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Duration? = decoder.decodeLong().takeIf { it >= 0 }?.seconds
    override fun serialize(encoder: Encoder, value: Duration?) = encoder.encodeLong(value?.inWholeSeconds ?: -1)
}

internal object MinutesToDurationSerializer : KSerializer<Duration> {
    override val descriptor =
        PrimitiveSerialDescriptor(SecondsToDurationSerializer::class.qualifiedName!!, PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Duration = decoder.decodeLong().minutes
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeLong(value.inWholeMinutes)
}

internal object MinutesSinceStartOfDaySerializer : KSerializer<LocalTime> {
    override val descriptor =
        PrimitiveSerialDescriptor(MinutesSinceStartOfDaySerializer::class.qualifiedName!!, PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.ofSecondOfDay(decoder.decodeLong() * 60)
    override fun serialize(encoder: Encoder, value: LocalTime) =
        encoder.encodeLong((value.toSecondOfDay() / 60).toLong())
}

internal object UnixTimeToInstantSerializer : KSerializer<Instant?> {
    override val descriptor =
        PrimitiveSerialDescriptor(UnixTimeToInstantSerializer::class.qualifiedName!!, PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant? {
        val time = decoder.decodeLong()
        return if (time > 0) {
            Instant.ofEpochSecond(time)
        } else {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Instant?) = throw NotImplementedError()
}

internal interface RpcEnum {
    val rpcValue: Int

    abstract class Serializer<T>(klass: KClass<T>) : KSerializer<T> where T : Enum<T>, T : RpcEnum {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(klass.qualifiedName!!, PrimitiveKind.INT)
        private val mapping: SparseArray<T> = klass.java.enumConstants!!.let { values ->
            SparseArray<T>(values.size).apply {
                values.forEach { append(it.rpcValue, it) }
            }
        }
        private val enumClassName = klass.simpleName!!

        override fun deserialize(decoder: Decoder): T = deserialize(decoder.decodeInt())

        fun deserialize(rpcValue: Int): T {
            return mapping[rpcValue] ?: throw SerializationException("Unknown $enumClassName value $rpcValue")
        }

        override fun serialize(encoder: Encoder, value: T) {
            encoder.encodeInt(value.rpcValue)
        }
    }
}
