package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.prediction.Tone
import com.example.ui.theme.Homey
import com.example.ui.theme.Motion

val CardShape = RoundedCornerShape(18.dp)

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Homey.colors.surface)
            .animateContentSize(Motion.state())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
fun toneInk(tone: Tone): Color = when (tone) {
    Tone.URGENT -> Homey.colors.urgent
    Tone.WARNING -> Homey.colors.warning
    Tone.OK -> Homey.colors.green
}

@Composable
fun toneSoft(tone: Tone): Color = when (tone) {
    Tone.URGENT -> Homey.colors.urgentSoft
    Tone.WARNING -> Homey.colors.warningSoft
    Tone.OK -> Homey.colors.greenSoft
}

@Composable
fun toneDot(tone: Tone): Color = when (tone) {
    Tone.URGENT -> Homey.colors.urgentDot
    Tone.WARNING -> Homey.colors.warningDot
    Tone.OK -> Homey.colors.okDot
}

@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(CircleShape)
            .background(toneSoft(tone))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = toneInk(tone),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun ToneDot(tone: Tone, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(toneDot(tone)))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Homey.colors.ink)
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = Homey.colors.muted)
    }
}

/**
 * 加减步进器，中间的数字可以直接点开输入（只接受整数）。
 * - [commitWhileTyping] = true：边输入边生效（表单里用）；
 *   false：点键盘「完成」或离开输入框时才生效（直接改库存时用，避免每敲一个数字就写一次数据库）。
 * - [onStep] 不为空时，点 +/- 调用它（传入 ±step），用于"在最新库存上加减"；否则按当前值计算新值。
 * 按钮 44dp，满足触控尺寸。
 */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    unit: String,
    label: String,
    step: Int = 1,
    min: Int = 0,
    max: Int = 99_999,
    commitWhileTyping: Boolean = true,
    onStep: ((Int) -> Unit)? = null
) {
    val c = Homey.colors
    val focusManager = LocalFocusManager.current
    var field by remember { mutableStateOf(TextFieldValue(value.toString(), TextRange(value.toString().length))) }
    var focused by remember { mutableStateOf(false) }
    var selectAllPending by remember { mutableStateOf(false) }
    val maxDigits = max.toString().length

    // 外部数值变化（点了 +/-、数据库刷新）时同步到输入框；正在输入的内容若数值相同则不打断
    LaunchedEffect(value) {
        if (field.text.toIntOrNull() != value) {
            val t = value.toString()
            field = TextFieldValue(t, TextRange(t.length))
        }
    }

    fun commit() {
        val v = field.text.toIntOrNull()?.coerceIn(min, max) ?: value
        val t = v.toString()
        field = TextFieldValue(t, TextRange(t.length))
        if (v != value) onValueChange(v)
    }

    fun stepBy(delta: Int) {
        val base = field.text.toIntOrNull()?.coerceIn(min, max) ?: value
        if (focused) focusManager.clearFocus() // 失焦时会先提交正在输入的数字
        if (onStep != null) {
            onStep(delta)
        } else {
            val next = (base + delta).coerceIn(min, max)
            val t = next.toString()
            field = TextFieldValue(t, TextRange(t.length))
            if (next != value) onValueChange(next)
        }
    }

    val borderColor by animateColorAsState(if (focused) c.green else c.line, Motion.state(), label = "stepperBorder")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(
            onClick = { stepBy(-step) },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = c.stepperBg, contentColor = c.ink)
        ) { Icon(Icons.Filled.Remove, contentDescription = "$label 减少 $step") }
        Row(
            modifier = Modifier
                .height(44.dp)
                .widthIn(min = 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
        ) {
            BasicTextField(
                value = field,
                onValueChange = { new ->
                    if (selectAllPending && new.text == field.text) {
                        // 点进输入框时保持全选，直接输入就能替换原来的数字
                        selectAllPending = false
                    } else {
                        selectAllPending = false
                        val digits = new.text.filter { it.isDigit() }.take(maxDigits)
                        field = new.copy(text = digits, selection = TextRange(minOf(new.selection.end, digits.length)))
                        if (commitWhileTyping) {
                            digits.toIntOrNull()?.coerceIn(min, max)?.let { v -> if (v != value) onValueChange(v) }
                        }
                    }
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(c.green),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 14.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused && !focused) {
                            field = field.copy(selection = TextRange(0, field.text.length))
                            selectAllPending = true
                        }
                        if (focused && !state.isFocused) commit()
                        focused = state.isFocused
                    }
                    .semantics { contentDescription = label }
            )
            if (unit.isNotBlank()) Text(unit, fontSize = 13.sp, color = c.muted)
        }
        IconButton(
            onClick = { stepBy(step) },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = c.stepperBg, contentColor = c.ink)
        ) { Icon(Icons.Filled.Add, contentDescription = "$label 增加 $step") }
    }
}

/** 设置行：左边标题（可带说明），右边内容。 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, color = Homey.colors.ink)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Homey.colors.muted)
            }
            trailing()
        }
        if (showDivider) HorizontalDivider(color = Homey.colors.line)
    }
}

/** 可选择的卡片/胶囊（记录方式、余量档位、恢复方式等）。 */
@Composable
fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Homey.colors
    val bg by animateColorAsState(if (selected) c.greenSoft else c.surface, Motion.state(), label = "tileBg")
    val stroke by animateColorAsState(if (selected) c.green else c.line, Motion.state(), label = "tileBorder")
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(2.dp, stroke, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        content = content
    )
}

@Composable
fun ChipButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Homey.colors
    val shape = CircleShape
    val bg by animateColorAsState(if (selected) c.green else c.surface, Motion.state(), label = "chipBg")
    val stroke by animateColorAsState(if (selected) c.green else c.line, Motion.state(), label = "chipBorder")
    val ink by animateColorAsState(if (selected) c.onGreen else c.ink, Motion.state(), label = "chipInk")
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, stroke, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ink)
    }
}
