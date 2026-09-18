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

// south_llm_client_anthropic_secondary always issues its outbound request to
// "/", regardless of dialect -- it does not carry the dialect's own
// canonical path (e.g. /v1/messages) upstream.
app.post("/", (req, res) =>
{
    // Zilla's south_llm_client_anthropic_secondary forwards the caller's own
    // credential here via options.authorization pass-through; logged so
    // verify.sh can confirm it arrived unchanged.
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
                text: "Hello from the secondary anthropic-dialect deployment!"
            }
        ];

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
