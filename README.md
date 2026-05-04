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
2. **Verify the signing certificate fingerprint** matches the SHA-256 published with the release (see [APK signing](#apk-signing)). If it does not match, do not install.
3. Install it on your Android phone (you'll need to allow "Install from unknown sources").
4. Open the app and grant Health Connect permissions when prompted.
5. Tap **Start server**.
6. Copy the **Server URL** and **Bearer Token** shown — you'll need them in the next step.

The app runs as a foreground service with a persistent notification.

## Install — MCP server

On your computer (same WiFi as your phone):

```bash
claude mcp add health-connect \
  -e HC_MCP_URL=http://PHONE_IP:8080 \
  -e HC_MCP_TOKEN=YOUR_TOKEN \
  -- npx -y health-connect-mcp-server
```

Replace `PHONE_IP` and `YOUR_TOKEN` with the values from the Android app.

Restart your MCP client (e.g., VSCode / Claude Code) so the new server loads.

## Use

Once configured, ask Claude things like:

- "How many steps did I take this week?"
- "How did I sleep last night?"
- "Compare my average heart rate this week versus last week."
- "How many calories have I burned today?"

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

| Method | Path | Auth? | Query params |
|---|---|---|---|
| GET | `/health` | no | — |
| GET | `/summary` | yes | `date=YYYY-MM-DD` |
| GET | `/steps` | yes | `date` |
| GET | `/calories` | yes | `date` |
| GET | `/distance` | yes | `date` |
| GET | `/sleep` | yes | `date` |
| GET | `/heart-rate` | yes | `date` |
| GET | `/range` | yes | `metric=steps\|calories\|distance\|sleep\|heart_rate&start&end` |

`/health` returns `{"status":"ok"}` with no information about the app version. All other endpoints require `Authorization: Bearer <token>`. Failed authentication is rate-limited (10 attempts per minute per source IP).

## Requirements

- Android 9.0+ with [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) installed
- A phone and computer on the same WiFi
- Node.js 18+ on your computer

### IP address changes

Your phone's local IP may change if your router reassigns it (e.g. after a reboot). If the MCP stops working, open the Android app — the current IP is always shown in the **Server URL** field. Update `HC_MCP_URL` in your MCP config to match.

To avoid this, assign a static (reserved) IP to your phone in your router's DHCP settings. Once set, the IP shown in the app will stay the same across reboots.

## Security

### Threat model

This app runs an HTTP server on your phone, reachable by any device on the same WiFi. The security model assumes:

- **You trust every device on your WiFi.** Traffic is plain HTTP (no TLS). Anyone who can sniff your LAN can read your health data and capture the Bearer token. Use only on home, work, or other private WiFi networks you control. **Do not use on public WiFi** (café, hotel, university, airport).
- **The Bearer token is your only defense.** It is 24 random bytes (192-bit entropy), generated with `SecureRandom`. Auth comparison is constant-time. Failed attempts are rate-limited (10/minute per IP) and delayed 250 ms each.
- **If you believe the token has leaked**, tap **Regenerate token** in the app. This rotates the token in storage *and* restarts the running server so the old token stops working immediately.
- **Read-only.** No tool writes to Health Connect. The app requests read-only permissions for steps, total/active calories, sleep, heart rate, and distance. Nothing else.
- **No cloud, no telemetry, no analytics.** The app makes no outbound network calls. Data only leaves the phone in response to authenticated requests from your own MCP client. Note: conversations with your MCP client (Claude, etc.) travel to that client's inference backend — and sleep session titles may include free-form text you wrote in your wearable app, so treat them as potentially sensitive.
- **No adb backups.** The APK sets `android:allowBackup="false"`, so the token is not captured by `adb backup`.

### APK signing

Release APKs are signed by the maintainer with a dedicated release keystore.

**Canonical signing certificate SHA-256 fingerprint** — the same across every release from `v0.1.2` onward. If a downloaded APK shows a different fingerprint, it did not come from the same source, **do not install it**:

```
27:07:14:32:49:A7:35:42:B0:79:B3:12:B9:1B:2E:B5:12:D5:28:F4:22:F3:5F:0B:0A:27:D2:5E:60:B3:20:49
```

Verify a downloaded APK with `apksigner` from Android SDK build-tools:

```bash
apksigner verify --print-certs app-release.apk
```

Debug-signed APKs (from before `v0.1.2`, or from CI builds of PRs without access to the signing secrets) carry the Android SDK public debug fingerprint instead and must not be installed as production builds. If you have `v0.1.0` or `v0.1.1` installed, uninstall it before installing `v0.1.2` — Android will refuse to upgrade across different signing certificates.

### Reporting vulnerabilities

See [SECURITY.md](SECURITY.md).

## Building from source

Requirements: JDK 17, Android SDK, Node.js 18+.

```bash
# Android APK (debug)
cd android && ./gradlew assembleDebug
# APK lands at android/app/build/outputs/apk/debug/app-debug.apk

# MCP server
cd mcp-server && npm install && npm start
```

## License

MIT. See [LICENSE](LICENSE).
