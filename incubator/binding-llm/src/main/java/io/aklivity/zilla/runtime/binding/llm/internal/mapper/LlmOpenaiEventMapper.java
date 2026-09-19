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
import jakarta.json.JsonValue;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

/**
 * Translates between OpenAI's native streaming chunk sequence and the canonical vocabulary, in both
 * directions, genuinely against {@code common-json}: {@code decode} drives a {@link JsonParserEx}
 * directly over each native chunk (a fresh, self-contained document every call, so {@link
 * JsonParserEx#reset()} rearms it rather than {@code nextDocument()}, which is for continuing the
 * same session's next document); {@code encode*} writes native JSON directly with a {@link
 * JsonGeneratorEx}.
 * <p>
 * OpenAI's {@code index} counts tool calls only, unlike the canonical (Anthropic-shaped) block index,
 * which counts every content block including text; a block-index map translates between the two
 * spaces for the stream's lifetime. OpenAI also has no explicit block-close event, so a canonical
 * {@code blockEnd} is synthesized lazily, only once the next tool call starts or the stream finishes.
 * <p>
 * Holds per-stream state, so a fresh instance is required per stream; instances are not shared across
 * streams.
 */
public final class LlmOpenaiEventMapper implements LlmEventMapper
{
    private static final int NO_BLOCK = -1;
    private static final int GENERATOR_BUFFER_CAPACITY = 8192;

    // decode-direction: parses a fresh, self-contained document per decode() call
    private final JsonParserEx parser;

    // encode-direction: writes a fresh, self-contained native document per encode*() call
    private final JsonGeneratorEx generator;
    private final MutableDirectBufferEx generatorBuffer;

    private boolean messageStarted;
    private int nextBlockId = 1;
    private int openBlockId = NO_BLOCK;
    private final Int2IntHashMap blockIdByToolCallIndex;

    private int nextToolCallIndex;
    private int openToolCallIndex = NO_BLOCK;

    public LlmOpenaiEventMapper()
    {
        this.parser = JsonEx.createParser();
        this.generator = JsonEx.createGenerator();
        this.generatorBuffer = new UnsafeBufferEx(new byte[GENERATOR_BUFFER_CAPACITY]);
        this.blockIdByToolCallIndex = new Int2IntHashMap(NO_BLOCK);
    }

