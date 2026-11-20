package dev.bluelink.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluelink.*
import java.util.Locale

internal val Plum = Color(0xFF190B29)
internal val Lime = Color(0xFFB6FF00)
internal val Green = Color(0xFF78FF00)
internal val Purple = Color(0xFF7200FF)
internal val Yellow = Color(0xFFE5FF00)
internal val Paper = Color(0xFFF5F0FF)
internal val Muted = Color(0xFFC7B6D9)
internal val Panel = Color(0xFF2A153D)
internal val Line = Color(0xFF55316C)
internal val Pink = Color(0xFFFF9DD8)
internal val Chunky = RoundedCornerShape(topStart = 26.dp, topEnd = 12.dp, bottomEnd = 28.dp, bottomStart = 12.dp)

@Composable
fun BlueLinkApp(state: AppState, model: BlueLinkModel, action: (RadioAction, String?) -> Unit,
    onPickDocument:()->Unit, onAcceptDocument:(String,String)->Unit) {
    var calm by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); model.clearNotice() }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Plum, secondary = Purple,
        background = Plum, surface = Panel, onSurface = Paper, onBackground = Paper, outline = Line)) {
        Scaffold(containerColor = Plum, snackbarHost = { SnackbarHost(snackbar) },
            topBar = { BrandBar(calm) { calm = !calm } },
            bottomBar = { Navigation(state.page, model::select) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AcidBackdrop()
                if (calm) Page(state, model, action, onPickDocument,onAcceptDocument)
                else AnimatedContent(state.page, label = "page") { destination ->
                    Page(state.copy(page = destination), model, action,onPickDocument,onAcceptDocument)
                }
            }
        }
    }
}

@Composable
private fun Page(state: AppState, model: BlueLinkModel, action: (RadioAction, String?) -> Unit, onPickDocument:()->Unit,onAcceptDocument:(String,String)->Unit) {
    when (state.page) {
        Destination.LINK -> LinkScreen(state, model, action)
        Destination.CHAT -> ChatScreen(state, model,onPickDocument,onAcceptDocument)
        Destination.LAB -> LabScreen(state, model)
        Destination.LOG -> LogScreen(state, model)
        Destination.SOS -> SosScreen(model,action)
        Destination.ARCADE -> ArcadeScreen(model)
    }
}

@Composable
private fun BrandBar(calm: Boolean, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Glyphs.Bluetooth, "BlueLink", tint = Lime, modifier = Modifier.size(28.dp))
        Text("blue", color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("link", color = Lime, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = toggle, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(if (calm) Glyphs.Pause else Glyphs.AutoAwesome, null, Modifier.size(16.dp), tint = Yellow)
            Spacer(Modifier.width(6.dp))
            Text(if (calm) "CALM ON" else "CALM OFF", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Navigation(current: Destination, select: (Destination) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Plum).navigationBarsPadding()
        .padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Destination.LINK,Destination.CHAT,Destination.LAB,Destination.LOG).forEach { page ->
            val active = current == page
            val icon = when(page) { Destination.LINK -> Glyphs.Bluetooth; Destination.CHAT -> Glyphs.ChatBubbleOutline
                Destination.LAB -> Glyphs.Science; else -> Glyphs.Terminal }
            Surface(onClick = { select(page) }, modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                color = if (active) Lime else Panel, shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (active) Lime else Line)) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = if (active) Plum else Muted, modifier = Modifier.size(22.dp))
                    Text(page.title, color = if (active) Plum else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AcidBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 26.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) { drawCircle(Line.copy(alpha = .35f), 1.dp.toPx(), Offset(x,y)); x += step }
            y += step
        }
        drawCircle(Purple.copy(alpha = .10f), size.width * .6f, Offset(size.width, size.height * .5f))
    }
}

