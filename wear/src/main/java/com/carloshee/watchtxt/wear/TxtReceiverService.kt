package com.carloshee.watchtxt.wear

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit

/**
 * Receives files sent by the phone over ChannelClient (path "/txt/<url-encoded name>").
 * Callbacks of WearableListenerService run on a background thread, so blocking here is fine.
 * After saving, the channel is closed with error code 0 (success) or 1 (failure); the phone
 * uses that as its delivery receipt.
 */
class TxtReceiverService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (!channel.path.startsWith(PATH_PREFIX)) return
        val client = Wearable.getChannelClient(this)
        var code = 1
        try {
            val name = TxtStore.safeName(Uri.decode(channel.path.removePrefix(PATH_PREFIX)))
            val input = Tasks.await(client.getInputStream(channel), 30, TimeUnit.SECONDS)
            input.use { TxtStore.save(this, name, it) }
            code = 0
            Log.i(TAG, "Saved $name")
        } catch (e: Exception) {
            Log.e(TAG, "Receive failed for ${channel.path}", e)
        } finally {
            try {
                Tasks.await(client.close(channel, code), 10, TimeUnit.SECONDS)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val PATH_PREFIX = "/txt/"
        private const val TAG = "TxtReceiver"
    }
}
