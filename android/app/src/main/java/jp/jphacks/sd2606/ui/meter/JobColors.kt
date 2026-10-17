package jp.jphacks.sd2606.ui.meter

import androidx.compose.ui.graphics.Color

/** バイト先の色。DB には番号だけ保存し、色はここで決める（隣り合っても見分けやすい8色）。 */
val JobPalette = listOf(
    Color(0xFF1E88E5), // 青
    Color(0xFFFB8C00), // オレンジ
    Color(0xFF43A047), // 緑
    Color(0xFFD81B60), // ピンク
    Color(0xFF8E24AA), // 紫
    Color(0xFF00ACC1), // 水色
    Color(0xFF6D4C41), // 茶
    Color(0xFFFFB300), // 黄
)

fun jobColor(colorIndex: Int): Color = JobPalette[colorIndex.mod(JobPalette.size)]
