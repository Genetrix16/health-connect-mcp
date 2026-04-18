package com.healthconnectmcp.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class HealthReader(private val context: Context) {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    companion object {
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class)
        )
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    private fun dayRange(date: LocalDate): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(start, end)
    }

    suspend fun steps(date: LocalDate): JSONObject {
        val req = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = dayRange(date)
        )
        val result = client.aggregate(req)
        val total = result[StepsRecord.COUNT_TOTAL] ?: 0L
        return JSONObject().apply {
            put("date", date.toString())
            put("total", total)
        }
    }

    suspend fun calories(date: LocalDate): JSONObject {
        val req = AggregateRequest(
            metrics = setOf(
                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
            ),
            timeRangeFilter = dayRange(date)
        )
        val result = client.aggregate(req)
        val total = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
        val active = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        return JSONObject().apply {
            put("date", date.toString())
            put("total_kcal", total)
            put("active_kcal", active)
            put("basal_kcal", (total - active).coerceAtLeast(0.0))
        }
    }

    suspend fun distance(date: LocalDate): JSONObject {
        val req = AggregateRequest(
            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
            timeRangeFilter = dayRange(date)
        )
        val result = client.aggregate(req)
        val meters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
        return JSONObject().apply {
            put("date", date.toString())
            put("total_meters", meters)
            put("total_km", meters / 1000.0)
        }
    }

    suspend fun sleep(date: LocalDate): JSONObject {
        val zone = ZoneId.systemDefault()
        val windowStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val windowEnd = date.plusDays(1).atTime(12, 0).atZone(zone).toInstant()

        val req = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd)
        )
        val sessions = client.readRecords(req).records

        val sessionsArr = JSONArray()
        var totalSeconds = 0L
        for (s in sessions) {
            val duration = Duration.between(s.startTime, s.endTime).seconds
            totalSeconds += duration
            sessionsArr.put(JSONObject().apply {
                put("start", s.startTime.toString())
                put("end", s.endTime.toString())
                put("duration_seconds", duration)
                put("duration_hours", duration / 3600.0)
                put("title", s.title ?: "")
            })
        }
        return JSONObject().apply {
            put("date", date.toString())
            put("total_hours", totalSeconds / 3600.0)
            put("session_count", sessions.size)
            put("sessions", sessionsArr)
        }
    }

    suspend fun heartRate(date: LocalDate): JSONObject {
        val req = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = dayRange(date)
        )
        val records = client.readRecords(req).records
        val samples = records.flatMap { it.samples }

        val bpms = samples.map { it.beatsPerMinute }
        return JSONObject().apply {
            put("date", date.toString())
            put("sample_count", bpms.size)
            put("avg_bpm", if (bpms.isNotEmpty()) bpms.average() else 0.0)
            put("min_bpm", bpms.minOrNull() ?: 0)
            put("max_bpm", bpms.maxOrNull() ?: 0)
        }
    }

    suspend fun summary(date: LocalDate): JSONObject {
        return JSONObject().apply {
            put("date", date.toString())
            put("steps", steps(date).optLong("total"))
            put("calories_kcal", calories(date).optDouble("total_kcal"))
            put("active_calories_kcal", calories(date).optDouble("active_kcal"))
            put("distance_km", distance(date).optDouble("total_km"))
            put("sleep_hours", sleep(date).optDouble("total_hours"))
            put("heart_rate_avg", heartRate(date).optDouble("avg_bpm"))
        }
    }

    suspend fun range(metric: String, start: LocalDate, end: LocalDate): JSONObject {
        val days = JSONArray()
        var cur = start
        while (!cur.isAfter(end)) {
            val value = when (metric) {
                "steps" -> steps(cur).optLong("total").toDouble()
                "calories" -> calories(cur).optDouble("total_kcal")
                "distance" -> distance(cur).optDouble("total_km")
                "sleep" -> sleep(cur).optDouble("total_hours")
                "heart_rate" -> heartRate(cur).optDouble("avg_bpm")
                else -> 0.0
            }
            days.put(JSONObject().apply {
                put("date", cur.toString())
                put("value", value)
            })
            cur = cur.plusDays(1)
        }
        return JSONObject().apply {
            put("metric", metric)
            put("start", start.toString())
            put("end", end.toString())
            put("days", days)
        }
    }
}