@Composable
private fun LinkScreen(state: AppState, model: BlueLinkModel, action: (RadioAction, String?) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Tag("OFFLINE BY NATURE", Yellow)
            Text("BLP / 1.0", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp, 12.dp, 48.dp, 16.dp))
            .background(Brush.linearGradient(listOf(Lime, Green))).padding(22.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text("LESS SCROLL.\nMORE SLIME.", color = Plum, fontSize = 37.sp, lineHeight = 36.sp,
                    fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (-1.6).sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Spacer(Modifier.height(15.dp))
                        Text("Your people. Your orbit.\nNo internet required.", color = Plum, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(17.dp))
                        Box(Modifier.rotate(-4f).background(Plum, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text("GET IN THE OOZE", color = Yellow, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    SlimeMascot(Modifier.size(130.dp).rotate(7f))
                }
            }
        }
        StatusPanel(state)
        LinkExtras(state,model,action)
        if (state.connected || state.busy || state.group!=null) {
            AcidButton(if (state.connected) "Disconnect" else "Cancel connection", Glyphs.Close, Yellow, model::disconnect)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("HOST A LINK", "Phone A starts here", Glyphs.Sensors, Purple, Modifier.weight(1f), state.supported) { action(RadioAction.HOST, null) }
                ActionTile("FIND A PEER", "Phone B scans here", Glyphs.Radar, Lime, Modifier.weight(1f), state.supported) { action(RadioAction.SCAN, null) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("THE NEARBY UNIVERSE", Modifier.weight(1f))
            if (state.scanning) TextButton(onClick = model::cancelDiscovery) { Text("STOP", color = Yellow) }
            else IconButton(onClick = { model.refresh() }) { Icon(Glyphs.Refresh, "Refresh paired devices", tint = Muted) }
        }
        if (state.scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)), color = Lime, trackColor = Panel)
        if (state.peers.isEmpty()) {
            Column(Modifier.fillMaxWidth().border(1.dp, Line, Chunky).background(Panel, Chunky).padding(22.dp)) {
                Text("A LITTLE QUIET IN HERE.", color = Paper, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (state.scanning) "Listening for nearby phones. Make the host discoverable." else "Start server on one phone, make it visible, then scan from the other.", color = Muted, fontSize = 13.sp)
            }
        } else state.peers.forEach { peer ->
            Row(Modifier.fillMaxWidth().clip(Chunky).background(Panel).border(1.dp, Line, Chunky)
                .clickable(enabled = !state.busy && !state.connected) { action(RadioAction.CONNECT, peer.address) }
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Purple, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Glyphs.PhoneAndroid, null, tint = Paper)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(peer.name, fontWeight = FontWeight.Bold, color = Paper)
                    Text("${if (peer.paired) "PAIRED" else "DISCOVERED"} · ${peer.address.takeLast(5)}", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                Icon(Glyphs.ArrowOutward, "Connect to ${peer.name}", tint = Lime)
            }
        }
        OutlinedButton(onClick = { action(RadioAction.DISCOVER, null) }, enabled = state.supported && !state.connected,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = Chunky, border = BorderStroke(1.dp, Purple)) {
            Icon(Glyphs.Visibility, null, tint = Lime); Spacer(Modifier.width(8.dp)); Text("Make this phone discoverable", color = Paper)
        }
        Text("PAIRING ≠ CONNECTED\nKeep BlueLink open on both phones. Allow Android pairing when prompted.", color = Muted,
            fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun StatusPanel(state: AppState) {
    Column(Modifier.fillMaxWidth().background(Panel, Chunky).border(1.dp, if (state.connected) Lime else Line, Chunky).padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (state.connected) Green else Yellow, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
            Text(if (!state.supported) "Bluetooth unavailable" else state.status, color = Paper, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (state.enabled) "BT ON" else "BT OFF", color = Lime, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        Text(state.detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text("THIS PHONE / ${state.localName}", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ChatScreen(state: AppState, model: BlueLinkModel,onPickDocument:()->Unit,onAcceptDocument:(String,String)->Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size,state.polls.size,state.transfers.size) {
        val count=state.messages.size+state.polls.size+state.transfers.size
        if(count>0)list.animateScrollToItem(count-1)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("THE OOZE ROOM", color = Lime, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                Text(if (state.connected) state.peerName else "Waiting for your next connection", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = { confirmClear = true }) { Icon(Glyphs.DeleteOutline, "Clear conversation", tint = Muted) }
        }
        ChatTools(state,model,onPickDocument)
        if (!state.connected) {
            Surface(color = Panel, shape = Chunky, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(state.detail, color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { model.select(Destination.LINK) }) { Text("Go to Link", color = Lime) }
                }
            }
        }
        if (state.messages.isEmpty() && state.polls.isEmpty() && state.transfers.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                SlimeMascot(Modifier.size(150.dp))
                Text("WORDS WITH NO WI-FI.", color = Paper, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(if (state.connected) "Your first message starts the ooze." else "Connect two phones to start chatting.", color = Muted, fontSize = 12.sp)
            }
        } else LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.polls,key={"poll-${it.record["id"]}"}) { PollCard(it,model) }
            items(state.transfers,key={"file-${it.id}"}) { TransferCard(it,model,onAcceptDocument) }
            items(state.messages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                    if(state.group!=null)Text(message.sender, color=Muted, fontSize=10.sp)
                    Box(Modifier.widthIn(max = 310.dp).clip(if (message.mine) RoundedCornerShape(22.dp, 22.dp, 4.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 4.dp))
                        .background(if (message.mine) Lime else Purple).padding(15.dp)) {
                        Text(message.text, color = if (message.mine) Plum else Paper, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                    Text("${message.time} · ${message.status}", color = if (message.status == "Failed") Pink else Muted,
                        fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(value = text, onValueChange = { if (it.length <= state.maxPayload) text = it },
                placeholder = { Text("Spill something…", color = Muted) }, modifier = Modifier.weight(1f),
                enabled = state.connected, maxLines = 4, shape = RoundedCornerShape(22.dp),
                textStyle = TextStyle(color = Paper, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (model.send(text)) text = "" }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Lime, unfocusedBorderColor = Line))
            FilledIconButton(onClick = { if (model.send(text)) text = "" }, enabled = state.connected && text.isNotBlank(),
                modifier = Modifier.size(56.dp), shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Plum)) {
                Icon(Glyphs.ArrowUpward, "Send message")
            }
        }
        Text("ACK = APP RECEIPT, NOT READ · ${text.toByteArray().size}/${state.maxPayload} B", color = Muted, fontFamily = FontFamily.Monospace,
            fontSize = 9.sp, modifier = Modifier.padding(bottom = 8.dp))
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear this conversation?") },
        text = { Text("Messages on this phone will be removed. Pending sends stay visible until they finish.") },
        confirmButton = { TextButton(onClick = { model.clearChat(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } })
}

@Composable
private fun LabScreen(state: AppState, model: BlueLinkModel) {
    val m = state.metrics
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Tag("REAL PACKETS. REAL NUMBERS.", Yellow)
        Text("THE MUTATION\nSTATION", color = Paper, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            fontSize = 33.sp, lineHeight = 33.sp)
        StatusPanel(state)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("ROUND TRIP", m.rttMs?.let { "$it ms" } ?: "N/A", Lime, Modifier.weight(1f))
            MetricCard("AVG RTT", m.averageRttMs?.let { "${format(it)} ms" } ?: "N/A", Yellow, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("TX GOODPUT", "${format(m.txBps)} B/s", Paper, Modifier.weight(1f))
            MetricCard("RX GOODPUT", "${format(m.rxBps)} B/s", Paper, Modifier.weight(1f))
        }
        Text("TX ${format(m.txBps / 1000)} kB/s · RX ${format(m.rxBps / 1000)} kB/s\nUnique payload bytes since reset. Headers and retries excluded.", color = Muted, fontSize = 11.sp)
        AcidButton("Ping the other side", Glyphs.Bolt, Purple, model::ping, state.connected)
        SectionLabel("UNDER THE GOO")
        val rows = listOf("Transport" to "Classic RFCOMM", "Protocol" to "BLP 1.x / CRC32",
            "Frames TX / RX" to "${m.framesSent} / ${m.framesReceived}", "DATA TX / RX" to "${m.dataSent} / ${m.dataReceived}",
            "ACK TX / RX" to "${m.ackSent} / ${m.ackReceived}", "Retries" to "${m.retries}", "Duplicates suppressed" to "${m.duplicates}",
            "CRC failures" to "${m.checksumFailures}", "Protocol errors" to "${m.errors}", "Failed messages" to "${m.failed}",
            "Connection duration" to "${m.seconds} s", "Payload limit" to "${state.maxPayload} bytes")
        Column(Modifier.background(Panel, Chunky).border(1.dp, Line, Chunky).padding(17.dp)) {
            rows.forEachIndexed { index, (key,value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(key, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(value, color = Paper, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                if (index < rows.lastIndex) HorizontalDivider(color = Line.copy(alpha = .5f))
            }
        }
        OutlinedButton(onClick = model::resetStats, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), border = BorderStroke(1.dp, Line)) {
            Text("Reset statistics", color = Lime)
        }
        Text("Application RTT includes software and transport delays. CRC32 is error detection, not encryption. No app-level end-to-end encryption is claimed.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun LogScreen(state: AppState, model: BlueLinkModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tag("THE RECEIPTS", Yellow)
        Text("PACKET DIARY", color = Lime, fontSize = 30.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Black)
        Text("Chronological protocol events. Message content stays out of the log.", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = model::clearLogs, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Line)) { Text("Clear", color = Paper) }
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                    "BlueLink 1.0.0 protocol log\n" + state.events.joinToString("\n"))
                context.startActivity(Intent.createChooser(intent, "Export BlueLink log"))
            }, enabled = state.events.isNotEmpty(), modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Line)) { Text("Export", color = Lime) }
        }
        if (state.events.isEmpty()) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Glyphs.Terminal, null, tint = Purple, modifier = Modifier.size(64.dp))
            Text("NO EXPERIMENTS YET.", color = Paper, fontWeight = FontWeight.Bold)
            Text("Connection events appear here.", color = Muted, fontSize = 12.sp)
        } else LazyColumn(Modifier.fillMaxSize().clip(Chunky).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.events.size) { i ->
                Text(state.events[i], color = if (state.events[i].contains("RX")) Lime else if (state.events[i].contains("TX")) Paper else Muted,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun ActionTile(title: String, description: String, icon: ImageVector, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = color, shape = Chunky, modifier = modifier.heightIn(min = 134.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = if (color == Purple) Paper else Plum, modifier = Modifier.size(30.dp))
            Text(title, color = if (color == Purple) Paper else Plum, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(description, color = if (color == Purple) Paper else Plum, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AcidButton(text: String, icon: ImageVector, color: Color, click: () -> Unit, enabled: Boolean = true) {
    Button(onClick = click, enabled = enabled, shape = Chunky, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (color == Purple) Paper else Plum)) {
        Icon(icon, null); Spacer(Modifier.width(9.dp)); Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(Panel, Chunky).border(1.dp, Line, Chunky).padding(16.dp)) {
        Text(label, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(text, color = Plum, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp,
        modifier = Modifier.rotate(-2f).background(color, RoundedCornerShape(5.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp, modifier = modifier)
}

private fun format(value: Double) = String.format(Locale.US, "%.1f", value)

@Composable
private fun SlimeMascot(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Grinning radioactive slime mascot" }) {
        val sx = size.width / 160f; val sy = size.height / 160f
        fun pt(x: Float,y: Float) = Offset(x*sx,y*sy)
        drawOval(Plum.copy(alpha=.18f), pt(15f,137f), Size(140*sx,16*sy))
        val blob = Path().apply {
            moveTo(22*sx,109*sy)
            cubicTo(5*sx,56*sy,32*sx,19*sy,70*sx,23*sy)
            cubicTo(98*sx,3*sy,146*sx,31*sy,139*sx,76*sy)
            cubicTo(137*sx,103*sy,158*sx,123*sy,141*sx,136*sy)
            cubicTo(127*sx,151*sy,115*sx,131*sy,106*sx,138*sy)
            cubicTo(91*sx,163*sy,76*sx,127*sy,63*sx,140*sy)
            cubicTo(47*sx,156*sy,42*sx,132*sy,30*sx,139*sy)
            cubicTo(7*sx,148*sy,17*sx,118*sy,22*sx,109*sy)
            close()
        }
        drawPath(blob, Purple)
        drawPath(blob, Plum, style=Stroke(4*sx))
        drawOval(Paper, pt(44f,49f), Size(29*sx,39*sy))
        drawOval(Paper, pt(86f,43f), Size(31*sx,40*sy))
        drawOval(Plum, pt(58f,60f), Size(12*sx,19*sy))
        drawOval(Plum, pt(86f,54f), Size(12*sx,19*sy))
        val smile=Path().apply { moveTo(47*sx,96*sy); cubicTo(64*sx,113*sy,104*sx,111*sy,118*sx,86*sy); cubicTo(107*sx,124*sy,64*sx,136*sy,47*sx,96*sy); close() }
        drawPath(smile, Plum)
        drawLine(Yellow, pt(66f,109f),pt(98f,107f),5*sx)
        drawCircle(Pink,8*sx,pt(38f,90f)); drawCircle(Pink,7*sx,pt(128f,82f))
        drawOval(Paper.copy(alpha=.35f),pt(32f,38f),Size(12*sx,22*sy))
        drawLine(Plum,pt(122f,21f),pt(136f,5f),3*sx)
        drawLine(Plum,pt(137f,29f),pt(153f,24f),3*sx)
    }
}
