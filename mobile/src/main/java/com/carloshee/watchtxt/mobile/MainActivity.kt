package com.carloshee.watchtxt.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SentItem(
    val name: String,
    val chars: Int,
    val status: String,
    val time: String
)

/** Process-wide list of what was sent (survives rotation / new share intents). */
object SentLog {
    val items = mutableStateListOf<SentItem>()
}

class MainActivity : ComponentActivity() {

    private lateinit var sender: WatchSender

    private var nodeLabel by mutableStateOf("正在查找手表…")
    private var busy by mutableStateOf(false)

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) sendUris(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sender = WatchSender(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhoneScreen(
                        nodeLabel = nodeLabel,
                        busy = busy,
                        items = SentLog.items,
                        onPick = { pickLauncher.launch(arrayOf("text/plain")) },
                        onRefresh = { refreshNodes() }
                    )
                }
            }
        }

        if (savedInstanceState == null) handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshNodes()
    }

    private fun refreshNodes() {
        lifecycleScope.launch {
            val withApp = sender.watchNodesWithApp()
            nodeLabel = if (withApp.isNotEmpty()) {
                "已连接手表：" + withApp.joinToString { it.displayName }
            } else {
                val connected = sender.connectedNodes()
                if (connected.isNotEmpty()) {
                    "已连接：" + connected.joinToString { it.displayName } +
                        "\n（但手表上未检测到「手表TXT」应用，请先安装手表端）"
                } else {
                    "未连接手表（请确认 Galaxy Wearable 已连接手表）"
                }
            }
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    sendUris(listOf(uri))
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrEmpty()) {
                        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                        val name = if (!subject.isNullOrBlank()) subject else "分享_" + stamp("yyyyMMdd_HHmmss")
                        sendTexts(listOf(ensureTxt(name) to text))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(
                    intent, Intent.EXTRA_STREAM, Uri::class.java
                )
                if (!uris.isNullOrEmpty()) sendUris(uris)
            }
        }
    }

    private fun sendUris(uris: List<Uri>) {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        val name = ensureTxt(displayName(uri) ?: ("文档_" + stamp("HHmmss")))
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@mapNotNull null
                        if (bytes.size > MAX_BYTES) {
                            withContext(Dispatchers.Main) { toast("$name 太大（>20MB），已跳过") }
                            return@mapNotNull null
                        }
                        name to TextDecoder.decode(bytes)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { toast("读取失败：${e.message}") }
                        null
                    }
                }
            }
            if (files.isNotEmpty()) sendTexts(files)
        }
    }

    private fun sendTexts(files: List<Pair<String, String>>) {
        lifecycleScope.launch {
            busy = true
            try {
                val nodes: List<Node> = sender.watchNodesWithApp()
                if (nodes.isEmpty()) {
                    toast("没有找到安装了「手表TXT」的手表")
                    files.forEach { (name, text) ->
                        SentLog.items.add(0, SentItem(name, text.length, "失败：未找到手表", stamp("HH:mm:ss")))
                    }
                    refreshNodes()
                    return@launch
                }
                for ((name, text) in files) {
                    toast("正在发送：$name")
                    val results = nodes.map { node -> node to sender.send(node, name, text) }
                    val status = results.joinToString("；") { (node, r) ->
                        val label = when (r) {
                            SendResult.DELIVERED -> "已送达"
                            SendResult.SENT_UNCONFIRMED -> "已发送（未收到确认）"
                            SendResult.FAILED -> "失败"
                        }
                        if (nodes.size > 1) "${node.displayName} $label" else label
                    }
                    SentLog.items.add(0, SentItem(name, text.length, status, stamp("HH:mm:ss")))
                    toast("$name：$status")
                }
            } finally {
                busy = false
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun ensureTxt(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().ifEmpty { "未命名" }
        return if (clean.lowercase(Locale.ROOT).endsWith(".txt")) clean else "$clean.txt"
    }

    private fun stamp(pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024
    }
}

@Composable
private fun PhoneScreen(
    nodeLabel: String,
    busy: Boolean,
    items: List<SentItem>,
    onPick: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("手表TXT", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(nodeLabel, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRefresh) { Text("刷新") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPick, enabled = !busy) {
                Text("选择 TXT 文件发送到手表")
            }
            if (busy) {
                Spacer(Modifier.padding(8.dp))
                CircularProgressIndicator()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "也可以在「我的文件」里长按 txt → 分享 → 手表TXT",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Text("发送记录", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("还没有发送过文件", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(items) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${item.time} · ${item.chars} 字 · ${item.status}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
