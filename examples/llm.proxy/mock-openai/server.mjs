// Minimal mock OpenAI Chat Completions API. Backs south_llm_client_openai,
// the upstream for the anthropic-facing frontend (north_llm_server_anthropic)
// -- so every request this receives already started life as an
// Anthropic-shaped /v1/messages call that the llm binding translated into
// this OpenAI shape before forwarding it here.

import express from "express";

const PORT = Number(process.env.PORT ?? 4101);

const app = express();
app.use(express.json());

app.post("/v1/chat/completions", (req, res) =>
{
    // Zilla's south_llm_client_openai forwards the caller's own credential
    // here via options.authorization pass-through; logged so verify.sh can
    // confirm it arrived unchanged.
    console.log(`authorization: ${req.headers["authorization"] ?? "(none)"}`);

    const { model, tools } = req.body ?? {};

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

    res.json({
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
    });
});

app.listen(PORT, () => console.log(`mock-openai listening on ${PORT}`));
