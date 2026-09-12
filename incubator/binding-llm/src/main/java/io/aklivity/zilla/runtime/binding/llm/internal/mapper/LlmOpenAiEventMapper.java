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

import java.nio.charset.StandardCharsets;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockEndFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockStartFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmBlockType;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFinishFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFinishReason;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmMessageStartFlushExFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmUsageFlushExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * Translates between OpenAI's native streaming chunk sequence and the canonical
 * vocabulary, in both directions.
 * <p>
 * OpenAI's {@code index} counts tool calls only, unlike the canonical (Anthropic-shaped)
 * block index, which counts every content block including text; a block-index map
 * translates between the two spaces for the stream's lifetime. OpenAI also has no
 * explicit block-close event, so a canonical {@code blockEnd} is synthesized lazily,
 * only once the next tool call starts or the stream finishes.
 * <p>
 * Holds per-stream state, so a fresh instance is required per stream; instances are
 * not shared across streams.
 */
public final class LlmOpenAiEventMapper
{
    private static final int NO_BLOCK = -1;
    private static final int EXTENSION_BUFFER_CAPACITY = 512;

    private final int typeId;
    private final MutableDirectBufferEx flushExBuffer;
    private final LlmFlushExFW.Builder flushExRW;

    private boolean messageStarted;
    private int nextBlockId = 1;
    private int openBlockId = NO_BLOCK;
    private final Int2IntHashMap blockIdByToolCallIndex;

    private int nextToolCallIndex;
    private int openToolCallIndex = NO_BLOCK;

    public LlmOpenAiEventMapper(
        int typeId)
    {
        this.typeId = typeId;
        this.flushExBuffer = new UnsafeBufferEx(new byte[EXTENSION_BUFFER_CAPACITY]);
        this.flushExRW = new LlmFlushExFW.Builder();
        this.blockIdByToolCallIndex = new Int2IntHashMap(NO_BLOCK);
    }

    public void decode(
        String event,
        String data,
        LlmEventMapperOutput output)
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

    public void encode(
        DirectBuffer buffer,
        int offset,
        int length,
        LlmDataExFW dataEx,
        LlmNativeEventOutput output)
    {
        String text = buffer.getStringWithoutLengthUtf8(offset, length);

        JsonObjectBuilder delta = Json.createObjectBuilder();
        if (openToolCallIndex != NO_BLOCK)
        {
            JsonObjectBuilder function = Json.createObjectBuilder().add("arguments", text);
            JsonObjectBuilder toolCall = Json.createObjectBuilder()
                .add("index", openToolCallIndex)
                .add("function", function);
            delta.add("tool_calls", Json.createArrayBuilder().add(toolCall));
        }
        else
        {
            delta.add("content", text);
        }

        output.event(null, compact(chunk(0, delta).build()));
    }

    public void encode(
        LlmFlushExFW flushEx,
        LlmNativeEventOutput output)
    {
        switch (flushEx.kind())
        {
        case LlmFlushExFW.KIND_MESSAGE_START:
            encodeMessageStart(flushEx.messageStart(), output);
            break;
        case LlmFlushExFW.KIND_BLOCK_START:
            encodeBlockStart(flushEx.blockStart(), output);
            break;
        case LlmFlushExFW.KIND_BLOCK_END:
            encodeBlockEnd(flushEx.blockEnd(), output);
            break;
        case LlmFlushExFW.KIND_FINISH:
            encodeFinish(flushEx.finish(), output);
            break;
        case LlmFlushExFW.KIND_USAGE:
            encodeUsage(flushEx.usage(), output);
            break;
        default:
            break;
        }
    }

    public void encodeEnd(
        LlmNativeEventOutput output)
    {
        output.event(null, "[DONE]");
    }

    private void onChunk(
        String data,
        LlmEventMapperOutput output)
    {
        JsonObject root = readObject(data);
        JsonArray choices = root.getJsonArray("choices");
        JsonObject choice = choices != null && !choices.isEmpty() ? choices.getJsonObject(0) : null;

        if (!messageStarted)
        {
            onMessageStart(root, choice, output);
        }

        if (choice != null)
        {
            onChoice(choice, output);
        }

        JsonObject usage = root.getJsonObject("usage");
        if (usage != null)
        {
            onUsage(usage, output);
        }
    }

