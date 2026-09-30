// Minimal mock Anthropic Messages API. Backs south_llm_client_anthropic, the
// upstream for the openai-facing frontend (north_llm_server_openai) -- so
// every request this receives already started life as an OpenAI-shaped
// /v1/chat/completions call that the llm binding translated into this
// Anthropic shape before forwarding it here.

import express from "express";

const PORT = Number(process.env.PORT ?? 4102);

const app = express();
app.use(express.json());

function streamMessage(res, model, text)
{
    res.set("Content-Type", "text/event-stream");
    res.set("Cache-Control", "no-cache");
    res.set("Connection", "keep-alive");

    const send = (event, data) => res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);

    send("message_start", {
        type: "message_start",
        message: {
            id: "msg_01",
            type: "message",
            role: "assistant",
            model,
            content: [],
            stop_reason: null,
            stop_sequence: null,
            usage: { input_tokens: 25, output_tokens: 0 }
        }
    });
    send("content_block_start", { type: "content_block_start", index: 0, content_block: { type: "text", text: "" } });

    const cut1 = Math.floor(text.length / 3);
    const cut2 = Math.floor(2 * text.length / 3);
    for (const piece of [text.slice(0, cut1), text.slice(cut1, cut2), text.slice(cut2)])
    {
        send("content_block_delta", { type: "content_block_delta", index: 0, delta: { type: "text_delta", text: piece } });
    }

    send("content_block_stop", { type: "content_block_stop", index: 0 });
    send("message_delta", { type: "message_delta", delta: { stop_reason: "end_turn", stop_sequence: null }, usage: { output_tokens: 15 } });
    send("message_stop", { type: "message_stop" });
    res.end();
}

// south_llm_client_anthropic always issues its outbound request to "/",
// regardless of dialect -- it does not carry the dialect's own canonical
// path (e.g. /v1/messages) upstream.
app.post("/", (req, res) =>
{
    // Zilla's south_llm_client_anthropic forwards the caller's own credential
    // here via options.authorization pass-through; logged so verify.py can
    // confirm it arrived unchanged.
    console.log(`x-api-key: ${req.headers["x-api-key"] ?? "(none)"}`);

    const { model, tools, stream } = req.body ?? {};

    const content = Array.isArray(tools) && tools.length > 0
        ? [
            {
                type: "tool_use",
                id: "toolu_01",
                name: "get_weather",
                input: { city: "Paris" }
            }
        ]
        : [
            {
                type: "text",
                text: "Hello! How can I help you today?"
            }
        ];

    if (stream && content[0].type === "text")
    {
        streamMessage(res, model, content[0].text);
        return;
    }

    // res.json() sends "application/json; charset=utf-8", but the llm
    // binding looks up its response codec by an exact content-type match
    // against "application/json" -- the charset parameter would make that
    // lookup miss and silently drop the body.
    res.set("Content-Type", "application/json");
    res.send(JSON.stringify({
        id: "msg_01",
        type: "message",
        role: "assistant",
        model,
        content,
        stop_reason: content[0].type === "tool_use" ? "tool_use" : "end_turn",
        usage: { input_tokens: 25, output_tokens: 15 }
    }));
});

app.listen(PORT, () => console.log(`mock-anthropic listening on ${PORT}`));
