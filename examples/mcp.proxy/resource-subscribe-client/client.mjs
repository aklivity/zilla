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
// advertises resources.subscribe:true -- and lists the "everything" toolkit's resources at all
// -- once that handshake has actually completed at least once. The very first session against a
// freshly started gateway can race that hydration -- see "Observe the cache" in the README --
// surfacing either as a missing resources.subscribe capability or as resources/list simply not
// yet including an everything+... resource. So this client retries the whole handshake a few
// times rather than treating either transient miss as a hard failure.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { ResourceUpdatedNotificationSchema } from "@modelcontextprotocol/sdk/types.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_TOKEN = process.env.JWT_TOKEN;
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS ?? 20000);
const RETRIES = Number(process.env.RETRIES ?? 5);
const RETRY_DELAY_MS = Number(process.env.RETRY_DELAY_MS ?? 3000);

const headers = JWT_TOKEN ? { authorization: `Bearer ${JWT_TOKEN}` } : {};

// Elapsed rather than wall-clock: this process's stderr is captured by the
// caller and only reaches the log when it exits, so every line lands with the
// same flush timestamp. Stamping each line with its own offset from start is
// what makes the sequence -- and any gap in it -- readable after the fact.
const START = Date.now();
const log = (...args) => console.error("[client]", `+${((Date.now() - START) / 1000).toFixed(3)}s`, ...args);

// node's own startup and the evaluation of the hoisted imports above both run
// before START, so they are charged to the caller's wall-clock while landing
// outside every stamp below. That blind spot is not theoretical: a 20.7s
// round-trip measured here accounted for only 0.489s of client work, and the
// missing ~20s was invisible until this line existed. process.uptime() is
// exactly that span.
log(`node startup and imports took ${process.uptime().toFixed(3)}s, before the stamps below`);

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
        // Each step is logged on completion because this block runs before the process
        // exits, and therefore before the caller sees any result -- so a slow step here
        // is charged to the whole round-trip while looking like a slow notification.
        await client.callTool({ name: "everything__toggle-subscriber-updates", arguments: {} });
        log("toggled subscriber updates off");
        await client.unsubscribeResource({ uri });
        log(`unsubscribed from ${uri}`);
        try
        {
            await transport.terminateSession();
            log("terminated session");
        }
        catch (err)
        {
            log(`failed to terminate session: ${err}`);
        }
        await client.close();
        log("closed client");
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
            const message = err.message ?? "";
            const hydrationRace = /does not support resource subscriptions/.test(message) ||
                /no everything\+ resource found/.test(message);
            if (!hydrationRace || remaining <= 1)
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
