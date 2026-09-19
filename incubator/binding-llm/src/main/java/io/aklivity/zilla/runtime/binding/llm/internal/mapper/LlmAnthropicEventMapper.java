/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.internal.mapper;

import static io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmEventMapperJson.compact;
import static io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmEventMapperJson.getString;
import static io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmEventMapperJson.orDefault;
import static io.aklivity.zilla.runtime.binding.llm.internal.mapper.LlmEventMapperJson.readObject;
import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Translates between Anthropic's native streaming event sequence and the canonical vocabulary, in
 * both directions, genuinely against {@code common-json}: {@code decode} drives a {@link
 * JsonParserEx} directly over each native event's data (a fresh, self-contained document every
 * call), walking it via {@link #walk(String, FieldSink)} -- a shallow, dotted-path field reader,
 * since none of the fields this dialect's events carry sit inside an array; {@code encode*} writes
 * native JSON directly with a {@link JsonGeneratorEx}.
 * <p>
 * Holds per-stream state (input token count, the currently open block's type, held output tokens
 * pending a {@code finish}) so a fresh instance is required per stream; instances are not shared
 * across streams.
 */
public final class LlmAnthropicEventMapper implements LlmEventMapper
{
    private static final int GENERATOR_BUFFER_CAPACITY = 8192;

    // decode-direction: parses a fresh, self-contained document per decode() call
    private final JsonParserEx parser;

    // encode-direction: writes a fresh, self-contained native document per encode*() call
    private final JsonGeneratorEx generator;
    private final MutableDirectBufferEx generatorBuffer;

    private int inputTokens = -1;
    private LlmCanonicalBlockKind openBlockType;
    private int openBlockId;

    private int heldOutputTokens = -1;
    private boolean finishSent;

    public LlmAnthropicEventMapper()
    {
        this.parser = JsonEx.createParser();
        this.generator = JsonEx.createGenerator();
        this.generatorBuffer = new UnsafeBufferEx(new byte[GENERATOR_BUFFER_CAPACITY]);
    }

    @Override
    public void decode(
        String event,
        String data,
        LlmCanonicalOutput output)
    {
        switch (event)
        {
        case "message_start":
            onMessageStart(data, output);
            break;
        case "content_block_start":
            onContentBlockStart(data, output);
            break;
        case "content_block_delta":
            onContentBlockDelta(data, output);
            break;
        case "content_block_stop":
            onContentBlockStop(data, output);
            break;
        case "message_delta":
            onMessageDelta(data, output);
            break;
        case "message_stop":
            output.end();
            break;
        default:
            break;
        }
    }

    @Override
    public void encode(
        DirectBuffer buffer,
        int offset,
        int length,
        LlmNativeEventOutput output)
    {
        String text = buffer.getStringWithoutLengthUtf8(offset, length);
        boolean toolCall = openBlockType == LlmCanonicalBlockKind.TOOL_CALL;

        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("type", "content_block_delta");
        json.write("index", openBlockId);
        json.writeStartObject("delta");
        if (toolCall)
        {
            json.write("type", "input_json_delta");
            json.write("partial_json", text);
        }
        else
        {
            json.write("type", "text_delta");
            json.write("text", text);
        }
        json.writeEnd();
        json.writeEnd();

        emit("content_block_delta", json.length(), output);
    }

    @Override
    public void encodeMessageStart(
        int choiceIndex,
        String id,
        String model,
        String role,
        LlmNativeEventOutput output)
    {
        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("type", "message_start");
        json.writeStartObject("message");
        json.write("id", id);
        json.write("type", "message");
        json.write("role", orDefault(role, "assistant"));
        if (model != null)
        {
            json.write("model", model);
        }
        json.writeEnd();
        json.writeEnd();

        emit("message_start", json.length(), output);
    }

    @Override
    public void encodeBlockStart(
        int choiceIndex,
        int blockId,
        LlmCanonicalBlockKind type,
        String toolId,
        String toolName,
        LlmNativeEventOutput output)
    {
        openBlockType = type;
        openBlockId = blockId;

        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("type", "content_block_start");
        json.write("index", blockId);
        json.writeStartObject("content_block");
        if (type == LlmCanonicalBlockKind.TOOL_CALL)
        {
            json.write("type", "tool_use");
            if (toolId != null)
            {
                json.write("id", toolId);
            }
            if (toolName != null)
            {
                json.write("name", toolName);
            }
        }
        else
        {
            json.write("type", "text");
            json.write("text", "");
        }
        json.writeEnd();
        json.writeEnd();

        emit("content_block_start", json.length(), output);
    }

    @Override
    public void encodeBlockEnd(
        int choiceIndex,
        int blockId,
        LlmNativeEventOutput output)
    {
        openBlockType = null;

        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("type", "content_block_stop");
        json.write("index", blockId);
        json.writeEnd();

        emit("content_block_stop", json.length(), output);
    }

    @Override
    public void encodeFinish(
        int choiceIndex,
        LlmCanonicalFinishReason reason,
        LlmNativeEventOutput output)
    {
        String stopReason = stopReason(reason);
        int outputTokens = Math.max(heldOutputTokens, 0);

        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("type", "message_delta");
        json.writeStartObject("delta");
        json.write("stop_reason", stopReason);
        json.writeEnd();
        json.writeStartObject("usage");
        json.write("output_tokens", outputTokens);
        json.writeEnd();
        json.writeEnd();

        emit("message_delta", json.length(), output);
        finishSent = true;
    }

    @Override
    public void encodeUsage(
        int inputTokens,
        int outputTokens,
        LlmNativeEventOutput output)
    {
        if (!finishSent)
        {
            heldOutputTokens = outputTokens;
        }
    }

    @Override
    public void encodeEnd(
        LlmNativeEventOutput output)
    {
        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("type", "message_stop");
        json.writeEnd();

        emit("message_stop", json.length(), output);
    }

    @Override
    public JsonObject decodeMessage(
        String data)
    {
        JsonObject root = readObject(data);
        JsonArray blocks = root.getJsonArray("content");

        JsonArrayBuilder content = Json.createArrayBuilder();
        if (blocks != null)
        {
            for (int i = 0; i < blocks.size(); i++)
            {
                JsonObject block = blocks.getJsonObject(i);
                if ("tool_use".equals(getString(block, "type", null)))
                {
                    JsonObject input = block.getJsonObject("input");
                    JsonObjectBuilder canonicalBlock = Json.createObjectBuilder().add("type", "tool_call");
                    addIfPresent(canonicalBlock, "toolId", getString(block, "id", null));
                    addIfPresent(canonicalBlock, "toolName", getString(block, "name", null));
                    canonicalBlock.add("arguments", input != null ? compact(input) : "{}");
                    content.add(canonicalBlock);
                }
                else
                {
                    content.add(Json.createObjectBuilder().add("type", "text").add("text", getString(block, "text", "")));
                }
            }
        }

        JsonObject usage = root.getJsonObject("usage");

        JsonObjectBuilder canonical = Json.createObjectBuilder();
        addIfPresent(canonical, "id", getString(root, "id", null));
        addIfPresent(canonical, "model", getString(root, "model", null));
        canonical.add("role", orDefault(getString(root, "role", null), "assistant"));
        canonical.add("content", content);
        canonical.add("finishReason", finishReason(getString(root, "stop_reason", null)).name());
        canonical.add("usage", Json.createObjectBuilder()
            .add("inputTokens", usage != null ? usage.getInt("input_tokens", -1) : -1)
            .add("outputTokens", usage != null ? usage.getInt("output_tokens", -1) : -1));

        return canonical.build();
    }

    @Override
    public String encodeMessage(
        JsonObject message)
    {
        JsonArray content = message.getJsonArray("content");
        JsonArrayBuilder blocks = Json.createArrayBuilder();

        for (int i = 0; i < content.size(); i++)
        {
            JsonObject block = content.getJsonObject(i);
            if ("tool_call".equals(block.getString("type")))
            {
                JsonObjectBuilder toolUse = Json.createObjectBuilder().add("type", "tool_use");
                addIfPresent(toolUse, "id", getString(block, "toolId", null));
                addIfPresent(toolUse, "name", getString(block, "toolName", null));
                String arguments = getString(block, "arguments", "");
                toolUse.add("input", arguments.isEmpty() ? Json.createObjectBuilder().build() : readObject(arguments));
                blocks.add(toolUse);
            }
            else
            {
                blocks.add(Json.createObjectBuilder().add("type", "text").add("text", getString(block, "text", "")));
            }
        }

        JsonObjectBuilder root = Json.createObjectBuilder();
        addIfPresent(root, "id", getString(message, "id", null));
        root.add("type", "message");
        root.add("role", getString(message, "role", "assistant"));
        addIfPresent(root, "model", getString(message, "model", null));
        root.add("content", blocks);
        root.add("stop_reason", stopReason(LlmCanonicalFinishReason.valueOf(message.getString("finishReason"))));

        JsonObject usage = message.getJsonObject("usage");
        root.add("usage", Json.createObjectBuilder()
            .add("input_tokens", usage != null ? usage.getInt("inputTokens", -1) : -1)
            .add("output_tokens", usage != null ? usage.getInt("outputTokens", -1) : -1));

        return compact(root.build());
    }

    private void onMessageStart(
        String data,
        LlmCanonicalOutput output)
    {
        Fields fields = new Fields();
        walk(data, (path, event) ->
        {
            switch (path)
            {
            case "message.id":
                fields.id = parser.getString();
                break;
            case "message.model":
                fields.model = parser.getString();
                break;
            case "message.role":
                fields.role = parser.getString();
                break;
            case "message.usage.input_tokens":
                fields.inputTokens = parser.getInt();
                break;
            default:
                break;
            }
        });

        inputTokens = fields.inputTokens;
        output.messageStart(0, fields.id, fields.model, fields.role);
    }

    private void onContentBlockStart(
        String data,
        LlmCanonicalOutput output)
    {
        Fields fields = new Fields();
        walk(data, (path, event) ->
        {
            switch (path)
            {
            case "index":
                fields.blockId = parser.getInt();
                break;
            case "content_block.type":
                fields.blockType = parser.getString();
                break;
            case "content_block.id":
                fields.toolId = parser.getString();
                break;
            case "content_block.name":
                fields.toolName = parser.getString();
                break;
            default:
                break;
            }
        });

        boolean toolCall = "tool_use".equals(fields.blockType);
        openBlockType = toolCall ? LlmCanonicalBlockKind.TOOL_CALL : LlmCanonicalBlockKind.TEXT;

        if (toolCall)
        {
            output.blockStart(0, fields.blockId, LlmCanonicalBlockKind.TOOL_CALL, fields.toolId, fields.toolName);
        }
    }

    private void onContentBlockDelta(
        String data,
        LlmCanonicalOutput output)
    {
        Fields fields = new Fields();
        walk(data, (path, event) ->
        {
            switch (path)
            {
            case "delta.type":
                fields.blockType = parser.getString();
                break;
            case "delta.text":
                fields.content = parser.getString();
                break;
            case "delta.partial_json":
                fields.toolCallArguments = parser.getString();
                break;
            default:
                break;
            }
        });

        boolean toolCall = "input_json_delta".equals(fields.blockType);
        String content = toolCall ? orDefault(fields.toolCallArguments, "") : orDefault(fields.content, "");
        emitData(content, output);
    }

    private void onContentBlockStop(
        String data,
        LlmCanonicalOutput output)
    {
        if (openBlockType == LlmCanonicalBlockKind.TOOL_CALL)
        {
            Fields fields = new Fields();
            walk(data, (path, event) ->
            {
                if ("index".equals(path))
                {
                    fields.blockId = parser.getInt();
                }
            });

            output.blockEnd(0, fields.blockId);
        }

        openBlockType = null;
    }

    private void onMessageDelta(
        String data,
        LlmCanonicalOutput output)
    {
        Fields fields = new Fields();
        walk(data, (path, event) ->
        {
            switch (path)
            {
            case "delta.stop_reason":
                fields.finishReason = event == JsonEvent.VALUE_STRING ? parser.getString() : null;
                break;
            case "usage.output_tokens":
                fields.outputTokens = parser.getInt();
                break;
            default:
                break;
            }
        });

        output.finish(0, finishReason(fields.finishReason));
        output.usage(inputTokens, fields.outputTokens);
    }

    private void emitData(
        String content,
        LlmCanonicalOutput output)
    {
        byte[] bytes = content.getBytes(UTF_8);
        output.data(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private void emit(
        String name,
        int length,
        LlmNativeEventOutput output)
    {
        output.event(name, generatorBuffer, 0, length);
    }

    // Walks the whole native event document, calling sink once for every scalar (or null) value with
    // the dotted path of object keys leading to it (root-level fields have no dot). None of this
    // dialect's needed fields sit inside an array, so this shallow, path-based reader -- rather than
    // OpenAI's index-tracking walk -- is all decode needs: no depth/position bookkeeping to get wrong.
    private void walk(
        String data,
        FieldSink sink)
    {
        byte[] bytes = data.getBytes(UTF_8);
        parser.reset();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        StringBuilder path = new StringBuilder();
        int[] pathLengthAt = new int[16];
        int depth = 0;
        String pendingKey = null;

        JsonEvent event;
        while ((event = parser.nextEvent()) != null)
        {
            switch (event)
            {
            case KEY_NAME:
                pendingKey = parser.getString();
                break;
            case START_OBJECT:
            case START_ARRAY:
                pathLengthAt[depth++] = path.length();
                if (pendingKey != null)
                {
                    if (path.length() > 0)
                    {
                        path.append('.');
                    }
                    path.append(pendingKey);
                    pendingKey = null;
                }
                break;
            case END_OBJECT:
            case END_ARRAY:
                path.setLength(pathLengthAt[--depth]);
                break;
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                if (pendingKey != null)
                {
                    int fieldAt = path.length();
                    if (fieldAt > 0)
                    {
                        path.append('.');
                    }
                    path.append(pendingKey);
                    sink.onField(path.toString(), event);
                    path.setLength(fieldAt);
                    pendingKey = null;
                }
                break;
            default:
                break;
            }
        }
    }

    private static void addIfPresent(
        JsonObjectBuilder builder,
        String name,
        String value)
    {
        if (value != null)
        {
            builder.add(name, value);
        }
    }

    private static LlmCanonicalFinishReason finishReason(
        String stopReason)
    {
        LlmCanonicalFinishReason reason;
        if ("max_tokens".equals(stopReason))
        {
            reason = LlmCanonicalFinishReason.LENGTH;
        }
        else if ("tool_use".equals(stopReason))
        {
            reason = LlmCanonicalFinishReason.TOOL_CALL;
        }
        else
        {
            reason = LlmCanonicalFinishReason.STOP;
        }
        return reason;
    }

    private static String stopReason(
        LlmCanonicalFinishReason reason)
    {
        String stopReason;
        switch (reason)
        {
        case LENGTH:
            stopReason = "max_tokens";
            break;
        case TOOL_CALL:
            stopReason = "tool_use";
            break;
        default:
            stopReason = "end_turn";
            break;
        }
        return stopReason;
    }

    @FunctionalInterface
    private interface FieldSink
    {
        void onField(
            String path,
            JsonEvent event);
    }

    // Scratch accumulator for one decode() call's fields, populated by a walk() callback and applied
    // to canonical output afterward, in this dialect's own fixed sequencing.
    private static final class Fields
    {
        private String id;
        private String model;
        private String role;
        private int inputTokens = -1;
        private int blockId;
        private String blockType;
        private String toolId;
        private String toolName;
        private String content;
        private String toolCallArguments;
        private String finishReason;
        private int outputTokens = -1;
    }
}
