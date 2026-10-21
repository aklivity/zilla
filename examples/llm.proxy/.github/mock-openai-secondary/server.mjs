// Second mock OpenAI-dialect deployment. Backs south_llm_client_openai_secondary,
// which north_llm_proxy routes gpt-4o requests to instead of translating them
// to mock-anthropic -- same dialect in as out, no translation on this leg at
// all. Stands in for a second real-world deployment of the same dialect (a
// regional fallback, a self-hosted OpenAI-compatible model server, etc.)
// chosen purely by model name. Serves both OpenAI APIs, Chat Completions and
// Responses, as a second real-world deployment of the vendor would.

import express from "express";

const PORT = Number(process.env.PORT ?? 4103);

const app = express();
app.use(express.json());

const STREAM_MARKER = "[stream]";

function promptText(messages)
{
    return (messages ?? [])
        .map(m => typeof m.content === "string" ? m.content : (m.content ?? []).map(part => part.text ?? "").join(""))
        .join("\n");
}

function logReceived(path, req, prompt, roles)
{
    console.log(`authorization: ${req.headers["authorization"] ?? "(none)"}`);
    console.log(
        `received ${path} members=${Object.keys(req.body ?? {}).join(",")} roles=${roles.join(",")} ` +
        `prompt=${JSON.stringify(prompt)}`);
}

function streamChatCompletion(res, model, message, includeUsage)
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
    // Zilla's south_llm_client_openai_secondary forwards the caller's own
    // credential here via options.authorization pass-through; logged so
    // verify.py can confirm it arrived unchanged.
    const { model, tools, messages, stream_options: streamOptions } = req.body ?? {};
    const lastUser = [...(messages ?? [])].reverse().find(m => m.role === "user");

    logReceived(
        "/v1/chat/completions", req, lastUser === undefined ? "" : promptText([lastUser]), (messages ?? []).map(m => m.role));

    // a request that asks for the private key is answered with one, so the
    // response guardrail on gpt-4o has something to reject
    const askedForKey = typeof lastUser?.content === "string" && lastUser.content.includes("private key");

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
            content: askedForKey
                ? "-----BEGIN PRIVATE KEY-----"
                : "Hello from the secondary openai-dialect deployment!"
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

const RESPONSES_USAGE = {
    input_tokens: 25,
    input_tokens_details: { cache_write_tokens: 0, cached_tokens: 0 },
    output_tokens: 15,
    output_tokens_details: { reasoning_tokens: 0 },
    total_tokens: 40
};

const OBFUSCATION_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

function inputItems(input)
{
    return typeof input === "string" ? [{ role: "user", content: input }] : (input ?? []);
}

function itemText(item)
{
    const content = item.content ?? item.output ?? "";

    return typeof content === "string" ? content : content.map(part => part.text ?? "").join("");
}

function responsesPrompt(request, items)
{
    return [request.instructions ?? "", ...items.map(itemText)].join("\n");
}

function responsesOutput(request, items)
{
    const toolResult = items.find(item => item.type === "function_call_output");
    const lastUser = [...items].reverse().find(item => item.role === "user");
    const askedForKey = lastUser !== undefined && itemText(lastUser).includes("private key");
    let output;

    if (Array.isArray(request.tools) && request.tools.length > 0 && toolResult === undefined)
    {
        output = {
            id: "fc_2",
            type: "function_call",
            status: "completed",
            arguments: "{\"city\":\"Paris\"}",
            call_id: "call_1",
            name: "get_weather"
        };
    }
    else
    {
        const text = toolResult !== undefined
            ? `The tool returned: ${itemText(toolResult)}`
            : askedForKey
                ? "-----BEGIN PRIVATE KEY-----"
                : "Hello from the secondary openai-dialect deployment!";

        const content = request.instructions ? `[${request.instructions}] ${text}` : text;

        output = {
            id: "msg_2",
            type: "message",
            status: "completed",
            content: [{ type: "output_text", annotations: [], logprobs: [], text: content }],
            role: "assistant"
        };
    }

    return [output];
}

// echoes the request members a caller can observe in the response, as the real
// API does, so a member that crossed the llm binding intact shows up here and
// one the binding did not carry to this deployment does not
function responseObject(request, status, output, usage)
{
    return {
        id: "resp_2",
        object: "response",
        created_at: 1792445323,
        status,
        background: false,
        error: null,
        incomplete_details: null,
        instructions: request.instructions ?? null,
        max_output_tokens: request.max_output_tokens ?? null,
        metadata: request.metadata ?? {},
        model: request.model,
        output,
        parallel_tool_calls: true,
        previous_response_id: null,
        store: request.store ?? true,
        temperature: request.temperature ?? 1.0,
        tool_choice: "auto",
        tools: request.tools ?? [],
        top_p: 1.0,
        truncation: "disabled",
        usage,
        user: request.user ?? null
    };
}

