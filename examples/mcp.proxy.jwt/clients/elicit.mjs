import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import {
    ElicitRequestSchema,
    ElicitationCompleteNotificationSchema
} from "@modelcontextprotocol/sdk/types.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const TOOL = process.env.TOOL ?? "urlelicit__authorize";
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS ?? 15000);
const JWT_TOKEN = process.env.JWT_TOKEN;

const headers = JWT_TOKEN ? { authorization: `Bearer ${JWT_TOKEN}` } : {};
const log = (...args) => console.error("[client]", ...args);

let sawUrlRequest = false;
let completeResolve;
const completed = new Promise((resolve) => { completeResolve = resolve; });

const client = new Client(
    { name: "mcp-proxy-jwt-elicit-client", version: "0.0.1" },
    { capabilities: { elicitation: { url: {} } } });

client.setRequestHandler(ElicitRequestSchema, async (request) =>
{
    const { mode, message, url } = request.params;
    log(`elicitation/create mode=${mode} message="${message}" url=${url}`);
    sawUrlRequest = mode === "url";
    return { action: "accept" };
});

client.setNotificationHandler(ElicitationCompleteNotificationSchema, (notification) =>
{
    log(`notifications/elicitation/complete elicitationId=${notification.params.elicitationId}`);
    completeResolve();
});

const awaitWithin = async (promise, ms, message) =>
{
    let timer;
    try
    {
        await Promise.race([
            promise,
            new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(message)), ms); })
        ]);
    }
    finally
    {
        clearTimeout(timer);
    }
};

const main = async () =>
{
    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);

    const result = await client.callTool({ name: TOOL, arguments: { resource: "demo" } });
    log(`tool result: ${result.content?.map((c) => c.text).join(" ") ?? ""}`);

    await awaitWithin(completed, TIMEOUT_MS, "timed out waiting for notifications/elicitation/complete");

    await transport.terminateSession().catch(() => {});
    await client.close();

    if (!sawUrlRequest)
    {
        throw new Error("did not receive url-mode elicitation/create");
    }

    console.log("OK url-mode elicitation relayed end-to-end");
};

main().catch((err) =>
{
    log(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
