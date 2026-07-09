// Minimal MCP server demonstrating url-mode elicitation (SEP-1036, MCP 2025-11-25).
//
// On an `authorize` tool call it sends an `elicitation/create` request with
// mode "url", asking the client to open a link in the browser to complete an
// out-of-band interaction, then signals completion with
// `notifications/elicitation/complete`. Built on the published
// @modelcontextprotocol/sdk; no OAuth, so it runs as a plain Streamable HTTP
// upstream behind the Zilla mcp proxy.

import { randomUUID } from "node:crypto";

import express from "express";
import { z } from "zod";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { isInitializeRequest } from "@modelcontextprotocol/sdk/types.js";

const PORT = Number(process.env.PORT ?? 3003);

const createServer = () =>
{
    const server = new McpServer(
        { name: "url-elicitation", version: "1.0.0" },
        { capabilities: { logging: {} } });

    server.registerTool(
        "authorize",
        {
            description: "Authorize access to a protected resource by confirming in the browser (url-mode elicitation)",
            inputSchema: { resource: z.string().describe("resource to authorize") }
        },
        async ({ resource }) =>
        {
            const elicitationId = randomUUID();
            const complete = server.server.createElicitationCompletionNotifier(elicitationId);
            setTimeout(() => complete().catch(() => {}), 3000);

            const result = await server.server.elicitInput({
                mode: "url",
                message: `Open the link to authorize access to ${resource}.`,
                url: `http://localhost:${PORT}/authorize?elicitation=${elicitationId}`,
                elicitationId
            });

            return { content: [{ type: "text", text: `authorization ${result.action} for ${resource}` }] };
        });

    return server;
};

const transports = {};
const app = express();
app.use(express.json());

const handle = async (req, res, body) =>
{
    // Zilla's south_mcp_client_urlelicit forwards the caller's own JWT here via
    // options.authorization; logged so .github/test.sh can confirm it arrived.
    console.log(`authorization: ${req.headers["authorization"] ?? "(none)"}`);

    const sessionId = req.headers["mcp-session-id"];
    let transport = sessionId ? transports[sessionId] : undefined;

    if (!transport && isInitializeRequest(body))
    {
        transport = new StreamableHTTPServerTransport({
            sessionIdGenerator: () => randomUUID(),
            onsessioninitialized: (id) => { transports[id] = transport; }
        });
        transport.onclose = () =>
        {
            if (transport.sessionId)
            {
                delete transports[transport.sessionId];
            }
        };
        await createServer().connect(transport);
    }

    if (!transport)
    {
        res.status(400).json({ jsonrpc: "2.0", error: { code: -32000, message: "Bad Request: session required" }, id: null });
        return;
    }

    await transport.handleRequest(req, res, body);
};

app.post("/mcp", (req, res) => handle(req, res, req.body));
app.get("/mcp", (req, res) => handle(req, res));
app.delete("/mcp", (req, res) => handle(req, res));

app.get("/authorize", (req, res) =>
    res.set("content-type", "text/html").send(
        `<!doctype html><meta charset=utf-8><title>Authorize</title>` +
        `<body style="font-family:sans-serif;max-width:30rem;margin:4rem auto;text-align:center">` +
        `<h1>Authorized &#10003;</h1><p>You may close this window and return to your MCP client.</p></body>`));

app.listen(PORT, () => console.log(`url-elicitation MCP server listening on ${PORT}`));