function streamResponse(res, request, output, obfuscate)
{
    res.set("Content-Type", "text/event-stream");
    res.set("Cache-Control", "no-cache");
    res.set("Connection", "keep-alive");

    let sequenceNumber = 0;
    const send = (type, data) => res.write(
        `event: ${type}\ndata: ${JSON.stringify({ type, ...data, sequence_number: sequenceNumber++ })}\n\n`);
    const noise = () => obfuscate
        ? {
            obfuscation: Array.from(
                { length: 4 + Math.floor(Math.random() * 28) },
                () => OBFUSCATION_ALPHABET[Math.floor(Math.random() * OBFUSCATION_ALPHABET.length)]).join("")
        }
        : {};
    const pieces = text =>
    {
        const cut1 = Math.floor(text.length / 3);
        const cut2 = Math.floor(2 * text.length / 3);

        return [text.slice(0, cut1), text.slice(cut1, cut2), text.slice(cut2)];
    };

    const [item] = output;

    send("response.created", { response: responseObject(request, "in_progress", [], null) });
    send("response.in_progress", { response: responseObject(request, "in_progress", [], null) });

    if (item.type === "function_call")
    {
        send("response.output_item.added", { item: { ...item, status: "in_progress", arguments: "" }, output_index: 0 });
        pieces(item.arguments).forEach(piece => send("response.function_call_arguments.delta", {
            delta: piece, item_id: item.id, ...noise(), output_index: 0
        }));
        send("response.function_call_arguments.done", { arguments: item.arguments, item_id: item.id, output_index: 0 });
    }
    else
    {
        const { text } = item.content[0];

        send("response.output_item.added", { item: { ...item, status: "in_progress", content: [] }, output_index: 0 });
        send("response.content_part.added", {
            content_index: 0, item_id: item.id, output_index: 0,
            part: { type: "output_text", annotations: [], logprobs: [], text: "" }
        });
        pieces(text).forEach(piece => send("response.output_text.delta", {
            content_index: 0, delta: piece, item_id: item.id, logprobs: [], ...noise(), output_index: 0
        }));
        send("response.output_text.done", { content_index: 0, item_id: item.id, logprobs: [], output_index: 0, text });
        send("response.content_part.done", {
            content_index: 0, item_id: item.id, output_index: 0, part: item.content[0]
        });
    }

    send("response.output_item.done", { item, output_index: 0 });
    send("response.completed", { response: responseObject(request, "completed", output, RESPONSES_USAGE) });
    res.end();
}

app.post("/v1/responses", (req, res) =>
{
    const request = req.body ?? {};
    const items = inputItems(request.input);
    const lastUser = [...items].reverse().find(item => item.role === "user");

    // Zilla's south_llm_client_openai forwards the caller's own
    // credential here via options.authorization pass-through; logged so
    // verify.py can confirm it arrived unchanged.
    logReceived("/v1/responses", req, lastUser === undefined ? "" : itemText(lastUser),
        [request.instructions ? "system" : null, ...items.map(item => item.role ?? item.type)].filter(Boolean));

    if (String(request.model).startsWith("no-such-model"))
    {
        res.status(404).set("Content-Type", "application/json");
        res.send(JSON.stringify({
            error: {
                message: `The model \`${request.model}\` does not exist or you do not have access to it.`,
                type: "invalid_request_error",
                param: null,
                code: "model_not_found"
            }
        }));
        return;
    }

    const output = responsesOutput(request, items);

    // the response is streamed when the request says so or the prompt asks for
    // it, whatever the request's own `stream` setting says, so the llm binding
    // is shown to follow the response's content type rather than the request
    if (request.stream === true || responsesPrompt(request, items).includes(STREAM_MARKER))
    {
        streamResponse(res, request, output, request.stream_options?.include_obfuscation !== false);
        return;
    }

    res.set("Content-Type", "application/json");
    res.send(JSON.stringify(responseObject(request, "completed", output, RESPONSES_USAGE)));
});

app.listen(PORT, () => console.log(`mock-openai-secondary listening on ${PORT}`));
