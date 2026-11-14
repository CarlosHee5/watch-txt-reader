package com.carloshee.watchtxt.wear

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WatchTxtApp() }
    }
}

@Composable
fun WatchTxtApp() {
    MaterialTheme {
        val nav = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(navController = nav, startDestination = "list") {
            composable("list") {
                FileListScreen(
                    onOpen = { name -> nav.navigate("read/" + Uri.encode(name)) },
                    onDelete = { name -> nav.navigate("delete/" + Uri.encode(name)) }
                )
            }
            // Navigation decodes path arguments itself, so no Uri.decode() here.
            composable("read/{name}") { entry ->
                ReaderScreen(name = entry.arguments?.getString("name").orEmpty())
            }
            composable("delete/{name}") { entry ->
                ConfirmDeleteScreen(
                    name = entry.arguments?.getString("name").orEmpty(),
                    onDone = { nav.popBackStack() }
                )
            }
        }
    }
}

private fun sizeLabel(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024f / 1024f)
}

@Composable
fun FileListScreen(onOpen: (String) -> Unit, onDelete: (String) -> Unit) {
    val context = LocalContext.current
    val version by TxtStore.version.collectAsState()
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(version) {
        files = withContext(Dispatchers.IO) { TxtStore.list(context) }
    }
    var deleteMode by remember { mutableStateOf(false) }
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryScroll(listState),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0)
        ) {
            item {
                ListHeader {
                    Text(if (deleteMode) "点选要删除的文件" else "手表TXT")
                }
            }
            if (files.isEmpty()) {
                item {
                    Text(
                        "还没有文件\n请在手机上选择或分享 TXT",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            items(files, key = { it.name }) { f ->
                Chip(
                    onClick = { if (deleteMode) onDelete(f.name) else onOpen(f.name) },
                    label = {
                        Text(
                            f.name.removeSuffix(".txt"),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(if (deleteMode) "点按删除" else sizeLabel(f.length()))
                    },
                    colors = if (deleteMode) ChipDefaults.secondaryChipColors()
                    else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (files.isNotEmpty()) {
                item {
                    CompactChip(
                        onClick = { deleteMode = !deleteMode },
                        label = { Text(if (deleteMode) "完成" else "删除文件…") },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderScreen(name: String) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(TxtStore.READER_PREFS, Context.MODE_PRIVATE)
    }
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat(TxtStore.KEY_FONT, 16f)) }
    var paragraphs by remember { mutableStateOf<List<String>?>(null) }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    fun changeFont(delta: Float) {
        fontSize = (fontSize + delta).coerceIn(10f, 32f)
        prefs.edit().putFloat(TxtStore.KEY_FONT, fontSize).apply()
    }

    LaunchedEffect(name) {
        paragraphs = withContext(Dispatchers.IO) { TxtStore.readParagraphs(context, name) }
        // Restore reading position (index in the list).
        val saved = prefs.getInt(TxtStore.positionKey(name), 0)
        if (saved > 0) listState.scrollToItem(saved)
    }

    DisposableEffect(name) {
        onDispose {
            if (paragraphs != null) {
                prefs.edit().putInt(TxtStore.positionKey(name), listState.centerItemIndex).apply()
            }
        }
    }

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .rotaryScroll(listState),
            state = listState,
            // No shrinking/fading at the edges: easier to read long text.
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
            autoCentering = AutoCenteringParams(itemIndex = 0),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    name.removeSuffix(".txt"),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { FontControls(fontSize, ::changeFont) }

            val list = paragraphs
            if (list == null) {
                item { Text("加载中…") }
            } else if (list.isEmpty()) {
                item { Text("（空文件）") }
            } else {
                items(list) { p ->
                    Text(
                        text = p,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.45f).sp,
                        color = MaterialTheme.colors.onBackground,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { FontControls(fontSize, ::changeFont) }
                item {
                    Text(
                        "— 完 —",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun FontControls(fontSize: Float, onChange: (Float) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        CompactChip(
            onClick = { onChange(-2f) },
            label = { Text("A−") },
            colors = ChipDefaults.secondaryChipColors()
        )
        Spacer(Modifier.width(6.dp))
        Text("${fontSize.toInt()}", fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        CompactChip(
            onClick = { onChange(2f) },
            label = { Text("A+") },
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

@Composable
fun ConfirmDeleteScreen(name: String, onDone: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "删除「${name.removeSuffix(".txt")}」？",
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onDone,
                colors = ButtonDefaults.secondaryButtonColors()
            ) { Text("取消") }
            Button(
                onClick = {
                    TxtStore.delete(context, name)
                    onDone()
                }
            ) { Text("删除") }
        }
    }
}
