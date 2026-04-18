# Health Connect MCP

Connect your Android Health Connect data (steps, calories, sleep, heart rate, distance) to Claude or any MCP-compatible AI assistant.

Works with anything that writes to Health Connect: Mi Fitness (Xiaomi), Samsung Health, Fitbit, Google Fit, Oura, Withings, and more.

## Architecture

```
 Wearable → Health Connect ──► [Android App]  (reads via Health Connect API,
                                     │         serves local HTTP on your WiFi)
                                     ▼
                            [Node.js MCP Server]
                                     │
                                     ▼
                                Claude Code
```

The Android companion app runs on your phone, reads Health Connect data, and exposes a small HTTP API on your local WiFi. A Node.js MCP server on your computer queries that API and exposes the data to Claude.

No cloud involved. Your data stays between your phone and your computer.

## Install — Android app

1. Download the latest `app-release.apk` from [Releases](../../releases).
2. Install it on your Android phone (allow "Install from unknown sources").
3. Open the app and grant Health Connect permissions when prompted.
4. Tap **Start server**.
5. Note the **Server URL** and **Bearer Token** shown.

The app will keep running in background while connected to power or foreground.

## Install — MCP server

On your computer (same WiFi as your phone):

```bash
claude mcp add health-connect \
  -e HC_MCP_URL=http://PHONE_IP:8080 \
  -e HC_MCP_TOKEN=YOUR_TOKEN \
  -- npx -y health-connect-mcp-server
```

Replace `PHONE_IP` and `YOUR_TOKEN` with the values shown in the app.

## Use

Once configured, ask Claude things like:

- "¿Cuántos pasos hice esta semana?"
- "¿Cómo dormí anoche?"
- "Compara mi frecuencia cardíaca promedio de esta semana con la anterior"
- "¿Cuántas calorías he quemado hoy?"

## Tools exposed by the MCP

- `get_daily_summary(date?)` — all metrics for a date
- `get_steps(date?)`
- `get_calories(date?)` — total, active, basal
- `get_distance(date?)` — meters and km
- `get_sleep(date?)` — sessions and total hours
- `get_heart_rate(date?)` — avg / min / max / sample count
- `get_range(metric, start, end)` — daily values for trend analysis

## API endpoints (Android app)

If you want to integrate this with another client:

| Method | Path | Query params |
|---|---|---|
| GET | `/health` | — |
| GET | `/summary` | `date=YYYY-MM-DD` |
| GET | `/steps` | `date` |
| GET | `/calories` | `date` |
| GET | `/distance` | `date` |
| GET | `/sleep` | `date` |
| GET | `/heart-rate` | `date` |
| GET | `/range` | `metric=steps\|calories\|distance\|sleep\|heart_rate&start&end` |

All authenticated endpoints require: `Authorization: Bearer <token>`.

## Requirements

- Android 9.0+ with [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed
- A phone and computer on the same WiFi
- Node.js 18+ on your computer

## Privacy

- Your health data never leaves your local network.
- No analytics, no telemetry, no cloud storage.
- The Bearer token protects against other devices on your WiFi.

## License

MIT
