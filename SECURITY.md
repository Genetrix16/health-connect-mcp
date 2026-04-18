# Security Policy

## Supported versions

Only the latest tagged release on `main` is supported. Older releases receive
no fixes.

## Reporting a vulnerability

**Do not open a public GitHub issue for security reports.**

Use GitHub's [private vulnerability reporting](https://github.com/Genetrix16/health-connect-mcp/security/advisories/new)
instead. I'll respond on a best-effort basis (this is a hobby project — please
be patient). If you get no response in 14 days, you may disclose publicly.

## Scope

**In scope:**

- The Android app: Kotlin code, `AndroidManifest.xml`, signing configuration,
  permissions, the local HTTP server, token handling.
- The Node.js MCP server (`mcp-server/`).
- The CI workflow and its handling of signing keys.
- The release signing process.

**Out of scope:**

- Attacks that require both (a) being on the same WiFi as the victim and
  (b) knowing or guessing the 192-bit Bearer token. This is the documented
  threat model; see [README — Security](README.md#security).
- Bugs in third-party components (Health Connect provider, Mi Fitness,
  Samsung Health, Android OS itself, NanoHTTPD beyond documented behavior,
  `@modelcontextprotocol/sdk`).
- Social engineering, physical device access, compromised host machines.
- Rate-limit bypasses that still require the correct Bearer token — the
  rate-limit is defense-in-depth, not the primary control.

## Threat model summary

- The app assumes a **trusted local WiFi**. Traffic is plain HTTP.
- The Bearer token (24 random bytes, 192-bit entropy) is the only access
  control on data endpoints.
- No outbound network calls are made by the app. Data only leaves the phone
  in response to authenticated requests from the user's own MCP client.
- Release APKs are signed by the maintainer. Users should verify the
  SHA-256 fingerprint of the signing certificate before installing updates.

## Credits

Responsible reporters will be credited in release notes (with permission).
