// Minimal mock Anthropic Messages API. Backs south_llm_client_anthropic, the
// upstream for the openai-facing frontend (north_llm_server_openai) -- so
// every request this receives already started life as an OpenAI-shaped
// /v1/chat/completions call that the llm binding translated into this
// Anthropic shape before forwarding it here.

import express from "express";

const PORT = Number(process.env.PORT ?? 4102);

const app = express();
app.use(express.json());

// south_llm_client_anthropic always issues its outbound request to "/",
// regardless of dialect -- it does not carry the dialect's own canonical
// path (e.g. /v1/messages) upstream.
app.post("/", (req, res) =>
{
    // Zilla's south_llm_client_anthropic forwards the caller's own credential
    // here via options.authorization pass-through; logged so verify.sh can
    // confirm it arrived unchanged.
    console.log(`x-api-key: ${req.headers["x-api-key"] ?? "(none)"}`);

    const { model, tools } = req.body ?? {};

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
