package com.healthconnectmcp.app

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.LocalDate

class HttpServer(
    port: Int,
    private val token: String,
    private val context: Context
) : NanoHTTPD(port) {

    private val reader = HealthReader(context)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val params = session.parameters

        if (uri == "/health") {
            return json(JSONObject().apply {
                put("status", "ok")
                put("version", "0.1.0")
            })
        }

        val auth = session.headers["authorization"] ?: ""
        val expected = "bearer $token".lowercase()
        if (token.isNotEmpty() && auth.lowercase() != expected) {
            return error(Response.Status.UNAUTHORIZED, "missing or invalid bearer token")
        }

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
        } catch (e: SecurityException) {
            error(Response.Status.FORBIDDEN, "health connect permissions missing: ${e.message}")
        } catch (e: Exception) {
            error(Response.Status.INTERNAL_ERROR, "error: ${e.message}")
        }
    }

    private fun json(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString())

    private fun error(status: Response.Status, msg: String): Response {
        val body = JSONObject().apply { put("error", msg) }.toString()
        return newFixedLengthResponse(status, "application/json", body)
    }
}
