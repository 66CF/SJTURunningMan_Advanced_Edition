package com.sjtu.runner.data

import com.sjtu.runner.network.PointRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataGeneratorTest {

    private val defaultRoute = listOf(
        121.43680706489432 to 31.027665038002322,
        121.43692833613818 to 31.027541257648274,
        121.43703613279938 to 31.02741360898802,
        121.43713494640546 to 31.027266619407648,
        121.43716189557077 to 31.02704226644857,
        121.43719333626362 to 31.026903012619336,
        121.43727418375951 to 31.02665158157359,
        121.43732359056257 to 31.02648138171604,
        121.43723376001157 to 31.026373072555867,
        121.43709003112997 to 31.026222213161212,
        121.43700469210653 to 31.02611390370344,
        121.43703164127183 to 31.025966912097502,
        121.43704960738202 to 31.025738687834345,
        121.43705409890957 to 31.025649718904198,
        121.43709452265752 to 31.025514331240768,
        121.43711698029527 to 31.025421493873534,
        121.43712147182282 to 31.025274501188534,
        121.43714392946058 to 31.025119771798952,
        121.43714842098812 to 31.025019197559615,
        121.43696875988613 to 31.02494183268714,
        121.43675765809128 to 31.024910886720395,
        121.43645672574544 to 31.024794839254636,
        121.43629503075364 to 31.024752288481416,
        121.436012064518 to 31.02543309854944,
        121.43545960662937 to 31.026473645351572,
        121.43508680984272 to 31.027104156973348,
        121.43533833538551 to 31.027274355707064,
        121.43567969147931 to 31.027421345275375,
        121.4359581661874 to 31.027541257648274,
        121.4361198611792 to 31.02756446647703,
        121.43633545450159 to 31.02764569733272,
        121.43654206476889 to 31.027649565466955,
        121.43673070892598 to 31.027665038002322,
        121.43681604794943 to 31.027665038002322
    )

    private val rules = PointRules(id = 9, spmin = 180, spmax = 540)

    private fun run(targetM: Double, truncate: Boolean = true): RunOutcome {
        val logs = mutableListOf<Pair<LogLevel, String>>()
        return DataGenerator.generate(
            coordinates = defaultRoute,
            userId = "test-user",
            targetDistanceM = targetM,
            startTimeMs = 1_700_000_000_000L,
            rules = rules,
            truncateToTarget = truncate,
            log = { level, msg -> logs.add(level to msg) }
        )
    }

    @Test
    fun correctedRouteDistanceMatchesSingleLoop() {
        val distance = DataGenerator.correctedRouteDistance(defaultRoute)
        assertTrue("单圈距离应接近 871m，实际 $distance", distance in 860.0..880.0)
    }

    @Test
    fun truncateModeReachesTargetDistance() {
        val outcome = run(4_000.0)
        assertTrue("实际距离应接近 4000m 目标，实际 ${outcome.distanceM}", outcome.distanceM in 3_900.0..4_400.0)
        assertTrue("轨迹点应足够密集，实际 ${outcome.pointCount}", outcome.pointCount > 150)
        assertEquals(outcome.pointCount, outcome.samples.size)
        assertEquals(0, outcome.jumpCount)
        assertTrue("相邻点不应跳段，最大 ${outcome.maxSegmentM}", outcome.maxSegmentM < DataGenerator.JUMP_THRESHOLD_M)
    }

    @Test
    fun fullRouteModeKeepsWholeLoop() {
        val outcome = run(500.0, truncate = false)
        assertTrue("完整路线应覆盖整圈，实际 ${outcome.distanceM}", outcome.distanceM > 871.0)
    }

    @Test
    fun tooLongRouteIsTruncatedToTarget() {
        val outcome = run(500.0)
        assertTrue("削减后应接近 500m + 200m 补偿，实际 ${outcome.distanceM}", outcome.distanceM in 600.0..800.0)
    }

    @Test
    fun paceIsClampedInsidePointRules() {
        repeat(8) {
            val outcome = run(4_000.0)
            val spAvg = (outcome.payload.first()["spavg"] as Int)
            assertTrue("配速 $spAvg 分钟/公里应在规则区间内", spAvg in 3..9)
        }
    }

    @Test
    fun payloadMatchesUploadSchema() {
        val outcome = run(4_000.0)
        val record = outcome.payload.first()
        assertEquals(9, record["id"])
        assertEquals("test-user", record["userId"])
        assertEquals("0", record["state"])
        assertEquals(0, record["fravg"])
        assertTrue(record["signpoints"] is List<*>)
        assertTrue((record["sid"] as String).isNotBlank())

        @Suppress("UNCHECKED_CAST")
        val tracks = record["tracks"] as List<Map<String, Any>>
        assertTrue("应拆分为多个轨迹段，实际 ${tracks.size}", tracks.size > 1)
        assertEquals(outcome.segmentCount, tracks.size)

        var pointTotal = 0
        tracks.forEach { track ->
            @Suppress("UNCHECKED_CAST")
            val points = track["points"] as List<Map<String, Any>>
            assertEquals(track["counts"], points.size)
            pointTotal += points.size
            assertTrue(track["status"] in listOf("normal", "invalid", "stop"))
            // 末段可能只剩 1 个点，此时时长为 0，与电脑版行为一致
            assertTrue((track["stime"] as Int) <= (track["etime"] as Int))
            assertTrue((track["duration"] as Int) >= 0)
            assertTrue((track["counts"] as Int) > 0)
            assertTrue((track["trid"] as String).isNotBlank())
            points.forEach { point ->
                val location = point["location"] as String
                assertTrue("坐标应保留 7 位小数: $location", Regex("""^-?\d+\.\d{7},-?\d+\.\d{7}$""").matches(location))
                @Suppress("UNCHECKED_CAST")
                val latLng = point["latLng"] as Map<String, Double>
                val latitude = latLng["latitude"] ?: error("缺少 latitude")
                val longitude = latLng["longitude"] ?: error("缺少 longitude")
                assertTrue(latitude in 31.01..31.04)
                assertTrue(longitude in 121.42..121.45)
            }
        }
        assertEquals(outcome.pointCount, pointTotal)
    }
}
