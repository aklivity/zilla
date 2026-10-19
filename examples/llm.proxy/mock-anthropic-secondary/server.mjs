// Second mock Anthropic-dialect deployment. Backs
// south_llm_client_anthropic_secondary, which north_llm_proxy routes
// claude-3-5-haiku-20241022 requests to instead of translating them to
// mock-openai -- same dialect in as out, no translation on this leg at all.
// Stands in for a second real-world deployment of the same dialect (a
// regional fallback, a different hosting provider, etc.) chosen purely by
// model name.

import express from "express";

const PORT = Number(process.env.PORT ?? 4104);

const app = express();
app.use(express.json());

const STREAM_MARKER = "[stream]";

function promptText(messages)
{
    return (messages ?? [])
        .map(m => typeof m.content === "string" ? m.content : (m.content ?? []).map(part => part.text ?? "").join(""))
        .join("\n");
}

function streamMessage(res, model, block)
{
    res.set("Content-Type", "text/event-stream");
    res.set("Cache-Control", "no-cache");
    res.set("Connection", "keep-alive");

    const send = (event, data) => res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
    const pieces = text =>
    {
        const cut1 = Math.floor(text.length / 3);
        const cut2 = Math.floor(2 * text.length / 3);

        return [text.slice(0, cut1), text.slice(cut1, cut2), text.slice(cut2)];
    };

    send("message_start", {
        type: "message_start",
        message: {
            id: "msg_02",
            type: "message",
            role: "assistant",
            model,
            content: [],
            stop_reason: null,
            stop_sequence: null,
            usage: { input_tokens: 25, output_tokens: 0 }
        }
    });

    if (block.type === "tool_use")
    {
        send("content_block_start", {
            type: "content_block_start",
            index: 0,
            content_block: { type: "tool_use", id: block.id, name: block.name, input: {} }
        });
        pieces(JSON.stringify(block.input)).forEach(piece => send("content_block_delta", {
            type: "content_block_delta", index: 0, delta: { type: "input_json_delta", partial_json: piece }
        }));
    }
    else
    {
        send("content_block_start", { type: "content_block_start", index: 0, content_block: { type: "text", text: "" } });
        pieces(block.text).forEach(piece => send("content_block_delta", {
            type: "content_block_delta", index: 0, delta: { type: "text_delta", text: piece }
        }));
    }

    send("content_block_stop", { type: "content_block_stop", index: 0 });
    send("message_delta", {
        type: "message_delta",
        delta: { stop_reason: block.type === "tool_use" ? "tool_use" : "end_turn", stop_sequence: null },
        usage: { output_tokens: 15 }
    });
    send("message_stop", { type: "message_stop" });
    res.end();
}

app.post("/v1/messages", (req, res) =>
{
    // Zilla's south_llm_client_anthropic_secondary forwards the caller's own
    // credential here via options.authorization pass-through; logged so
    // verify.py can confirm it arrived unchanged.
    console.log(`x-api-key: ${req.headers["x-api-key"] ?? "(none)"}`);

    const { model, tools, messages } = req.body ?? {};

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
                text: "Hello from the secondary anthropic-dialect deployment!"
            }
        ];

    // the response is streamed when the prompt asks for it, whatever the request's
    // own `stream` setting says, so the llm binding is shown to follow the
    // response's content type rather than the request
    if (promptText(messages).includes(STREAM_MARKER))
    {
        streamMessage(res, model, content[0]);
        return;
    }

    // res.json() sends "application/json; charset=utf-8", but the llm
    // binding looks up its response codec by an exact content-type match
    // against "application/json" -- the charset parameter would make that
    // lookup miss and silently drop the body.
    res.set("Content-Type", "application/json");
    res.send(JSON.stringify({
        id: "msg_02",
        type: "message",
        role: "assistant",
        model,
        content,
        stop_reason: content[0].type === "tool_use" ? "tool_use" : "end_turn",
        usage: { input_tokens: 25, output_tokens: 15 }
    }));
});

app.listen(PORT, () => console.log(`mock-anthropic-secondary listening on ${PORT}`));
