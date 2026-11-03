// Headless MCP client that connects through the Zilla mcp proxy. By default it
// lists every tool, resource, and resource template the caller can see, one per
// line: tools bare (petstore__list_pets), resources prefixed "resource:", and
// resource templates prefixed "template:". Set CALL_TOOL (with CALL_ARGS, a JSON
// object) to call one tool, or READ_RESOURCE to read one resource URI, and print
// the result instead. A tool or resource the caller is not authorized for is
// absent from the list entirely, never listed-but-denied.
//
// The caller's bearer token is JWT_TOKEN, or minted on the fly from the
// example's private.pem when JWT_SCOPE (a space-separated scope list) is set.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { mintJwt } from "./mint-jwt.mjs";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");
const READ_RESOURCE = process.env.READ_RESOURCE;

const token = process.env.JWT_TOKEN ?? (process.env.JWT_SCOPE ? await mintJwt(process.env.JWT_SCOPE) : undefined);
const headers = token ? { authorization: `Bearer ${token}` } : {};

const main = async () =>
{
    const client = new Client(
        { name: "zilla-mcp-openapi-proxy-client", version: "0.0.1" },
        { capabilities: {} });

    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);

    try
    {
        if (CALL_TOOL)
        {
            const result = await client.callTool({ name: CALL_TOOL, arguments: CALL_ARGS });
            const describe = (content) => content.text ?? JSON.stringify(content);
            console.log(result.content?.map(describe).join(" ") ?? "");
            if (result.structuredContent !== undefined)
            {
                console.log(JSON.stringify(result.structuredContent));
            }
            if (result.isError)
            {
                process.exitCode = 1;
            }
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
            const { resources } = await client.listResources().catch(() => ({ resources: [] }));
            const { resourceTemplates } = await client.listResourceTemplates().catch(() => ({ resourceTemplates: [] }));

            tools.forEach((tool) => console.log(tool.name));
            resources.forEach((resource) => console.log(`resource:${resource.uri}`));
            resourceTemplates.forEach((template) => console.log(`template:${template.uriTemplate}`));
        }
    }
    finally
    {
        await transport.terminateSession().catch(() => {});
        await client.close();
    }
};

main().catch((err) =>
{
    console.error(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
