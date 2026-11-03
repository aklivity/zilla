// Headless MCP client for the Zilla mcp proxy. Mints a JWT for JWT_SCOPE (a
// space-separated `scope` claim, subject JWT_SUBJECT or "alice") with
// /private.pem, or presents JWT_TOKEN as is, or sends no credential at all
// when neither is set.
//
// Default mode lists tools, resources and resource templates, one per line:
// tools bare, resources prefixed "resource:", templates prefixed "template:".
// CALL_TOOL (with CALL_ARGS, a JSON object) calls one tool and prints its text
// content, then its structuredContent when present. READ_RESOURCE reads one
// concrete URI. A refused request prints "ERROR <message>" and exits non-zero.

import { readFileSync } from "node:fs";
import { createPrivateKey } from "node:crypto";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { SignJWT } from "jose";

const MCP_URL = process.env.MCP_URL ?? "http://localhost:7114/mcp";
const JWT_SCOPE = process.env.JWT_SCOPE;
const CALL_TOOL = process.env.CALL_TOOL;
const CALL_ARGS = JSON.parse(process.env.CALL_ARGS ?? "{}");
const READ_RESOURCE = process.env.READ_RESOURCE;

const mint = (scope) =>
    new SignJWT({ scope })
        .setProtectedHeader({ alg: "RS256", kid: "example" })
        .setSubject(process.env.JWT_SUBJECT ?? "alice")
        .setIssuer("https://auth.example.com")
        .setAudience("https://api.example.com")
        .setExpirationTime("1d")
        .sign(createPrivateKey(readFileSync(process.env.PRIVATE_KEY ?? "/private.pem")));

const main = async () =>
{
    const token = JWT_SCOPE ? await mint(JWT_SCOPE) : process.env.JWT_TOKEN;
    const headers = token ? { authorization: `Bearer ${token}` } : {};

    const client = new Client({ name: "mcp-http-proxy-client", version: "0.0.1" }, { capabilities: {} });
    const transport = new StreamableHTTPClientTransport(new URL(MCP_URL), { requestInit: { headers } });

    try
    {
        await client.connect(transport);

        if (CALL_TOOL)
        {
            const result = await client.callTool({ name: CALL_TOOL, arguments: CALL_ARGS });
            const text = result.content?.map((c) => c.text ?? JSON.stringify(c)).join(" ") ?? "";
            if (result.isError)
            {
                throw new Error(text);
            }
            console.log(text);
            if (result.structuredContent !== undefined)
            {
                console.log(JSON.stringify(result.structuredContent));
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
        await client.close().catch(() => {});
    }
};

main().catch((err) =>
{
    console.log(`ERROR ${err.message ?? err}`);
    process.exitCode = 1;
});
