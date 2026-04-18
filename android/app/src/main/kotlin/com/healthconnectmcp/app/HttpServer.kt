package com.healthconnectmcp.app

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

class HttpServer(
    port: Int,
    private val token: String,
    context: Context
) : NanoHTTPD(port) {

    private val reader = HealthReader(context)
    private val expectedAuthBytes: ByteArray =
        if (token.isEmpty()) ByteArray(0)
        else "bearer $token".lowercase().toByteArray(Charsets.UTF_8)
    private val failedAttempts = ConcurrentHashMap<String, AttemptRecord>()

    private data class AttemptRecord(val count: Int, val windowStart: Long)

    private object TooManyRequests : Response.IStatus {
        override fun getRequestStatus(): Int = 429
        override fun getDescription(): String = "429 Too Many Requests"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        if (uri == "/health") {
            return json(JSONObject().apply { put("status", "ok") })
        }

        val ip = try {
            session.remoteIpAddress ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }

        if (isRateLimited(ip)) {
            return error(TooManyRequests, "rate_limited")
        }

        if (token.isNotEmpty()) {
            val provided = (session.headers["authorization"] ?: "")
                .lowercase()
                .toByteArray(Charsets.UTF_8)
            if (!MessageDigest.isEqual(provided, expectedAuthBytes)) {
                recordFailure(ip)
                try { Thread.sleep(AUTH_FAILURE_DELAY_MS) } catch (_: InterruptedException) {}
                return error(Response.Status.UNAUTHORIZED, "unauthorized")
            }
        }

        val params = session.parameters
        return try {
            val date = params["date"]?.firstOrNull()?.let { LocalDate.parse(it) }
                ?: LocalDate.now()

            val result: JSONObject = runBlocking {
                when (uri) {
                    "/summary" -> reader.summary(date)
                    "/steps" -> reader.steps(date)
                    "/calories" -> reader.calories(date)
                    "/distance" -> reader.distance(date)
                    "/sleep" -> reader.sleep(date)
                    "/heart-rate" -> reader.heartRate(date)
                    "/range" -> {
                        val metric = params["metric"]?.firstOrNull() ?: "steps"
                        val start = LocalDate.parse(params["start"]?.firstOrNull())
                        val end = LocalDate.parse(params["end"]?.firstOrNull())
                        reader.range(metric, start, end)
                    }
                    else -> return@runBlocking JSONObject().apply { put("error", "not_found") }
                }
            }

            if (result.has("error")) error(Response.Status.NOT_FOUND, result.getString("error"))
            else json(result)
        } catch (_: SecurityException) {
            error(Response.Status.FORBIDDEN, "permission_denied")
        } catch (_: DateTimeParseException) {
            error(Response.Status.BAD_REQUEST, "bad_date")
        } catch (_: Exception) {
            error(Response.Status.INTERNAL_ERROR, "internal_error")
        }
    }

    private fun isRateLimited(ip: String): Boolean {
        val rec = failedAttempts[ip] ?: return false
        val now = System.currentTimeMillis()
        if (now - rec.windowStart > WINDOW_MS) {
            failedAttempts.remove(ip)
            return false
        }
        return rec.count >= MAX_FAILURES
    }

    private fun recordFailure(ip: String) {
        val now = System.currentTimeMillis()
        failedAttempts.compute(ip) { _, old ->
            if (old == null || now - old.windowStart > WINDOW_MS) AttemptRecord(1, now)
            else AttemptRecord(old.count + 1, old.windowStart)
        }
    }

    private fun json(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString())

    private fun error(status: Response.IStatus, msg: String): Response {
        val body = JSONObject().apply { put("error", msg) }.toString()
        return newFixedLengthResponse(status, "application/json", body)
    }

    companion object {
        private const val MAX_FAILURES = 10
        private const val WINDOW_MS = 60_000L
        private const val AUTH_FAILURE_DELAY_MS = 250L
    }
}
