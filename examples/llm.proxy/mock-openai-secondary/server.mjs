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

function streamChatCompletion(res, model, content)
{
    res.set("Content-Type", "text/event-stream");
    res.set("Cache-Control", "no-cache");
    res.set("Connection", "keep-alive");

    const id = "chatcmpl_2";
    const created = Math.floor(Date.now() / 1000);
    const send = (delta, finishReason) => res.write(`data: ${JSON.stringify({
        id,
        object: "chat.completion.chunk",
        created,
        model,
        choices: [{ index: 0, delta, finish_reason: finishReason }]
    })}\n\n`);

    const cut1 = Math.floor(content.length / 3);
    const cut2 = Math.floor(2 * content.length / 3);

    send({ role: "assistant" }, null);
    send({ content: content.slice(0, cut1) }, null);
    send({ content: content.slice(cut1, cut2) }, null);
    send({ content: content.slice(cut2) }, null);
    send({}, "stop");
    res.write("data: [DONE]\n\n");
    res.end();
}

app.post("/v1/chat/completions", (req, res) =>
{
    // Zilla's south_llm_client_openai_secondary forwards the caller's own
    // credential here via options.authorization pass-through; logged so
    // verify.py can confirm it arrived unchanged.
    console.log(`authorization: ${req.headers["authorization"] ?? "(none)"}`);

    const { model, tools, stream } = req.body ?? {};

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

    if (stream && typeof message.content === "string")
    {
        streamChatCompletion(res, model, message.content);
        return;
    }

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
