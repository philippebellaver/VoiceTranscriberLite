package com.example.voicetranscriberlite.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Composable que desenha a onda sonora da gravação
 *
 * @param amplitudes Lista de amplitudes recentes da gravação
 * @param modifier Modifier para definir tamanho e layout
 * @param barWidth Largura de cada barra da waveform
 * @param spacing Espaço entre barras
 * @param color Cor da barra
 */
@Composable
fun AudioWaveformCanvas(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(150.dp),
    barWidth: Dp = 6.dp,
    spacing: Dp = 4.dp,
    color: Color = Color.Green
) {
    // Calcula média móvel simples para suavizar
    val smoothedAmps = amplitudes.mapIndexed { index, amp ->
        val prev = if (index > 0) amplitudes[index - 1] else amp
        (amp + prev) / 2f
    }

    Canvas(modifier = modifier) {
        drawWaveform(smoothedAmps, barWidth.toPx(), spacing.toPx(), color)
    }
}

private fun DrawScope.drawWaveform(
    amplitudes: List<Float>,
    barWidth: Float,
    spacing: Float,
    color: Color
) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    val maxBars = (canvasWidth / (barWidth + spacing)).toInt()
    val displayedAmps = if (amplitudes.size > maxBars) amplitudes.takeLast(maxBars) else amplitudes

    // Escala dinâmica baseado na maior amplitude visível
    val maxAmp = (displayedAmps.maxOrNull() ?: 1f).coerceAtLeast(1f)

    var x = 0f
    for (amp in displayedAmps) {
        val normalized = amp / maxAmp
        val barHeight = normalized * canvasHeight
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(x, canvasHeight - barHeight),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2) // proporção do barWidth
        )
        x += barWidth + spacing
    }
}
