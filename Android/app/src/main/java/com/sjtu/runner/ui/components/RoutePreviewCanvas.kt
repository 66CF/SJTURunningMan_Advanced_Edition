package com.sjtu.runner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sjtu.runner.data.DataGenerator
import com.sjtu.runner.data.TrackSample
import com.sjtu.runner.ui.theme.LocalAppColors
import com.sjtu.runner.utils.GpsUtil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** 按所选路线坐标绘制等比轮廓，自动缩放，跳段高亮。 */
@Composable
fun RoutePreviewCanvas(
    samples: List<TrackSample>,
    modifier: Modifier = Modifier,
    jumpCount: Int = 0
) {
    val primary = MaterialTheme.colorScheme.primary
    val startColor = LocalAppColors.current.success
    val endColor = MaterialTheme.colorScheme.tertiary
    val jumpColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (samples.size < 2) {
            Text(
                "暂无可预览路线",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lons = samples.map { it.longitude }
                val lats = samples.map { it.latitude }
                val minLon = lons.min(); val maxLon = lons.max()
                val minLat = lats.min(); val maxLat = lats.max()

                val spanLon = max(maxLon - minLon, 1e-6)
                val spanLat = max(maxLat - minLat, 1e-6)

                // 纬度方向按 cos(lat) 压缩，保证形状比例正确
                val latScale = cos(Math.toRadians((minLat + maxLat) / 2.0))
                val availableW = size.width - 24.dp.toPx()
                val availableH = size.height - 24.dp.toPx()
                val scale = min(availableW / spanLon, availableH / (spanLat * latScale))

                val drawnW = spanLon * scale
                val drawnH = spanLat * latScale * scale
                val offsetX = (size.width - drawnW) / 2f
                val offsetY = (size.height - drawnH) / 2f

                fun toOffset(sample: TrackSample): Offset {
                    val x = offsetX + (sample.longitude - minLon) * scale
                    val y = offsetY + (maxLat - sample.latitude) * scale * latScale
                    return Offset(x.toFloat(), y.toFloat())
                }

                val path = Path().apply {
                    val first = toOffset(samples.first())
                    moveTo(first.x, first.y)
                    samples.drop(1).forEach { sample ->
                        val point = toOffset(sample)
                        lineTo(point.x, point.y)
                    }
                }

                drawPath(
                    path = path,
                    color = primary.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(14f, 6f),
                            0f
                        )
                    )
                )

                // 跳段高亮
                if (jumpCount > 0) {
                    for (i in 0 until samples.size - 1) {
                        val a = samples[i]; val b = samples[i + 1]
                        val dist = GpsUtil.haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
                        if (dist > DataGenerator.JUMP_THRESHOLD_M) {
                            drawLine(
                                color = jumpColor.copy(alpha = 0.9f),
                                start = toOffset(a),
                                end = toOffset(b),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                val start = toOffset(samples.first())
                val end = toOffset(samples.last())
                drawCircle(color = gridColor, radius = 9.dp.toPx(), center = start, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = gridColor, radius = 9.dp.toPx(), center = end, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = startColor, radius = 6.dp.toPx(), center = start)
                drawCircle(color = endColor, radius = 6.dp.toPx(), center = end)
            }
        }
    }
}
