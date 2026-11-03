import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_TOKEN = process.env.JWT_TOKEN;
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");
const CALL_TIMEOUT_MS = Number(process.env.CALL_TIMEOUT_MS ?? 60000);

const headers = JWT_TOKEN ? { authorization: `Bearer ${JWT_TOKEN}` } : {};

const main = async () =>
{
    const client = new Client({ name: "mcp-proxy-jwt-list-client", version: "0.0.1" }, { capabilities: {} });
    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });
    await client.connect(transport);

    try
    {
        if (CALL_TOOL)
        {
            const result = await client.callTool(
                { name: CALL_TOOL, arguments: CALL_ARGS }, undefined, { timeout: CALL_TIMEOUT_MS });
            console.log(result.content?.map((c) => c.text ?? JSON.stringify(c)).join(" ") ?? "");
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
    console.error(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
