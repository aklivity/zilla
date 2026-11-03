// Lists the tools the Zilla mcp proxy advertises, one name per line. With CALL_TOOL
// (and optionally CALL_ARGS, a JSON object) it calls that tool instead and prints the
// result: search_tools matches as space-separated names, describe_tool as JSON,
// anything else as its text content.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");

const isDigest = (tool) => Object.keys(tool).every((key) => key === "name" || key === "description");
const describeTool = (tool) => isDigest(tool) ? tool.name : JSON.stringify(tool);
const describeContent = (content) => content.text ?? JSON.stringify(content);

const main = async () =>
{
    const client = new Client(
        { name: "zilla-mcp-proxy-search-tools-list-client", version: "0.0.1" },
        { capabilities: {} });
    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL));
    await client.connect(transport);

    try
    {
        if (CALL_TOOL)
        {
            const result = await client.callTool({ name: CALL_TOOL, arguments: CALL_ARGS });
            if (Array.isArray(result.structuredContent?.tools))
            {
                console.log(result.structuredContent.tools.map(describeTool).join(" "));
            }
            else
            {
                console.log(result.content?.map(describeContent).join(" ") ?? "");
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
    console.error(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
