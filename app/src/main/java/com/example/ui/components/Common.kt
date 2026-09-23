package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.prediction.Tone
import com.example.ui.theme.Homey

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

/** 加减步进器，按钮 44dp，满足触控尺寸。 */
@Composable
fun Stepper(
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String = "减少",
    plusLabel: String = "增加"
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(
            onClick = onMinus,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Homey.colors.stepperBg, contentColor = Homey.colors.ink)
        ) { Icon(Icons.Filled.Remove, contentDescription = minusLabel) }
        Text(
            valueText,
            modifier = Modifier.widthIn(min = 64.dp),
            textAlign = TextAlign.Center,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Homey.colors.ink
        )
        IconButton(
            onClick = onPlus,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Homey.colors.stepperBg, contentColor = Homey.colors.ink)
        ) { Icon(Icons.Filled.Add, contentDescription = plusLabel) }
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
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) c.greenSoft else c.surface)
            .border(2.dp, if (selected) c.green else c.line, shape)
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
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .background(if (selected) c.green else c.surface)
            .border(1.dp, if (selected) c.green else c.line, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) c.onGreen else c.ink)
    }
}
