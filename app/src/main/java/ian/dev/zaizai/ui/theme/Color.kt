package ian.dev.zaizai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 与 Design 原型一致的配色。状态不只靠颜色区分（还有图标和文字），
 * 但待办 / 已完成 / 跳过三种颜色在明度上也拉开，色弱也能分清。
 */
@Immutable
data class ZaiZaiColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    /** 主操作（确认已喂）。 */
    val action: Color,
    val onAction: Color,
    /** 待登记的标签。 */
    val pending: Color,
    val pendingSoft: Color,
    /** 已登记。 */
    val done: Color,
    val doneSoft: Color,
    /** 已跳过。 */
    val skipped: Color,
    val skippedSoft: Color,
    /** 离线提示条。 */
    val offline: Color,
    val onOffline: Color
)

val LightZaiZaiColors = ZaiZaiColors(
    background = Color(0xFFF3F5F0),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1C2420),
    muted = Color(0xFF55605A),
    line = Color(0xFFDDE3DC),
    action = Color(0xFFC2410C),
    onAction = Color(0xFFFFFFFF),
    pending = Color(0xFF9A3412),
    pendingSoft = Color(0xFFFDEBDC),
    done = Color(0xFF134A40),
    doneSoft = Color(0xFFDCEFE9),
    skipped = Color(0xFF3F4650),
    skippedSoft = Color(0xFFECEEF0),
    offline = Color(0xFF3F3A2E),
    onOffline = Color(0xFFFFFFFF)
)

val DarkZaiZaiColors = ZaiZaiColors(
    background = Color(0xFF131815),
    surface = Color(0xFF1C221F),
    ink = Color(0xFFE7ECE8),
    muted = Color(0xFFA9B3AD),
    line = Color(0xFF2E3632),
    action = Color(0xFFFF9A62),
    onAction = Color(0xFF3B1300),
    pending = Color(0xFFFFB68C),
    pendingSoft = Color(0xFF45230F),
    done = Color(0xFF8AD2BF),
    doneSoft = Color(0xFF173A31),
    skipped = Color(0xFFC3C8CE),
    skippedSoft = Color(0xFF2C3136),
    offline = Color(0xFFE8E2D2),
    onOffline = Color(0xFF2A261D)
)
