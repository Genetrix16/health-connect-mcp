# Health Connect MCP Server

Node.js MCP server that bridges Claude Code (or any MCP client) to the Health Connect MCP Android companion app.

## Install

```
npm install -g health-connect-mcp-server
```

Or run directly with npx.

## Configure with Claude Code

```
claude mcp add health-connect \
  -e HC_MCP_URL=http://PHONE_IP:8080 \
  -e HC_MCP_TOKEN=YOUR_TOKEN \
  -- npx -y health-connect-mcp-server
```

Replace `PHONE_IP` and `YOUR_TOKEN` with the values shown in the Android app.

## Tools exposed

- `get_daily_summary(date?)`
- `get_steps(date?)`
- `get_calories(date?)`
- `get_distance(date?)`
- `get_sleep(date?)`
- `get_heart_rate(date?)`
- `get_range(metric, start, end)`

All dates are `YYYY-MM-DD`. When omitted, today is used.
