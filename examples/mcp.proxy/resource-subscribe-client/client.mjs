// Headless MCP client that exercises resources/subscribe, notifications/resources/updated
// and resources/unsubscribe end-to-end through the Zilla mcp proxy, against the
// "everything" reference server.
//
// 1. resources/list, to find an everything+... resource URI to subscribe to.
// 2. resources/subscribe for that URI.
// 3. everything__toggle-subscriber-updates, which makes the everything server push a
//    simulated notifications/resources/updated every 5 seconds for whatever the
//    session has subscribed to -- subscribing alone pushes nothing.
// 4. Wait for the relayed notifications/resources/updated.
// 5. resources/unsubscribe, then toggle the updates back off.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { ResourceUpdatedNotificationSchema } from "@modelcontextprotocol/sdk/types.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const TIMEOUT_MS = Number(process.env.TIMEOUT_MS ?? 20000);

const log = (...args) => console.error("[client]", ...args);

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
        // a pending timer would hold the process open for the full TIMEOUT_MS
        clearTimeout(timer);
    }
};

const main = async () =>
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

    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL));
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
        await awaitWithin(updated, TIMEOUT_MS, "timed out waiting for notifications/resources/updated");
    }
    finally
    {
        await client.callTool({ name: "everything__toggle-subscriber-updates", arguments: {} });
        await client.unsubscribeResource({ uri });
        log(`unsubscribed from ${uri}`);
        try
        {
            await transport.terminateSession();
        }
        catch (err)
        {
            log(`failed to terminate session: ${err}`);
        }
        await client.close();
    }

    if (updatedUri !== uri)
    {
        throw new Error(`expected update for ${uri}, got ${updatedUri}`);
    }

    console.log(`OK resource subscription relayed end-to-end for ${uri}`);
};

main().catch((err) =>
{
    log(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
