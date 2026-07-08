// Headless MCP client that connects through the Zilla mcp proxy and prints
// the name of every tool returned by tools/list, one per line. Optionally
// carries a bearer token on the initial request so .github/test.sh can
// observe how the result set changes with the caller's authorized scopes --
// unauthorized toolkits and tools are absent from the list entirely, never
// present-but-marked-denied.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_TOKEN = process.env.JWT_TOKEN;

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

    const { tools } = await client.listTools();
    await client.close();

    for (const tool of tools)
    {
        console.log(tool.name);
    }
};

main().catch((err) =>
{
    log(`FAIL ${err.message ?? err}`);
    process.exitCode = 1;
});
