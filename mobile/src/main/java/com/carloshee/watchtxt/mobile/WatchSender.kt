package com.carloshee.watchtxt.mobile

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Result of sending one file to one watch. */
enum class SendResult { DELIVERED, SENT_UNCONFIRMED, FAILED }

/**
 * Sends text files to the watch over the Wearable Data Layer ChannelClient.
 * Channel path: "/txt/<url-encoded filename>". Content: UTF-8 bytes.
 * The watch closes the channel with appSpecificErrorCode 0 after saving, which we use as a delivery receipt.
 */
class WatchSender(context: Context) {

    companion object {
        const val PATH_PREFIX = "/txt/"
        const val CAPABILITY = "watchtxt_receiver"
        private const val ACK_TIMEOUT_MS = 60_000L
    }

    private val appContext = context.applicationContext
    private val channelClient: ChannelClient = Wearable.getChannelClient(appContext)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    /** Reachable watches that have the watch app installed (advertise our capability). */
    suspend fun watchNodesWithApp(): List<Node> = try {
        capabilityClient.getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.toList()
    } catch (e: Exception) {
        emptyList()
    }

    /** All connected Wear OS nodes (whether or not the watch app is installed). */
    suspend fun connectedNodes(): List<Node> = try {
        nodeClient.connectedNodes.await()
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun send(node: Node, fileName: String, text: String): SendResult =
        withContext(Dispatchers.IO) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val path = PATH_PREFIX + Uri.encode(fileName)
            var channel: ChannelClient.Channel? = null
            var callback: ChannelClient.ChannelCallback? = null
            try {
                val ch = channelClient.openChannel(node.id, path).await()
                channel = ch
                val closed = CompletableDeferred<Pair<Int, Int>>()
                val cb = object : ChannelClient.ChannelCallback() {
                    override fun onChannelClosed(
                        c: ChannelClient.Channel,
                        closeReason: Int,
                        appSpecificErrorCode: Int
                    ) {
                        closed.complete(closeReason to appSpecificErrorCode)
                    }
                }
                callback = cb
                channelClient.registerChannelCallback(ch, cb).await()

                channelClient.getOutputStream(ch).await().use { out ->
                    out.write(bytes)
                    out.flush()
                }

                val result = withTimeoutOrNull(ACK_TIMEOUT_MS) { closed.await() }
                when {
                    result == null -> SendResult.SENT_UNCONFIRMED
                    result.first == ChannelClient.ChannelCallback.CLOSE_REASON_DISCONNECTED -> SendResult.FAILED
                    result.second == 0 -> SendResult.DELIVERED
                    else -> SendResult.FAILED
                }
            } catch (e: Exception) {
                SendResult.FAILED
            } finally {
                val ch = channel
                val cb = callback
                if (ch != null && cb != null) {
                    try {
                        channelClient.unregisterChannelCallback(ch, cb).await()
                    } catch (_: Exception) {
                    }
                }
                if (ch != null) {
                    try {
                        channelClient.close(ch).await()
                    } catch (_: Exception) {
                    }
                }
            }
        }
}
