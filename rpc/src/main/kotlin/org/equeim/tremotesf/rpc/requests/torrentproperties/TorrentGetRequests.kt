package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Suppress("DataClassPrivateConstructor")
@Serializable
internal data class TorrentGetRequestForFields private constructor(
    @SerialName("ids")
    val torrentsHashStrings: List<String>,

    @SerialName("fields")
    val fields: List<String>,
) {
    constructor(torrentHashString: String, field: String) : this(listOf(torrentHashString), listOf(field))
    constructor(torrentHashString: String, fields: List<String>) : this(listOf(torrentHashString), fields)
}

@Serializable
internal data class TorrentGetResponseForFields<FieldsObject : Any>(
    @SerialName("torrents")
    val torrents: List<FieldsObject>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
    }
}
