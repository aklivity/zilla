// Minimal mock OpenAI Chat Completions API. Backs south_llm_client_openai,
// the upstream for the anthropic-facing frontend (north_llm_server_anthropic)
// -- so every request this receives already started life as an
// Anthropic-shaped /v1/messages call that the llm binding translated into
// this OpenAI shape before forwarding it here.

import express from "express";

const PORT = Number(process.env.PORT ?? 4101);

const app = express();
app.use(express.json());

const STREAM_MARKER = "[stream]";

function promptText(messages)
{
    return (messages ?? [])
        .map(m => typeof m.content === "string" ? m.content : (m.content ?? []).map(part => part.text ?? "").join(""))
        .join("\n");
}

function streamChatCompletion(res, model, message, includeUsage)
{
    res.set("Content-Type", "text/event-stream");
    res.set("Cache-Control", "no-cache");
    res.set("Connection", "keep-alive");

    const id = "chatcmpl_1";
    const created = Math.floor(Date.now() / 1000);
    const send = (delta, finishReason) => res.write(`data: ${JSON.stringify({
        id,
        object: "chat.completion.chunk",
        created,
        model,
        choices: [{ index: 0, delta, finish_reason: finishReason }]
    })}\n\n`);
    const pieces = text =>
    {
        const cut1 = Math.floor(text.length / 3);
        const cut2 = Math.floor(2 * text.length / 3);

        return [text.slice(0, cut1), text.slice(cut1, cut2), text.slice(cut2)];
    };

    send({ role: "assistant" }, null);

    if (message.tool_calls)
    {
        const { id: callId, function: { name, arguments: args } } = message.tool_calls[0];

        send({ tool_calls: [{ index: 0, id: callId, type: "function", function: { name, arguments: "" } }] }, null);
        pieces(args).forEach(piece => send({ tool_calls: [{ index: 0, function: { arguments: piece } }] }, null));
        send({}, "tool_calls");
    }
    else
    {
        pieces(message.content).forEach(piece => send({ content: piece }, null));
        send({}, "stop");
    }

    if (includeUsage)
    {
        res.write(`data: ${JSON.stringify({
            id,
            object: "chat.completion.chunk",
            created,
            model,
            choices: [],
            usage: { prompt_tokens: 25, completion_tokens: 15, total_tokens: 40 }
        })}\n\n`);
    }

    res.write("data: [DONE]\n\n");
    res.end();
}

app.post("/v1/chat/completions", (req, res) =>
{
    // Zilla's south_llm_client_openai forwards the caller's own credential
    // here via options.authorization pass-through; logged so verify.py can
    // confirm it arrived unchanged.
    console.log(`authorization: ${req.headers["authorization"] ?? "(none)"}`);

    const { model, tools, messages, stream_options: streamOptions } = req.body ?? {};

    const message = Array.isArray(tools) && tools.length > 0
        ? {
            role: "assistant",
            content: null,
            tool_calls: [
                {
                    id: "call_1",
                    type: "function",
                    function: {
                        name: "get_weather",
                        arguments: "{\"city\":\"Paris\"}"
                    }
                }
            ]
        }
        : {
            role: "assistant",
            content: "Hello! How can I help you today?"
        };

    // the response is streamed when the prompt asks for it, whatever the request's
    // own `stream` setting says, so the llm binding is shown to follow the
    // response's content type rather than the request
    if (promptText(messages).includes(STREAM_MARKER))
    {
        streamChatCompletion(res, model, message, streamOptions?.include_usage);
        return;
    }

    // res.json() sends "application/json; charset=utf-8", but the llm
    // binding looks up its response codec by an exact content-type match
    // against "application/json" -- the charset parameter would make that
    // lookup miss and silently drop the body.
    res.set("Content-Type", "application/json");
    res.send(JSON.stringify({
        object: "chat.completion",
        id: "chatcmpl_1",
        model,
        choices: [
            {
                index: 0,
                message,
                finish_reason: message.tool_calls ? "tool_calls" : "stop"
            }
        ],
        usage: { prompt_tokens: 25, completion_tokens: 15 }
    }));
});

app.listen(PORT, () => console.log(`mock-openai listening on ${PORT}`));
