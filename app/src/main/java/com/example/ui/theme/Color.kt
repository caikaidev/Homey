package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 与 Design 画布一致的配色。 */
@Immutable
data class HomeyColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val green: Color,
    val onGreen: Color,
    val greenSoft: Color,
    val coral: Color,
    val onCoral: Color,
    val urgent: Color,
    val urgentSoft: Color,
    val urgentDot: Color,
    val warning: Color,
    val warningSoft: Color,
    val warningDot: Color,
    val okDot: Color,
    val checkboxBorder: Color,
    val stepperBg: Color
)

val LightHomeyColors = HomeyColors(
    background = Color(0xFFF7F4EE),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEDE8DF),
    ink = Color(0xFF1C1E1D),
    muted = Color(0xFF5B615E),
    line = Color(0xFFE6E1D8),
    green = Color(0xFF1E5B4B),
    onGreen = Color(0xFFFFFFFF),
    greenSoft = Color(0xFFE3EFEA),
    coral = Color(0xFFC2562F),
    onCoral = Color(0xFFFFFFFF),
    urgent = Color(0xFFA3261D),
    urgentSoft = Color(0xFFFBE3E0),
    urgentDot = Color(0xFFD1493D),
    warning = Color(0xFF8A4B05),
    warningSoft = Color(0xFFFBEFD6),
    warningDot = Color(0xFFD99019),
    okDot = Color(0xFF4E9A7F),
    checkboxBorder = Color(0xFFB9B3A8),
    stepperBg = Color(0xFFEFEAE1)
)

val DarkHomeyColors = HomeyColors(
    background = Color(0xFF141615),
    surface = Color(0xFF1D201E),
    surfaceMuted = Color(0xFF262A28),
    ink = Color(0xFFE6E8E5),
    muted = Color(0xFFA9B0AC),
    line = Color(0xFF2F3431),
    green = Color(0xFF8AD2BF),
    onGreen = Color(0xFF00382E),
    greenSoft = Color(0xFF173A31),
    coral = Color(0xFFFFB59D),
    onCoral = Color(0xFF5A1C06),
    urgent = Color(0xFFFFB4AB),
    urgentSoft = Color(0xFF4A1F1B),
    urgentDot = Color(0xFFE5675C),
    warning = Color(0xFFF5C27A),
    warningSoft = Color(0xFF3F2E12),
    warningDot = Color(0xFFE0A33A),
    okDot = Color(0xFF6DB89C),
    checkboxBorder = Color(0xFF5E6561),
    stepperBg = Color(0xFF2B302D)
)
