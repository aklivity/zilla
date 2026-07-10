// Headless MCP client that connects through the Zilla mcp proxy. In its
// default (list) mode it prints every tool, resource, and resource template
// it can see -- one per line, tools bare (e.g. "everything__echo"), resources
// prefixed "resource:", and resource templates prefixed "template:". A tool
// omitted from this list is not necessarily gone -- options.cache.tools.eager
// on north_mcp_proxy keeps only a fixed set eagerly listed; the rest are
// "cold" and absent here but still callable by name and discoverable via the
// zilla__search_tools/zilla__describe_tool/zilla__execute_tool family. Set
// CALL_TOOL (and optionally CALL_ARGS, a JSON object) to instead call one
// tool and print its result -- zilla__search_tools returns schema-free
// matches in structuredContent.tools, printed space-separated by name;
// zilla__describe_tool returns one full tool definition (schema included) in
// the same field, printed as JSON since there's more than a name to show;
// zilla__execute_tool's own result is the target tool's real result, passed
// through unchanged, so it prints exactly like calling that tool directly;
// every other tool's result prints its text content as-is -- or READ_RESOURCE
// (a concrete URI, with any {template} placeholders already substituted) to
// read one resource and print its contents. Optionally carries a bearer
// token on the initial request so .github/test.sh can observe how the result
// set (or an authorized call/read's effect) changes with the caller's
// authorized scopes -- unauthorized toolkits and tools/resources are absent
// from the list entirely, never present-but-marked-denied.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_TOKEN = process.env.JWT_TOKEN;
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");
const READ_RESOURCE = process.env.READ_RESOURCE;

const headers = JWT_TOKEN ? { authorization: `Bearer ${JWT_TOKEN}` } : {};

const log = (...args) => console.error("[client]", ...args);

const main = async () =>
{
    const client = new Client(
        { name: "zilla-mcp-proxy-tools-list-client", version: "0.0.1" },
        { capabilities: {} });

    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);
    log(`connected, protocolVersion=${transport.protocolVersion}`);

    try
    {
        if (CALL_TOOL)
        {
            const result = await client.callTool({ name: CALL_TOOL, arguments: CALL_ARGS });
            if (Array.isArray(result.structuredContent?.tools))
            {
                const isDigest = (tool) => Object.keys(tool).every((key) => key === "name" || key === "description");
                const describeTool = (tool) => isDigest(tool) ? tool.name : JSON.stringify(tool);
                console.log(result.structuredContent.tools.map(describeTool).join(" "));
                return;
            }
            const describe = (c) => c.text ?? JSON.stringify(c);
            console.log(result.content?.map(describe).join(" ") ?? "");
            return;
        }

        if (READ_RESOURCE)
        {
            const result = await client.readResource({ uri: READ_RESOURCE });
            for (const entry of result.contents ?? [])
            {
                console.log(entry.text ?? JSON.stringify(entry));
            }
            return;
        }

        const { tools } = await client.listTools();
        const { resources } = await client.listResources().catch(() => ({ resources: [] }));
        const { resourceTemplates } = await client.listResourceTemplates().catch(() => ({ resourceTemplates: [] }));

        for (const tool of tools)
        {
            console.log(tool.name);
        }
        for (const resource of resources)
        {
            console.log(`resource:${resource.uri}`);
        }
        for (const template of resourceTemplates)
        {
            console.log(`template:${template.uriTemplate}`);
        }
    }
    finally
    {
        await client.close();
    }
};

main().catch((err) =>
{
    log(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