    private void onMessageStart(
        JsonObject root,
        JsonObject choice,
        LlmEventMapperOutput output)
    {
        messageStarted = true;

        int choiceIndex = choice != null ? choice.getInt("index", 0) : 0;
        JsonObject delta = choice != null ? choice.getJsonObject("delta") : null;
        String role = delta != null ? getString(delta, "role", "assistant") : "assistant";

        LlmFlushExFW messageStartEx = flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(typeId)
            .messageStart(m -> m
                .choiceIndex(choiceIndex)
                .id(root.getString("id"))
                .model(getString(root, "model", null))
                .role(role))
            .build();
        output.flush(messageStartEx);

        LlmFlushExFW blockStartEx = flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(typeId)
            .blockStart(b -> b.choiceIndex(choiceIndex).blockId(0).type(t -> t.set(LlmBlockType.TEXT)))
            .build();
        output.flush(blockStartEx);

        openBlockId = 0;
    }

    private void onChoice(
        JsonObject choice,
        LlmEventMapperOutput output)
    {
        JsonObject delta = choice.getJsonObject("delta");
        if (delta != null)
        {
            String content = getString(delta, "content", null);
            if (content != null && !content.isEmpty())
            {
                emitData(content, output);
            }

            JsonArray toolCalls = delta.getJsonArray("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty())
            {
                onToolCallDelta(toolCalls.getJsonObject(0), output);
            }
        }

        String finishReason = getString(choice, "finish_reason", null);
        if (finishReason != null)
        {
            onFinish(finishReason, output);
        }
    }

    private void onToolCallDelta(
        JsonObject toolCall,
        LlmEventMapperOutput output)
    {
        int toolCallIndex = toolCall.getInt("index", 0);
        int blockId = blockIdByToolCallIndex.get(toolCallIndex);
        if (blockId == NO_BLOCK)
        {
            closeOpenBlock(output);

            blockId = nextBlockId++;
            blockIdByToolCallIndex.put(toolCallIndex, blockId);

            JsonObject function = toolCall.getJsonObject("function");
            String toolId = getString(toolCall, "id", null);
            String toolName = function != null ? getString(function, "name", null) : null;
            int newBlockId = blockId;

            LlmFlushExFW blockStartEx = flushExRW
                .wrap(flushExBuffer, 0, flushExBuffer.capacity())
                .typeId(typeId)
                .blockStart(b -> b
                    .choiceIndex(0)
                    .blockId(newBlockId)
                    .type(t -> t.set(LlmBlockType.TOOL_CALL))
                    .toolId(toolId)
                    .toolName(toolName))
                .build();
            output.flush(blockStartEx);

            openBlockId = blockId;
        }

        JsonObject function = toolCall.getJsonObject("function");
        String arguments = function != null ? getString(function, "arguments", null) : null;
        if (arguments != null && !arguments.isEmpty())
        {
            emitData(arguments, output);
        }
    }

    private void closeOpenBlock(
        LlmEventMapperOutput output)
    {
        if (openBlockId != NO_BLOCK)
        {
            int blockId = openBlockId;

            LlmFlushExFW blockEndEx = flushExRW
                .wrap(flushExBuffer, 0, flushExBuffer.capacity())
                .typeId(typeId)
                .blockEnd(b -> b.choiceIndex(0).blockId(blockId))
                .build();
            output.flush(blockEndEx);

            openBlockId = NO_BLOCK;
        }
    }

    private void onFinish(
        String finishReason,
        LlmEventMapperOutput output)
    {
        closeOpenBlock(output);

        LlmFinishReason reason = finishReason(finishReason);
        LlmFlushExFW finishEx = flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(typeId)
            .finish(f -> f.choiceIndex(0).reason(r -> r.set(reason)))
            .build();
        output.flush(finishEx);
    }

    private void onUsage(
        JsonObject usage,
        LlmEventMapperOutput output)
    {
        LlmFlushExFW usageEx = flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(typeId)
            .usage(u -> u
                .inputTokens(usage.getInt("prompt_tokens", -1))
                .outputTokens(usage.getInt("completion_tokens", -1)))
            .build();
        output.flush(usageEx);
    }

    private static void emitData(
        String content,
        LlmEventMapperOutput output)
    {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        output.data(buffer, 0, bytes.length, null);
    }

    private void encodeMessageStart(
        LlmMessageStartFlushExFW messageStart,
        LlmNativeEventOutput output)
    {
        JsonObjectBuilder delta = Json.createObjectBuilder()
            .add("role", orDefault(messageStart.role().asString(), "assistant"));

        JsonObjectBuilder root = chunk(messageStart.choiceIndex(), delta)
            .add("id", messageStart.id().asString())
            .add("object", "chat.completion.chunk");

        String model = messageStart.model().asString();
        if (model != null)
        {
            root.add("model", model);
        }

        output.event(null, compact(root.build()));
    }

    private void encodeBlockStart(
        LlmBlockStartFlushExFW blockStart,
        LlmNativeEventOutput output)
    {
        if (blockStart.type().get() == LlmBlockType.TOOL_CALL)
        {
            int toolCallIndex = nextToolCallIndex++;
            openToolCallIndex = toolCallIndex;

            JsonObjectBuilder function = Json.createObjectBuilder().add("arguments", "");
            String toolName = blockStart.toolName().asString();
            if (toolName != null)
            {
                function.add("name", toolName);
            }

            JsonObjectBuilder toolCall = Json.createObjectBuilder()
                .add("index", toolCallIndex)
                .add("type", "function")
                .add("function", function);
            String toolId = blockStart.toolId().asString();
            if (toolId != null)
            {
                toolCall.add("id", toolId);
            }

            JsonObjectBuilder delta = Json.createObjectBuilder()
                .add("tool_calls", Json.createArrayBuilder().add(toolCall));

            output.event(null, compact(chunk(blockStart.choiceIndex(), delta).build()));
        }
        else
        {
            openToolCallIndex = NO_BLOCK;
        }
    }

    private void encodeBlockEnd(
        LlmBlockEndFlushExFW blockEnd,
        LlmNativeEventOutput output)
    {
        openToolCallIndex = NO_BLOCK;
    }

    private void encodeFinish(
        LlmFinishFlushExFW finish,
        LlmNativeEventOutput output)
    {
        String reason = finishReasonText(finish.reason().get());

        JsonObjectBuilder choice = Json.createObjectBuilder()
            .add("index", finish.choiceIndex())
            .add("delta", Json.createObjectBuilder())
            .add("finish_reason", reason);

        JsonObject root = Json.createObjectBuilder()
            .add("object", "chat.completion.chunk")
            .add("choices", Json.createArrayBuilder().add(choice))
            .build();

        output.event(null, compact(root));
    }

    private void encodeUsage(
        LlmUsageFlushExFW usage,
        LlmNativeEventOutput output)
    {
        JsonObjectBuilder usageObject = Json.createObjectBuilder();
        if (usage.inputTokens() >= 0)
        {
            usageObject.add("prompt_tokens", usage.inputTokens());
        }
        if (usage.outputTokens() >= 0)
        {
            usageObject.add("completion_tokens", usage.outputTokens());
        }

        JsonObject root = Json.createObjectBuilder()
            .add("object", "chat.completion.chunk")
            .add("choices", Json.createArrayBuilder())
            .add("usage", usageObject)
            .build();

        output.event(null, compact(root));
    }

    private static JsonObjectBuilder chunk(
        int choiceIndex,
        JsonObjectBuilder delta)
    {
        JsonObjectBuilder choice = Json.createObjectBuilder()
            .add("index", choiceIndex)
            .add("delta", delta)
            .add("finish_reason", JsonValue.NULL);

        return Json.createObjectBuilder()
            .add("object", "chat.completion.chunk")
            .add("choices", Json.createArrayBuilder().add(choice));
    }

    private static LlmFinishReason finishReason(
        String value)
    {
        LlmFinishReason reason;
        switch (value)
        {
        case "length":
            reason = LlmFinishReason.LENGTH;
            break;
        case "tool_calls":
            reason = LlmFinishReason.TOOL_CALL;
            break;
        case "content_filter":
            reason = LlmFinishReason.CONTENT_FILTER;
            break;
        default:
            reason = LlmFinishReason.STOP;
            break;
        }
        return reason;
    }

    private static String finishReasonText(
        LlmFinishReason reason)
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
}
