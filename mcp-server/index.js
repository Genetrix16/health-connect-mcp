#!/usr/bin/env node
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

const BASE_URL = process.env.HC_MCP_URL;
const TOKEN = process.env.HC_MCP_TOKEN || "";
const REQUEST_TIMEOUT_MS = 10_000;

if (!BASE_URL) {
  console.error(
    "HC_MCP_URL not set. Example: http://192.168.1.42:8080"
  );
  process.exit(1);
}

async function request(path, params = {}) {
  const url = new URL(path, BASE_URL);
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, v);
  }
  const headers = {};
  if (TOKEN) headers["Authorization"] = `Bearer ${TOKEN}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const res = await fetch(url, { headers, signal: controller.signal });
    if (!res.ok) {
      throw new Error(`upstream_${res.status}`);
    }
    return await res.text();
  } finally {
    clearTimeout(timer);
  }
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

const tools = [
  {
    name: "get_daily_summary",
    description:
      "Get a summary of steps, calories, distance, sleep and heart rate for a given date.",
    inputSchema: {
      type: "object",
      properties: {
        date: {
          type: "string",
          description: "Date in YYYY-MM-DD. Defaults to today.",
        },
      },
    },
    handler: async (args) =>
      await request("/summary", { date: args.date || today() }),
  },
  {
    name: "get_steps",
    description: "Get step count for a given date.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string" } },
    },
    handler: async (args) =>
      await request("/steps", { date: args.date || today() }),
  },
  {
    name: "get_calories",
    description: "Get calories burned (total, active, basal) for a given date.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string" } },
    },
    handler: async (args) =>
      await request("/calories", { date: args.date || today() }),
  },
  {
    name: "get_distance",
    description: "Get distance travelled (meters and km) for a given date.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string" } },
    },
    handler: async (args) =>
      await request("/distance", { date: args.date || today() }),
  },
  {
    name: "get_sleep",
    description:
      "Get sleep sessions and total sleep hours for a given date. The date is the wake-up date.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string" } },
    },
    handler: async (args) =>
      await request("/sleep", { date: args.date || today() }),
  },
  {
    name: "get_heart_rate",
    description:
      "Get heart rate stats (avg, min, max, sample count) for a given date.",
    inputSchema: {
      type: "object",
      properties: { date: { type: "string" } },
    },
    handler: async (args) =>
      await request("/heart-rate", { date: args.date || today() }),
  },
  {
    name: "get_range",
    description:
      "Get a metric aggregated per-day for a date range. Useful for trend analysis.",
    inputSchema: {
      type: "object",
      properties: {
        metric: {
          type: "string",
          enum: ["steps", "calories", "distance", "sleep", "heart_rate"],
        },
        start: { type: "string", description: "YYYY-MM-DD" },
        end: { type: "string", description: "YYYY-MM-DD" },
      },
      required: ["metric", "start", "end"],
    },
    handler: async (args) =>
      await request("/range", {
        metric: args.metric,
        start: args.start,
        end: args.end,
      }),
  },
];

const server = new Server(
  { name: "health-connect-mcp", version: "0.1.2" },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: tools.map(({ handler, ...t }) => t),
}));

server.setRequestHandler(CallToolRequestSchema, async (req) => {
  const tool = tools.find((t) => t.name === req.params.name);
  if (!tool) {
    return {
      content: [{ type: "text", text: `Unknown tool: ${req.params.name}` }],
      isError: true,
    };
  }
  try {
    const result = await tool.handler(req.params.arguments || {});
    return { content: [{ type: "text", text: result }] };
  } catch (e) {
    return {
      content: [{ type: "text", text: `Error: ${e.message}` }],
      isError: true,
    };
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
