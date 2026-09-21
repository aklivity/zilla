// Second mock OpenAI-dialect deployment. Backs south_llm_client_openai_secondary,
// which north_llm_proxy routes gpt-4o requests to instead of translating them
// to mock-anthropic -- same dialect in as out, no translation on this leg at
// all. Stands in for a second real-world deployment of the same dialect (a
// regional fallback, a self-hosted OpenAI-compatible model server, etc.)
// chosen purely by model name.

import express from "express";

const PORT = Number(process.env.PORT ?? 4103);

const app = express();
app.use(express.json());

app.post("/v1/chat/completions", (req, res) =>
{
    // Zilla's south_llm_client_openai_secondary forwards the caller's own
    // credential here via options.authorization pass-through; logged so
    // verify.sh can confirm it arrived unchanged.
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
            content: "Hello from the secondary openai-dialect deployment!"
        };

    // res.json() sends "application/json; charset=utf-8", but the llm
    // binding looks up its response codec by an exact content-type match
    // against "application/json" -- the charset parameter would make that
    // lookup miss and silently drop the body.
    res.set("Content-Type", "application/json");
    res.send(JSON.stringify({
        object: "chat.completion",
        id: "chatcmpl_2",
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

app.listen(PORT, () => console.log(`mock-openai-secondary listening on ${PORT}`));
