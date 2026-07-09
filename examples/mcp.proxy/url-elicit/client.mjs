// Headless MCP client that exercises url-mode elicitation (SEP-1036) end-to-end
// through the Zilla mcp proxy. No interactive UI: it advertises the
// `elicitation.url` capability, reacts to the server's `mode:"url"`
// `elicitation/create` request by "opening" the URL (an HTTP GET, standing in
// for the user's browser), then waits for the server's
// `notifications/elicitation/complete`. Used by .github/test.sh to assert the
// gateway relays both directions; built on the published SDK already installed
// for the url-elicit server.
//
// The urlelicit toolkit route is guarded (urlelicit:authorize), so a JWT with
// that scope must be supplied as JWT_TOKEN -- an anonymous request never
// reaches urlelicit__authorize at all.

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
let sawComplete = false;
let completeResolve;
const completed = new Promise((resolve) => { completeResolve = resolve; });

const client = new Client(
    { name: "zilla-mcp-proxy-url-elicit-client", version: "0.0.1" },
    { capabilities: { elicitation: { url: {} } } });

// React to the server-initiated elicitation/create request relayed by Zilla.
client.setRequestHandler(ElicitRequestSchema, async (request) =>
{
    const params = request.params;
    if (params.mode === "url")
    {
        sawUrlRequest = true;
        log(`elicitation/create mode=url message="${params.message}" url=${params.url}`);
        // Stand in for the user opening the link in a browser.
        await fetch(params.url).then((r) => log(`opened url -> ${r.status}`)).catch((e) => log(`open url failed: ${e}`));
        return { action: "accept" };
    }

    log(`elicitation/create mode=${params.mode ?? "form"} message="${params.message}"`);
    return { action: "accept", content: {} };
});

// Observe the out-of-band completion notification relayed by Zilla.
client.setNotificationHandler(ElicitationCompleteNotificationSchema, (notification) =>
{
    sawComplete = true;
    log(`notifications/elicitation/complete elicitationId=${notification.params.elicitationId}`);
    completeResolve();
});

const main = async () =>
{
    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);
    log(`connected, protocolVersion=${transport.protocolVersion}`);

    const result = await client.callTool({ name: TOOL, arguments: { resource: "demo" } });
    const text = result.content?.map((c) => c.text).join(" ") ?? "";
    log(`tool result: ${text}`);

    // Wait for the completion notification (fires shortly after the call).
    await Promise.race([
        completed,
        new Promise((_, reject) => setTimeout(() => reject(new Error("timed out waiting for completion")), TIMEOUT_MS))
    ]);

    await client.close();

    if (!sawUrlRequest)
    {
        throw new Error("did not receive url-mode elicitation/create");
    }
    if (!sawComplete)
    {
        throw new Error("did not receive notifications/elicitation/complete");
    }

    console.log("OK url-mode elicitation relayed end-to-end");
};

main().catch((err) =>
{
    log(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
