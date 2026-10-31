// Prints one tool name per line, or, with CALL_TOOL (and CALL_ARGS, a JSON
// object), calls that tool and prints its text result, then its structured
// result as JSON if it has one. A tool error or a rejected call exits non-zero.
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_TOKEN = process.env.JWT_TOKEN;
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");

const headers = JWT_TOKEN ? { authorization: `Bearer ${JWT_TOKEN}` } : {};

const main = async () =>
{
    const client = new Client({ name: "zilla-mcp-client", version: "0.0.1" }, { capabilities: {} });
    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);

    try
    {
        if (CALL_TOOL)
        {
            const result = await client.callTool({ name: CALL_TOOL, arguments: CALL_ARGS });
            console.log(result.content?.map((c) => c.text ?? JSON.stringify(c)).join(" ") ?? "");
            if (result.structuredContent !== undefined)
            {
                console.log(JSON.stringify(result.structuredContent));
            }
            if (result.isError)
            {
                process.exitCode = 1;
            }
        }
        else
        {
            const { tools } = await client.listTools();
            tools.forEach((tool) => console.log(tool.name));
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
    console.error(`ERROR ${err.message ?? err}`);
    process.exitCode = 1;
});
