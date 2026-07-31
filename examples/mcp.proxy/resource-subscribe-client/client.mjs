// Headless MCP client that exercises resources/subscribe, notifications/resources/updated,
// and resources/unsubscribe end-to-end through the Zilla mcp proxy, against the "everything"
// reference server (aklivity/zilla#2220). No JWT is required -- the everything toolkit route
// has no `guarded:` restriction.
//
// Flow:
// 1. resources/list, to find a real everything+... resource URI to subscribe to (the exact
//    set of static resources the reference server exposes is not pinned by this script).
// 2. resources/subscribe for that URI -- relayed south_mcp_client_everything -> everything.
// 3. everything__toggle-subscriber-updates, a tool the everything server registers
//    specifically to start a per-session 5-second interval of simulated
//    notifications/resources/updated for whatever that session has subscribed to.
//    Subscribing alone only registers interest; nothing is pushed until this tool is called.
// 4. Wait for notifications/resources/updated, relayed everything -> south_mcp_client_everything
//    -> north_mcp_proxy (re-prefixing the URI) -> north_mcp_server -> this client, as an SSE event.
// 5. resources/unsubscribe, then toggle the updates back off.
//
// Zilla's mcp(proxy) aggregates real per-toolkit server capabilities (including
// resources.subscribe) from each south binding's own handshake with its upstream, and only
// advertises resources.subscribe:true once that handshake has actually completed at least
// once. The very first session against a freshly started gateway can race that hydration --
// see "Observe the cache" in the README -- so this client retries the whole handshake a few
// times rather than treating a transient capability-negotiation miss as a hard failure.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { ResourceUpdatedNotificationSchema } from "@modelcontextprotocol/sdk/types.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_TOKEN = process.env.JWT_TOKEN;
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS ?? 20000);
const RETRIES = Number(process.env.RETRIES ?? 5);
const RETRY_DELAY_MS = Number(process.env.RETRY_DELAY_MS ?? 3000);

const headers = JWT_TOKEN ? { authorization: `Bearer ${JWT_TOKEN}` } : {};

const log = (...args) => console.error("[client]", ...args);

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const attempt = async () =>
{
    let updatedResolve;
    let updatedUri;
    const updated = new Promise((resolve) => { updatedResolve = resolve; });

    const client = new Client(
        { name: "zilla-mcp-proxy-resource-subscribe-client", version: "0.0.1" },
        { capabilities: {} });

    client.setNotificationHandler(ResourceUpdatedNotificationSchema, (notification) =>
    {
        updatedUri = notification.params.uri;
        log(`notifications/resources/updated uri=${updatedUri}`);
        updatedResolve();
    });

    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);
    log(`connected, protocolVersion=${transport.protocolVersion}`);

    const { resources } = await client.listResources();
    const resource = resources.find((r) => r.uri.startsWith("everything+"));
    if (!resource)
    {
        throw new Error("no everything+ resource found in resources/list");
    }
    const uri = resource.uri;

    await client.subscribeResource({ uri });
    log(`subscribed to ${uri}`);

    const toggleOn = await client.callTool({ name: "everything__toggle-subscriber-updates", arguments: {} });
    log(`toggle-subscriber-updates: ${toggleOn.content?.map((c) => c.text).join(" ")}`);

    try
    {
        await Promise.race([
            updated,
            new Promise((_, reject) => setTimeout(
                () => reject(new Error("timed out waiting for notifications/resources/updated")), TIMEOUT_MS))
        ]);
    }
    finally
    {
        await client.callTool({ name: "everything__toggle-subscriber-updates", arguments: {} });
        await client.unsubscribeResource({ uri });
        await client.close();
    }

    if (updatedUri !== uri)
    {
        throw new Error(`expected update for ${uri}, got ${updatedUri}`);
    }

    return uri;
};

const main = async () =>
{
    for (let remaining = RETRIES; ; remaining--)
    {
        try
        {
            const uri = await attempt();
            console.log(`OK resource subscription relayed end-to-end for ${uri}`);
            return;
        }
        catch (err)
        {
            const capabilityMiss = /does not support resource subscriptions/.test(err.message ?? "");
            if (!capabilityMiss || remaining <= 1)
            {
                throw err;
            }
            log(`retrying after: ${err.message ?? err}`);
            await sleep(RETRY_DELAY_MS);
        }
    }
};

main().catch((err) =>
{
    log(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
