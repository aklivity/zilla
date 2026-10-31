// Headless MCP client for the Zilla mcp proxy. With no CALL_TOOL it lists every
// tool, one name per line. With CALL_TOOL (and optionally CALL_ARGS, a JSON object)
// it calls that tool and prints its text content, followed by a second line with
// the raw structuredContent JSON when the result carries one. JWT_TOKEN, when set,
// is presented as a bearer token so the result reflects the caller's scopes.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_TOKEN = process.env.JWT_TOKEN;
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");

const headers = JWT_TOKEN ? { authorization: `Bearer ${JWT_TOKEN}` } : {};

const main = async () =>
{
    const client = new Client(
        { name: "zilla-mcp-kafka-proxy-tools-list-client", version: "0.0.1" },
        { capabilities: {} });

    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);

    try
    {
        if (CALL_TOOL)
        {
            const result = await client.callTool({ name: CALL_TOOL, arguments: CALL_ARGS });
            const describe = (c) => c.text ?? JSON.stringify(c);
            console.log(result.content?.map(describe).join(" ") ?? "");
            if (result.structuredContent !== undefined)
            {
                console.log(JSON.stringify(result.structuredContent));
            }
            if (result.isError)
            {
                process.exitCode = 1;
            }
            return;
        }

        const { tools } = await client.listTools();
        for (const tool of tools)
        {
            console.log(tool.name);
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
