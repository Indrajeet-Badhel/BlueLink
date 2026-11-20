package dev.bluelink.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Original 24-unit line icons keep the app independent of the large icon library. */
object Glyphs {
    private fun glyph(name:String, path:String) = ImageVector.Builder(name,24.dp,24.dp,24f,24f)
        .addPath(addPathNodes(path), fill=null, stroke=SolidColor(Color.White), strokeLineWidth=1.8f,
            strokeLineCap=StrokeCap.Round, strokeLineJoin=StrokeJoin.Round).build()
    val Bluetooth = glyph("Bluetooth", "M7,6 L17,16 L12,21 L12,3 L17,8 L7,18")
    val Pause = glyph("Pause", "M8,5 L8,19 M16,5 L16,19")
    val AutoAwesome = glyph("Sparkles", "M12,3 L14,9 L20,11 L14,13 L12,20 L10,13 L4,11 L10,9 Z M20,2 L20,6 M18,4 L22,4")
    val ChatBubbleOutline = glyph("Chat", "M4,4 L20,4 L20,16 L10,16 L4,21 Z M8,8 L16,8 M8,12 L14,12")
    val Science = glyph("Science", "M9,3 L15,3 M10,3 L10,10 L4,19 Q3,21 6,21 L18,21 Q21,21 20,19 L14,10 L14,3 M7,15 L17,15")
    val Terminal = glyph("Terminal", "M3,4 L21,4 L21,20 L3,20 Z M7,9 L10,12 L7,15 M13,15 L17,15")
    val Close = glyph("Close", "M6,6 L18,18 M18,6 L6,18")
    val Sensors = glyph("Broadcast", "M11,11 L13,11 L13,13 L11,13 Z M8,8 Q4,12 8,16 M16,8 Q20,12 16,16 M5,4 Q-2,12 5,20 M19,4 Q26,12 19,20")
    val Radar = glyph("Radar", "M12,2 A10,10 0,1 0,22,12 M12,6 A6,6 0,1 0,18,12 M12,12 L21,3 M12,2 L12,12 L22,12")
    val Refresh = glyph("Refresh", "M20,10 A8,8 0,1 0,18,18 M20,4 L20,10 L14,10")
    val PhoneAndroid = glyph("Phone", "M7,2 L17,2 Q19,2 19,4 L19,20 Q19,22 17,22 L7,22 Q5,22 5,20 L5,4 Q5,2 7,2 Z M10,18 L14,18")
    val ArrowOutward = glyph("Connect", "M5,19 L19,5 M7,5 L19,5 L19,17")
    val Visibility = glyph("Visible", "M2,12 Q12,-2 22,12 Q12,26 2,12 Z M15,12 A3,3 0,1 0,9,12 A3,3 0,1 0,15,12")
    val DeleteOutline = glyph("Delete", "M4,6 L20,6 M9,3 L15,3 M6,6 L7,21 L17,21 L18,6 M10,10 L10,17 M14,10 L14,17")
    val ArrowUpward = glyph("Send", "M12,21 L12,3 M5,10 L12,3 L19,10")
    val Bolt = glyph("Ping", "M14,2 L4,14 L11,14 L10,22 L20,10 L13,10 Z")
}
