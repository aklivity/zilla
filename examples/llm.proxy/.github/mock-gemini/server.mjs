// Minimal mock Gemini generateContent API. Backs south_llm_client_gemini, the
// upstream for every gemini-* model: a request reaches it either from the
// gemini-facing frontend (north_llm_server_gemini) unchanged, or from the
// openai- and anthropic-facing frontends translated into this Gemini shape by
// the llm binding.

import express from "express";

const PORT = Number(process.env.PORT ?? 4105);

const app = express();
app.use(express.json());

const STREAM_MARKER = "[stream]";

function promptText(contents)
{
    return (contents ?? [])
        .flatMap(content => (content.parts ?? []).map(part => part.text ?? ""))
        .join("\n");
}

function usage(candidates)
{
    return { promptTokenCount: 25, candidatesTokenCount: candidates, totalTokenCount: 25 + candidates };
}

function envelope(model, part, finishReason)
{
    const candidate = { content: { role: "model", parts: [part] }, index: 0 };

    if (finishReason)
    {
        candidate.finishReason = finishReason;
    }

    return { candidates: [candidate], modelVersion: model, responseId: "resp_01" };
}

function pieces(text)
{
    const cut1 = Math.floor(text.length / 3);
    const cut2 = Math.floor(2 * text.length / 3);

    return [text.slice(0, cut1), text.slice(cut1, cut2), text.slice(cut2)];
}

function chunks(model, part)
{
    let events;

    if (part.text !== undefined)
    {
        const last = envelope(model, { text: "" }, "STOP");

        last.usageMetadata = usage(15);
        events = [...pieces(part.text).map(text => envelope(model, { text })), last];
    }
    else
    {
        const only = envelope(model, part, "STOP");

        only.usageMetadata = usage(15);
        events = [only];
    }

    return events;
}

function streamSse(res, model, part)
{
    res.set("Content-Type", "text/event-stream");
    res.set("Cache-Control", "no-cache");
    res.set("Connection", "keep-alive");

    chunks(model, part).forEach(chunk => res.write(`data: ${JSON.stringify(chunk)}\r\n\r\n`));
    res.end();
}

app.post("/v1beta/models/:target", (req, res) =>
{
    // Zilla's south_llm_client_gemini forwards the caller's own credential here
    // via options.authorization pass-through; logged so verify.py can confirm it
    // arrived unchanged.
    console.log(`x-goog-api-key: ${req.headers["x-goog-api-key"] ?? "(none)"}`);

    const target = req.params.target;
    const colon = target.lastIndexOf(":");
    const model = target.slice(0, colon);
    const method = target.slice(colon + 1);
    const { tools, contents } = req.body ?? {};

    const declared = (tools ?? []).some(tool => (tool.functionDeclarations ?? []).length > 0);
    const part = declared
        ? { functionCall: { name: "get_weather", args: { city: "Paris" }, id: "call_1" } }
        : { text: "Hello! How can I help you today?" };

    // the response is streamed when the prompt asks for it, whatever the
    // operation says, so the llm binding is shown to follow the response's
    // content type rather than the request
    if (promptText(contents).includes(STREAM_MARKER) || method === "streamGenerateContent" && req.query.alt === "sse")
    {
        streamSse(res, model, part);
        return;
    }

    // res.json() sends "application/json; charset=utf-8", but the llm binding
    // looks up its response codec by an exact content-type match against
    // "application/json" -- the charset parameter would make that lookup miss.
    res.set("Content-Type", "application/json");
    res.send(JSON.stringify({
        ...envelope(model, part, "STOP"),
        usageMetadata: usage(15)
    }));
});

app.listen(PORT, () => console.log(`mock-gemini listening on ${PORT}`));
