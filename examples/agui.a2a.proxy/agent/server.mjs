// Minimal reference A2A agent (JSON-RPC over HTTP, no dependencies). Serves an agent card
// with streaming enabled, SendStreamingMessage and CancelTask. It keeps a context per
// contextId, so every reply says which context and turn it answered.
//
//   "ask: ..."   the task stops at INPUT_REQUIRED; a message carrying that taskId completes it
//   "slow: ..."  the reply streams slowly, so a client can abort the run while the task works
//   anything else is echoed back in two artifact chunks

import { createServer } from "node:http";

const PORT = Number(process.env.PORT ?? 8080);
const SLUG = process.env.AGENT_SLUG ?? "assistant";

const contexts = new Map();
const tasks = new Map();
const cancelled = [];
let contextSeq = 0;
let taskSeq = 0;

const card = {
    name: "Reference Agent",
    description: "Echoes messages and keeps a context per conversation",
    supportedInterfaces: [{ url: `http://agent:${PORT}/a2a/${SLUG}`, protocolBinding: "JSONRPC", protocolVersion: "1.0" }],
    version: "1.0.0",
    capabilities: { streaming: true },
    defaultInputModes: ["text/plain"],
    defaultOutputModes: ["text/plain"],
    skills: [{ id: "echo", name: "Echo", description: "Echoes the user message", tags: ["echo"] }]
};

const textOf = message => (message.parts ?? []).map(part => part.text ?? JSON.stringify(part.data ?? {})).join(" ");
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function emit(res, id, result)
{
    res.write(`data: ${JSON.stringify({ jsonrpc: "2.0", id, result })}\n\n`);
}

async function stream(res, id, message)
{
    const resumed = message.taskId ? tasks.get(message.taskId) : undefined;
    const contextId = resumed?.contextId ?? message.contextId ?? `ctx-${++contextSeq}`;
    const context = contexts.get(contextId) ?? { turns: 0 };
    contexts.set(contextId, context);
    context.turns++;

    const taskId = resumed?.taskId ?? `task-${++taskSeq}`;
    const task = resumed ?? { taskId, contextId, state: "TASK_STATE_WORKING" };
    tasks.set(taskId, task);

    const text = textOf(message);
    const status = (state, extra) => emit(res, id, { statusUpdate: { taskId, contextId, status: { state, ...extra } } });

    res.writeHead(200, { "content-type": "text/event-stream", "cache-control": "no-cache" });
    emit(res, id, { task: { id: taskId, contextId, status: { state: "TASK_STATE_SUBMITTED" } } });
    status("TASK_STATE_WORKING");

    if (!resumed && text.startsWith("ask:"))
    {
        task.state = "TASK_STATE_INPUT_REQUIRED";
        status("TASK_STATE_INPUT_REQUIRED",
            { message: { messageId: `msg-${taskId}-ask`, role: "ROLE_AGENT", parts: [{ text: "Which one do you mean?" }] } });
    }
    else
    {
        const reply = resumed
            ? `[${contextId} turn ${context.turns}] resumed ${taskId} with: ${text}`
            : `[${contextId} turn ${context.turns}] you said: ${text}`;
        const chunks = [reply.slice(0, 12), reply.slice(12)];
        const pause = text.startsWith("slow:") ? 1000 : 0;

        for (let i = 0; i < chunks.length && task.state !== "TASK_STATE_CANCELED"; i++)
        {
            await sleep(pause);
            if (task.state !== "TASK_STATE_CANCELED")
            {
                emit(res, id, { artifactUpdate: { taskId, contextId, append: i > 0, lastChunk: i === chunks.length - 1,
                    artifact: { artifactId: `art-${taskId}-${context.turns}`, parts: [{ text: chunks[i] }] } } });
            }
        }
        if (task.state !== "TASK_STATE_CANCELED")
        {
            task.state = "TASK_STATE_COMPLETED";
            status("TASK_STATE_COMPLETED");
        }
    }
    res.end();
}

function route(req, res, body)
{
    const path = new URL(req.url, "http://agent").pathname;

    if (req.method === "GET" && path.endsWith("/.well-known/agent-card.json"))
    {
        res.writeHead(200, { "content-type": "application/json" });
        res.end(JSON.stringify(card));
    }
    else if (req.method === "GET" && path === "/cancelled")
    {
        res.writeHead(200, { "content-type": "application/json" });
        res.end(JSON.stringify(cancelled));
    }
    else if (req.method === "POST" && path === `/a2a/${SLUG}`)
    {
        const { id, method, params } = JSON.parse(body);

        if (method === "SendStreamingMessage")
        {
            stream(res, id, params.message);
        }
        else if (method === "CancelTask" && tasks.has(params.id))
        {
            const task = tasks.get(params.id);
            task.state = "TASK_STATE_CANCELED";
            cancelled.push(task.taskId);
            res.writeHead(200, { "content-type": "application/json" });
            res.end(JSON.stringify({ jsonrpc: "2.0", id,
                result: { id: task.taskId, contextId: task.contextId, status: { state: task.state } } }));
        }
        else
        {
            res.writeHead(200, { "content-type": "application/json" });
            res.end(JSON.stringify({ jsonrpc: "2.0", id, error: { code: -32601, message: "Method not found" } }));
        }
    }
    else
    {
        res.writeHead(404);
        res.end();
    }
}

createServer((req, res) =>
{
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => route(req, res, body));
}).listen(PORT, () => console.log(`reference agent listening on ${PORT}`));
