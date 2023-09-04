package org.equeim.tremotesf.torrentfile.rpc.requests

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames

@Serializable
private data class ServerVersionRequestArguments(
    @OptIn(ExperimentalSerializationApi::class)
    @SerialName("fields")
    val fields: List<String> = ServerVersionResponseArguments.serializer().descriptor.elementNames.toList(),
)

internal val SERVER_VERSION_REQUEST = RpcRequestBody(RpcMethod.SessionGet, ServerVersionRequestArguments())

@Serializable
internal data class ServerVersionResponseArguments(
    @SerialName("rpc-version")
    val rpcVersion: Int,
    @SerialName("rpc-version-minimum")
    val minimumRpcVersion: Int,
    @SerialName("version")
    val version: String,
)