    @Override
    public void decode(
        String event,
        String data,
        LlmCanonicalOutput output)
    {
        if ("[DONE]".equals(data))
        {
            output.end();
        }
        else
        {
            onChunk(data, output);
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

        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        writeChunk(json, 0, () ->
        {
            json.writeStartObject("delta");
            if (openToolCallIndex != NO_BLOCK)
            {
                json.writeStartArray("tool_calls");
                json.writeStartObject();
                json.write("index", openToolCallIndex);
                json.writeStartObject("function");
                json.write("arguments", text);
                json.writeEnd();
                json.writeEnd();
                json.writeEnd();
            }
            else
            {
                json.write("content", text);
            }
            json.writeEnd();
        });
        json.writeEnd();

        emit(null, json.length(), output);
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
        writeChunk(json, choiceIndex, () ->
            json.writeStartObject("delta")
                .write("role", orDefault(role, "assistant"))
                .writeEnd());
        json.write("id", id);
        if (model != null)
        {
            json.write("model", model);
        }
        json.writeEnd();

        emit(null, json.length(), output);
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
        if (type == LlmCanonicalBlockKind.TOOL_CALL)
        {
            int toolCallIndex = nextToolCallIndex++;
            openToolCallIndex = toolCallIndex;

            JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
            json.writeStartObject();
            writeChunk(json, choiceIndex, () ->
            {
                json.writeStartObject("delta");
                json.writeStartArray("tool_calls");
                json.writeStartObject();
                json.write("index", toolCallIndex);
                json.write("type", "function");
                json.writeStartObject("function");
                json.write("arguments", "");
                if (toolName != null)
                {
                    json.write("name", toolName);
                }
                json.writeEnd();
                if (toolId != null)
                {
                    json.write("id", toolId);
                }
                json.writeEnd();
                json.writeEnd();
                json.writeEnd();
            });
            json.writeEnd();

            emit(null, json.length(), output);
        }
        else
        {
            openToolCallIndex = NO_BLOCK;
        }
    }

    @Override
    public void encodeBlockEnd(
        int choiceIndex,
        int blockId,
        LlmNativeEventOutput output)
    {
        openToolCallIndex = NO_BLOCK;
    }

    @Override
    public void encodeFinish(
        int choiceIndex,
        LlmCanonicalFinishReason reason,
        LlmNativeEventOutput output)
    {
        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("object", "chat.completion.chunk");
        json.writeStartArray("choices");
        json.writeStartObject();
        json.write("index", choiceIndex);
        json.writeStartObject("delta").writeEnd();
        json.write("finish_reason", finishReasonText(reason));
        json.writeEnd();
        json.writeEnd();
        json.writeEnd();

        emit(null, json.length(), output);
    }

    @Override
    public void encodeUsage(
        int inputTokens,
        int outputTokens,
        LlmNativeEventOutput output)
    {
        JsonGeneratorEx json = generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
        json.writeStartObject();
        json.write("object", "chat.completion.chunk");
        json.writeStartArray("choices").writeEnd();
        json.writeStartObject("usage");
        if (inputTokens >= 0)
        {
            json.write("prompt_tokens", inputTokens);
        }
        if (outputTokens >= 0)
        {
            json.write("completion_tokens", outputTokens);
        }
        json.writeEnd();
        json.writeEnd();

        emit(null, json.length(), output);
    }

    @Override
    public void encodeEnd(
        LlmNativeEventOutput output)
    {
        byte[] bytes = "[DONE]".getBytes(UTF_8);
        output.event(null, new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    @Override
    public JsonObject decodeMessage(
        String data)
    {
        JsonObject root = readObject(data);
        JsonArray choices = root.getJsonArray("choices");
        JsonObject choice = choices != null && !choices.isEmpty() ? choices.getJsonObject(0) : null;
        JsonObject message = choice != null ? choice.getJsonObject("message") : null;

        JsonArrayBuilder content = Json.createArrayBuilder();
        String text = message != null ? getString(message, "content", null) : null;
        if (text != null)
        {
            content.add(Json.createObjectBuilder().add("type", "text").add("text", text));
        }

        JsonArray toolCalls = message != null ? message.getJsonArray("tool_calls") : null;
        if (toolCalls != null)
        {
            for (int i = 0; i < toolCalls.size(); i++)
            {
                JsonObject toolCall = toolCalls.getJsonObject(i);
                JsonObject function = toolCall.getJsonObject("function");
                JsonObjectBuilder block = Json.createObjectBuilder().add("type", "tool_call");
                addIfPresent(block, "toolId", getString(toolCall, "id", null));
                addIfPresent(block, "toolName", function != null ? getString(function, "name", null) : null);
                block.add("arguments", function != null ? getString(function, "arguments", "") : "");
                content.add(block);
            }
        }

        String finishReasonValue = choice != null ? getString(choice, "finish_reason", "stop") : "stop";
        JsonObject usage = root.getJsonObject("usage");

        JsonObjectBuilder canonical = Json.createObjectBuilder();
        addIfPresent(canonical, "id", getString(root, "id", null));
        addIfPresent(canonical, "model", getString(root, "model", null));
        canonical.add("role", message != null ? orDefault(getString(message, "role", null), "assistant") : "assistant");
        canonical.add("content", content);
        canonical.add("finishReason", finishReason(finishReasonValue).name());
        canonical.add("usage", Json.createObjectBuilder()
            .add("inputTokens", usage != null ? usage.getInt("prompt_tokens", -1) : -1)
            .add("outputTokens", usage != null ? usage.getInt("completion_tokens", -1) : -1));

        return canonical.build();
    }

    @Override
    public String encodeMessage(
        JsonObject message)
    {
        JsonArray content = message.getJsonArray("content");
        StringBuilder text = new StringBuilder();
        JsonArrayBuilder toolCalls = Json.createArrayBuilder();
        boolean hasToolCalls = false;

        for (int i = 0; i < content.size(); i++)
        {
            JsonObject block = content.getJsonObject(i);
            if ("tool_call".equals(block.getString("type")))
            {
                hasToolCalls = true;

                JsonObjectBuilder function = Json.createObjectBuilder();
                addIfPresent(function, "name", getString(block, "toolName", null));
                function.add("arguments", getString(block, "arguments", ""));

                JsonObjectBuilder toolCall = Json.createObjectBuilder();
                addIfPresent(toolCall, "id", getString(block, "toolId", null));
                toolCall.add("type", "function");
                toolCall.add("function", function);

                toolCalls.add(toolCall);
            }
            else
            {
                text.append(getString(block, "text", ""));
            }
        }

        JsonObjectBuilder messageObject = Json.createObjectBuilder()
            .add("role", getString(message, "role", "assistant"))
            .add("content", text.length() > 0 ? Json.createValue(text.toString()) : JsonValue.NULL);
        if (hasToolCalls)
        {
            messageObject.add("tool_calls", toolCalls);
        }

        JsonObjectBuilder choice = Json.createObjectBuilder()
            .add("index", 0)
            .add("message", messageObject)
            .add("finish_reason", finishReasonText(LlmCanonicalFinishReason.valueOf(message.getString("finishReason"))));

        JsonObjectBuilder root = Json.createObjectBuilder()
            .add("object", "chat.completion");
        addIfPresent(root, "id", getString(message, "id", null));
        addIfPresent(root, "model", getString(message, "model", null));
        root.add("choices", Json.createArrayBuilder().add(choice));

        JsonObject usage = message.getJsonObject("usage");
        int inputTokens = usage != null ? usage.getInt("inputTokens", -1) : -1;
        int outputTokens = usage != null ? usage.getInt("outputTokens", -1) : -1;
        if (inputTokens >= 0 || outputTokens >= 0)
        {
            JsonObjectBuilder usageObject = Json.createObjectBuilder();
            if (inputTokens >= 0)
            {
                usageObject.add("prompt_tokens", inputTokens);
            }
            if (outputTokens >= 0)
            {
                usageObject.add("completion_tokens", outputTokens);
            }
            root.add("usage", usageObject);
        }

        return compact(root.build());
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

    // Walks the whole chunk via a plain JsonParserEx loop, accumulating the fields the OpenAI shape can
    // carry into a Chunk, then fires canonical output calls in the same fixed order the dialect's own
    // streaming semantics always resolve to, regardless of the order this chunk's own keys arrive in.
    private void onChunk(
        String data,
        LlmCanonicalOutput output)
    {
        Chunk chunk = new Chunk();
        byte[] bytes = data.getBytes(UTF_8);
        parser.reset();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        ChunkWalker walker = new ChunkWalker(chunk);
        JsonEvent event;
        while ((event = parser.nextEvent()) != null)
        {
            walker.onEvent(event);
        }

        apply(chunk, output);
    }

    // Holds the position/depth/array-index state a single onChunk() walk mutates across parser events,
    // split out of onChunk() itself only to keep that method under the checkstyle method-length limit --
    // the three onEvent() steps below still run once per event, in the same fixed order, against the
    // same shared state, exactly as onChunk()'s single loop body did before the split.
    private final class ChunkWalker
    {
        private final Chunk chunk;

        private int depth;
        private Position position = Position.ROOT;
        private Position choiceKeyPosition = Position.ROOT;
        private Position deltaKeyPosition = Position.ROOT;
        private Position toolCallKeyPosition = Position.ROOT;
        private Position functionKeyPosition = Position.ROOT;
        private Position usageKeyPosition = Position.ROOT;
        private int choicesArrayIndex = -1;
        private int toolCallsArrayIndex = -1;
        private boolean inChoice0;
        private boolean inToolCall0;

        private ChunkWalker(
            Chunk chunk)
        {
            this.chunk = chunk;
        }

        private void onEvent(
            JsonEvent event)
        {
            onStructural(event);
            onContainerTransition(event);
            onLeafValue(event);
        }

        private void onStructural(
            JsonEvent event)
        {
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                if (depth == 2 && position == Position.CHOICE)
                {
                    inChoice0 = false;
                    position = Position.CHOICES_ARRAY;
                }
                else if (depth == 5 && position == Position.TOOL_CALL)
                {
                    inToolCall0 = false;
                    position = Position.TOOL_CALLS_ARRAY;
                }
                else if (depth == 6 && position == Position.FUNCTION)
                {
                    position = Position.TOOL_CALL;
                }
                else if (depth == 1 && (position == Position.CHOICES_ARRAY || position == Position.USAGE))
                {
                    position = Position.ROOT;
                }
                else if (depth == 3 && position == Position.DELTA)
                {
                    position = Position.CHOICE;
                }
                else if (depth == 4 && position == Position.TOOL_CALLS_ARRAY)
                {
                    position = Position.DELTA;
                }
                break;
            case KEY_NAME:
                onKeyName();
                break;
            default:
                break;
            }
        }

        private void onKeyName()
        {
            CharSequence key = parser.getStringView();
            if (depth == 1 && position == Position.ROOT)
            {
                if (matches(key, "id"))
                {
                    position = Position.ROOT_ID;
                }
                else if (matches(key, "model"))
                {
                    position = Position.ROOT_MODEL;
                }
                else if (matches(key, "choices"))
                {
                    position = Position.CHOICES_KEY;
                }
                else if (matches(key, "usage"))
                {
                    position = Position.USAGE_KEY;
                }
            }
            else if (depth == 3 && position == Position.CHOICE)
            {
                if (matches(key, "index"))
                {
                    choiceKeyPosition = Position.CHOICE_INDEX;
                }
                else if (matches(key, "delta"))
                {
                    choiceKeyPosition = Position.DELTA_KEY;
                }
                else if (matches(key, "finish_reason"))
                {
                    choiceKeyPosition = Position.FINISH_REASON;
                }
                else
                {
                    choiceKeyPosition = Position.ROOT;
                }
            }
            else if (depth == 4 && position == Position.DELTA)
            {
                if (matches(key, "role"))
                {
                    deltaKeyPosition = Position.DELTA_ROLE;
                }
                else if (matches(key, "content"))
                {
                    deltaKeyPosition = Position.DELTA_CONTENT;
                }
                else if (matches(key, "tool_calls"))
                {
                    deltaKeyPosition = Position.TOOL_CALLS_KEY;
                }
                else
                {
                    deltaKeyPosition = Position.ROOT;
                }
            }
            else if (depth == 6 && position == Position.TOOL_CALL)
            {
                if (matches(key, "index"))
                {
                    toolCallKeyPosition = Position.TOOL_CALL_INDEX;
                }
                else if (matches(key, "id"))
                {
                    toolCallKeyPosition = Position.TOOL_CALL_ID;
                }
                else if (matches(key, "function"))
                {
                    toolCallKeyPosition = Position.FUNCTION_KEY;
                }
                else
                {
                    toolCallKeyPosition = Position.ROOT;
                }
            }
            else if (depth == 7 && position == Position.FUNCTION)
            {
                if (matches(key, "name"))
                {
                    functionKeyPosition = Position.FUNCTION_NAME;
                }
                else if (matches(key, "arguments"))
                {
                    functionKeyPosition = Position.FUNCTION_ARGUMENTS;
                }
                else
                {
                    functionKeyPosition = Position.ROOT;
                }
            }
            else if (depth == 2 && position == Position.USAGE)
            {
                if (matches(key, "prompt_tokens"))
                {
                    usageKeyPosition = Position.USAGE_PROMPT_TOKENS;
                }
                else if (matches(key, "completion_tokens"))
                {
                    usageKeyPosition = Position.USAGE_COMPLETION_TOKENS;
                }
                else
                {
                    usageKeyPosition = Position.ROOT;
                }
            }
        }

        // Container transitions decided after a KEY_NAME's next START_OBJECT/START_ARRAY, or after a
        // scalar value at the position the preceding KEY_NAME selected.
        private void onContainerTransition(
            JsonEvent event)
        {
            switch (position)
            {
            case CHOICES_KEY:
                if (event == JsonEvent.START_ARRAY)
                {
                    position = Position.CHOICES_ARRAY;
                    choicesArrayIndex = -1;
                }
                break;
            case CHOICES_ARRAY:
                if (event == JsonEvent.START_OBJECT && depth == 3)
                {
                    choicesArrayIndex++;
                    inChoice0 = choicesArrayIndex == 0;
                    position = Position.CHOICE;
                    choiceKeyPosition = Position.ROOT;
                }
                break;
            case USAGE_KEY:
                if (event == JsonEvent.START_OBJECT)
                {
                    position = Position.USAGE;
                    usageKeyPosition = Position.ROOT;
                }
                break;
            case ROOT_ID:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.id = parser.getString();
                    position = Position.ROOT;
                }
                break;
            case ROOT_MODEL:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.model = parser.getString();
                    position = Position.ROOT;
                }
                break;
            default:
                break;
            }
        }

        private void onLeafValue(
            JsonEvent event)
        {
            if (position == Position.CHOICE && inChoice0)
            {
                onChoiceLeafValue(event);
            }
            else if (position == Position.DELTA)
            {
                onDeltaLeafValue(event);
            }
            else if (position == Position.TOOL_CALLS_ARRAY)
            {
                if (event == JsonEvent.START_OBJECT && depth == 6)
                {
                    toolCallsArrayIndex++;
                    inToolCall0 = toolCallsArrayIndex == 0;
                    position = Position.TOOL_CALL;
                    toolCallKeyPosition = Position.ROOT;
                    if (inToolCall0)
                    {
                        chunk.hasToolCall = true;
                    }
                }
            }
            else if (position == Position.TOOL_CALL && inToolCall0)
            {
                onToolCallLeafValue(event);
            }
            else if (position == Position.FUNCTION)
            {
                onFunctionLeafValue(event);
            }
            else if (position == Position.USAGE)
            {
                onUsageLeafValue(event);
            }
        }

        private void onChoiceLeafValue(
            JsonEvent event)
        {
            switch (choiceKeyPosition)
            {
            case CHOICE_INDEX:
                if (event == JsonEvent.VALUE_NUMBER)
                {
                    chunk.choiceIndex = parser.getInt();
                    choiceKeyPosition = Position.ROOT;
                }
                break;
            case DELTA_KEY:
                if (event == JsonEvent.START_OBJECT)
                {
                    chunk.hasDelta = true;
                    position = Position.DELTA;
                    deltaKeyPosition = Position.ROOT;
                }
                break;
            case FINISH_REASON:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.finishReason = parser.getString();
                    choiceKeyPosition = Position.ROOT;
                }
                break;
            default:
                break;
            }
        }

        private void onDeltaLeafValue(
            JsonEvent event)
        {
            switch (deltaKeyPosition)
            {
            case DELTA_ROLE:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.role = parser.getString();
                    deltaKeyPosition = Position.ROOT;
                }
                break;
            case DELTA_CONTENT:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.content = parser.getString();
                    deltaKeyPosition = Position.ROOT;
                }
                break;
            case TOOL_CALLS_KEY:
                if (event == JsonEvent.START_ARRAY)
                {
                    position = Position.TOOL_CALLS_ARRAY;
                    toolCallsArrayIndex = -1;
                }
                break;
            default:
                break;
            }
        }

        private void onToolCallLeafValue(
            JsonEvent event)
        {
            switch (toolCallKeyPosition)
            {
            case TOOL_CALL_INDEX:
                if (event == JsonEvent.VALUE_NUMBER)
                {
                    chunk.toolCallIndex = parser.getInt();
                    toolCallKeyPosition = Position.ROOT;
                }
                break;
            case TOOL_CALL_ID:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.toolCallId = parser.getString();
                    toolCallKeyPosition = Position.ROOT;
                }
                break;
            case FUNCTION_KEY:
                if (event == JsonEvent.START_OBJECT)
                {
                    position = Position.FUNCTION;
                    functionKeyPosition = Position.ROOT;
                }
                break;
            default:
                break;
            }
        }

        private void onFunctionLeafValue(
            JsonEvent event)
        {
            switch (functionKeyPosition)
            {
            case FUNCTION_NAME:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.toolCallName = parser.getString();
                    functionKeyPosition = Position.ROOT;
                }
                break;
            case FUNCTION_ARGUMENTS:
                if (event == JsonEvent.VALUE_STRING)
                {
                    chunk.toolCallArguments = parser.getString();
                    functionKeyPosition = Position.ROOT;
                }
                break;
            default:
                break;
            }
        }

        private void onUsageLeafValue(
            JsonEvent event)
        {
            switch (usageKeyPosition)
            {
            case USAGE_PROMPT_TOKENS:
                if (event == JsonEvent.VALUE_NUMBER)
                {
                    chunk.inputTokens = parser.getInt();
                    usageKeyPosition = Position.ROOT;
                }
                break;
            case USAGE_COMPLETION_TOKENS:
                if (event == JsonEvent.VALUE_NUMBER)
                {
                    chunk.outputTokens = parser.getInt();
                    usageKeyPosition = Position.ROOT;
                }
                break;
            default:
                break;
            }
        }
    }

    // Fires canonical output calls from the fully-accumulated chunk, in the same fixed order the
    // dialect's own streaming semantics always resolve to.
    private void apply(
        Chunk chunk,
        LlmCanonicalOutput output)
    {
        if (!messageStarted)
        {
            messageStarted = true;
            String role = chunk.hasDelta ? orDefault(chunk.role, "assistant") : "assistant";
            output.messageStart(chunk.choiceIndex, chunk.id, chunk.model, role);
            output.blockStart(chunk.choiceIndex, 0, LlmCanonicalBlockKind.TEXT, null, null);
            openBlockId = 0;
        }

        if (chunk.content != null && !chunk.content.isEmpty())
        {
            emitData(chunk.content, output);
        }

        if (chunk.hasToolCall)
        {
            onToolCallDelta(chunk, output);
        }

        if (chunk.finishReason != null)
        {
            closeOpenBlock(output);
            output.finish(0, finishReason(chunk.finishReason));
        }

        if (chunk.inputTokens != -1 || chunk.outputTokens != -1)
        {
            output.usage(chunk.inputTokens, chunk.outputTokens);
        }
    }

    private void onToolCallDelta(
        Chunk chunk,
        LlmCanonicalOutput output)
    {
        int toolCallIndex = chunk.toolCallIndex;
        int blockId = blockIdByToolCallIndex.get(toolCallIndex);
        if (blockId == NO_BLOCK)
        {
            closeOpenBlock(output);

            blockId = nextBlockId++;
            blockIdByToolCallIndex.put(toolCallIndex, blockId);

            output.blockStart(0, blockId, LlmCanonicalBlockKind.TOOL_CALL, chunk.toolCallId, chunk.toolCallName);
            openBlockId = blockId;
        }

        if (chunk.toolCallArguments != null && !chunk.toolCallArguments.isEmpty())
        {
            emitData(chunk.toolCallArguments, output);
        }
    }

    private void closeOpenBlock(
        LlmCanonicalOutput output)
    {
        if (openBlockId != NO_BLOCK)
        {
            output.blockEnd(0, openBlockId);
            openBlockId = NO_BLOCK;
        }
    }

    private static void emitData(
        String content,
        LlmCanonicalOutput output)
    {
        byte[] bytes = content.getBytes(UTF_8);
        output.data(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private static void writeChunk(
        JsonGeneratorEx json,
        int choiceIndex,
        Runnable writeDelta)
    {
        json.write("object", "chat.completion.chunk");
        json.writeStartArray("choices");
        json.writeStartObject();
        json.write("index", choiceIndex);
        writeDelta.run();
        json.writeNull("finish_reason");
        json.writeEnd();
        json.writeEnd();
    }

    private void emit(
        String name,
        int length,
        LlmNativeEventOutput output)
    {
        output.event(name, generatorBuffer, 0, length);
    }

    private static LlmCanonicalFinishReason finishReason(
        String value)
    {
        LlmCanonicalFinishReason reason;
        switch (value)
        {
        case "length":
            reason = LlmCanonicalFinishReason.LENGTH;
            break;
        case "tool_calls":
            reason = LlmCanonicalFinishReason.TOOL_CALL;
            break;
        case "content_filter":
            reason = LlmCanonicalFinishReason.CONTENT_FILTER;
            break;
        default:
            reason = LlmCanonicalFinishReason.STOP;
            break;
        }
        return reason;
    }

    private static String finishReasonText(
        LlmCanonicalFinishReason reason)
    {
        String value;
        switch (reason)
        {
        case LENGTH:
            value = "length";
            break;
        case TOOL_CALL:
            value = "tool_calls";
            break;
        case CONTENT_FILTER:
            value = "content_filter";
            break;
        default:
            value = "stop";
            break;
        }
        return value;
    }

    private static boolean matches(
        CharSequence key,
        String name)
    {
        boolean matches = key.length() == name.length();
        for (int i = 0; matches && i < name.length(); i++)
        {
            matches = key.charAt(i) == name.charAt(i);
        }
        return matches;
    }

    // A native chunk's fields, fully accumulated across the walk regardless of the order the chunk's own
    // keys arrive in, so canonical output can fire in the dialect's own fixed sequencing afterward.
    private static final class Chunk
    {
        private String id;
        private String model;
        private int choiceIndex;
        private boolean hasDelta;
        private String role;
        private String content;
        private boolean hasToolCall;
        private int toolCallIndex;
        private String toolCallId;
        private String toolCallName;
        private String toolCallArguments;
        private String finishReason;
        private int inputTokens = -1;
        private int outputTokens = -1;
    }

    private enum Position
    {
        ROOT,
        ROOT_ID,
        ROOT_MODEL,
        CHOICES_KEY,
        CHOICES_ARRAY,
        CHOICE,
        CHOICE_INDEX,
        DELTA_KEY,
        DELTA,
        DELTA_ROLE,
        DELTA_CONTENT,
        TOOL_CALLS_KEY,
        TOOL_CALLS_ARRAY,
        TOOL_CALL,
        TOOL_CALL_INDEX,
        TOOL_CALL_ID,
        FUNCTION_KEY,
        FUNCTION,
        FUNCTION_NAME,
        FUNCTION_ARGUMENTS,
        FINISH_REASON,
        USAGE_KEY,
        USAGE,
        USAGE_PROMPT_TOKENS,
        USAGE_COMPLETION_TOKENS
    }
}
