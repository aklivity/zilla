// Headless MCP client for the Zilla mcp proxy. By default prints every tool
// (bare, e.g. "everything__echo"), resource ("resource:<uri>") and resource
// template ("template:<uriTemplate>"), one per line. Set CALL_TOOL (and optionally
// CALL_ARGS, a JSON object) to call one tool and print its text content, or
// READ_RESOURCE to read one resource URI and print its contents.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");
const READ_RESOURCE = process.env.READ_RESOURCE;

const log = (...args) => console.error("[client]", ...args);

const main = async () =>
{
    const client = new Client(
        { name: "zilla-mcp-proxy-tools-list-client", version: "0.0.1" },
        { capabilities: {} });

    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL));
    await client.connect(transport);
    log(`connected, protocolVersion=${transport.protocolVersion}`);

    try
    {
        if (CALL_TOOL)
        {
            const result = await client.callTool({ name: CALL_TOOL, arguments: CALL_ARGS });
            console.log(result.content?.map((c) => c.text ?? JSON.stringify(c)).join(" ") ?? "");
        }
        else if (READ_RESOURCE)
        {
            const result = await client.readResource({ uri: READ_RESOURCE });
            for (const entry of result.contents ?? [])
            {
                console.log(entry.text ?? JSON.stringify(entry));
            }
        }
        else
        {
            const { tools } = await client.listTools();
            const { resources } = await client.listResources();
            const { resourceTemplates } = await client.listResourceTemplates();

            tools.forEach((tool) => console.log(tool.name));
            resources.forEach((resource) => console.log(`resource:${resource.uri}`));
            resourceTemplates.forEach((template) => console.log(`template:${template.uriTemplate}`));
        }
    }
    finally
    {
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
};

main().catch((err) =>
{
    log(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
