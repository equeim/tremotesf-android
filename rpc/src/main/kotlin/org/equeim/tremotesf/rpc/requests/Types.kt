// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.tremotesf.rpc.ServerCapabilities
import org.equeim.tremotesf.rpc.normalizePath

@JvmInline
@Serializable
value class FileSize private constructor(val bytes: Long) {
    companion object {
        fun fromBytes(bytes: Long) = FileSize(bytes)
    }
}

@JvmInline
@Serializable
value class TransferRate private constructor(val bytesPerSecond: Long) {
    val kiloBytesPerSecond: Long get() = bytesPerSecond / 1000

    object KiloBytesPerSecondSerializer : KSerializer<TransferRate> {
        override val descriptor =
            PrimitiveSerialDescriptor(KiloBytesPerSecondSerializer::class.qualifiedName!!, PrimitiveKind.LONG)

        override fun deserialize(decoder: Decoder) = fromKiloBytesPerSecond(decoder.decodeLong())
        override fun serialize(encoder: Encoder, value: TransferRate) = encoder.encodeLong(value.kiloBytesPerSecond)
    }

    companion object {
        fun fromKiloBytesPerSecond(kiloBytesPerSecond: Long) = TransferRate(bytesPerSecond = kiloBytesPerSecond * 1000)
    }
}


data class NormalizedRpcPath internal constructor(
    val value: String,
    internal val serverOs: ServerCapabilities.ServerOs?,
) {
    internal class Serializer(private val serverCapabilities: () -> ServerCapabilities?) :
        KSerializer<NormalizedRpcPath> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(Serializer::class.qualifiedName!!, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): NormalizedRpcPath =
            decoder.decodeString().normalizePath(serverCapabilities())

        override fun serialize(encoder: Encoder, value: NormalizedRpcPath) = encoder.encodeString(value.value)
    }
}

@JvmInline
value class NotNormalizedRpcPath(val value: String) {
    internal class Serializer(private val serverCapabilities: () -> ServerCapabilities?) :
        KSerializer<NotNormalizedRpcPath> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(Serializer::class.qualifiedName!!, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): NotNormalizedRpcPath = NotNormalizedRpcPath(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: NotNormalizedRpcPath) =
            encoder.encodeString(value.value.normalizePath(serverCapabilities()).value)
    }
}

@Serializable(TorrentStatus.Serializer::class)
enum class TorrentStatus(override val rpcValue: Int) : RpcEnum {
    Paused(0),
    QueuedForChecking(1),
    Checking(2),
    QueuedForDownloading(3),
    Downloading(4),
    QueuedForSeeding(5),
    Seeding(6);

    internal object Serializer : RpcEnum.Serializer<TorrentStatus>(TorrentStatus::class)
}
