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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.agrona.DirectBuffer;
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
 * Translates between Anthropic's native streaming event sequence and the canonical
 * vocabulary, in both directions. Holds per-stream state (input token count, the
 * currently open block's type, held output tokens pending a {@code finish}) so a
 * fresh instance is required per stream; instances are not shared across streams.
 */
public final class LlmAnthropicEventMapper
{
    private static final int EXTENSION_BUFFER_CAPACITY = 512;

    private final int typeId;
    private final MutableDirectBufferEx flushExBuffer;
    private final LlmFlushExFW.Builder flushExRW;

    private int inputTokens = -1;
    private LlmBlockType openBlockType;

    private int heldOutputTokens = -1;
    private boolean finishSent;

    public LlmAnthropicEventMapper(
        int typeId)
    {
        this.typeId = typeId;
        this.flushExBuffer = new UnsafeBufferEx(new byte[EXTENSION_BUFFER_CAPACITY]);
        this.flushExRW = new LlmFlushExFW.Builder();
    }

    public void decode(
        String event,
        String data,
        LlmEventMapperOutput output)
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

    public void encode(
        DirectBuffer buffer,
        int offset,
        int length,
        LlmDataExFW dataEx,
        LlmNativeEventOutput output)
    {
        String text = buffer.getStringWithoutLengthUtf8(offset, length);
        boolean toolCall = openBlockType == LlmBlockType.TOOL_CALL;

        JsonObjectBuilder delta = Json.createObjectBuilder();
        if (toolCall)
        {
            delta.add("type", "input_json_delta").add("partial_json", text);
        }
        else
        {
            delta.add("type", "text_delta").add("text", text);
        }

        JsonObject event = Json.createObjectBuilder()
            .add("type", "content_block_delta")
            .add("index", 0)
            .add("delta", delta)
            .build();

        output.event("content_block_delta", compact(event));
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
        JsonObject event = Json.createObjectBuilder()
            .add("type", "message_stop")
            .build();

        output.event("message_stop", compact(event));
    }

    private void onMessageStart(
        String data,
        LlmEventMapperOutput output)
    {
        JsonObject message = readObject(data).getJsonObject("message");
        JsonObject usage = message.getJsonObject("usage");
        inputTokens = usage != null ? usage.getInt("input_tokens", -1) : -1;

        LlmFlushExFW flushEx = flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(typeId)
            .messageStart(m -> m
                .choiceIndex(0)
                .id(message.getString("id"))
                .model(getString(message, "model", null))
                .role(getString(message, "role", null)))
            .build();

        output.flush(flushEx);
    }

    private void onContentBlockStart(
        String data,
        LlmEventMapperOutput output)
    {
        JsonObject root = readObject(data);
        JsonObject block = root.getJsonObject("content_block");
        int blockId = root.getInt("index");
        boolean toolCall = "tool_use".equals(getString(block, "type", null));
        openBlockType = toolCall ? LlmBlockType.TOOL_CALL : LlmBlockType.TEXT;

        if (toolCall)
        {
            LlmFlushExFW flushEx = flushExRW
                .wrap(flushExBuffer, 0, flushExBuffer.capacity())
                .typeId(typeId)
                .blockStart(b -> b
                    .choiceIndex(0)
                    .blockId(blockId)
                    .type(t -> t.set(LlmBlockType.TOOL_CALL))
                    .toolId(getString(block, "id", null))
                    .toolName(getString(block, "name", null)))
                .build();

            output.flush(flushEx);
        }
    }

    private void onContentBlockDelta(
        String data,
        LlmEventMapperOutput output)
    {
        JsonObject delta = readObject(data).getJsonObject("delta");
        boolean toolCall = "input_json_delta".equals(getString(delta, "type", null));
        String content = toolCall ? getString(delta, "partial_json", "") : getString(delta, "text", "");

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        output.data(buffer, 0, bytes.length, null);
    }

    private void onContentBlockStop(
        String data,
        LlmEventMapperOutput output)
    {
        if (openBlockType == LlmBlockType.TOOL_CALL)
        {
            int blockId = readObject(data).getInt("index");

            LlmFlushExFW flushEx = flushExRW
                .wrap(flushExBuffer, 0, flushExBuffer.capacity())
                .typeId(typeId)
                .blockEnd(b -> b.choiceIndex(0).blockId(blockId))
                .build();

            output.flush(flushEx);
        }

        openBlockType = null;
    }

    private void onMessageDelta(
        String data,
        LlmEventMapperOutput output)
    {
        JsonObject root = readObject(data);
        JsonObject delta = root.getJsonObject("delta");
        JsonObject usage = root.getJsonObject("usage");

        LlmFinishReason reason = finishReason(getString(delta, "stop_reason", null));
        int outputTokens = usage != null ? usage.getInt("output_tokens", -1) : -1;

        LlmFlushExFW finishEx = flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(typeId)
            .finish(f -> f.choiceIndex(0).reason(r -> r.set(reason)))
            .build();
        output.flush(finishEx);

        LlmFlushExFW usageEx = flushExRW
            .wrap(flushExBuffer, 0, flushExBuffer.capacity())
            .typeId(typeId)
            .usage(u -> u.inputTokens(inputTokens).outputTokens(outputTokens))
            .build();
        output.flush(usageEx);
    }

    private void encodeMessageStart(
        LlmMessageStartFlushExFW messageStart,
        LlmNativeEventOutput output)
    {
        JsonObjectBuilder message = Json.createObjectBuilder()
            .add("id", messageStart.id().asString())
            .add("type", "message")
            .add("role", orDefault(messageStart.role().asString(), "assistant"));

        String model = messageStart.model().asString();
        if (model != null)
        {
            message.add("model", model);
        }

        JsonObject event = Json.createObjectBuilder()
            .add("type", "message_start")
            .add("message", message)
            .build();

        output.event("message_start", compact(event));
    }

    private void encodeBlockStart(
        LlmBlockStartFlushExFW blockStart,
        LlmNativeEventOutput output)
    {
        LlmBlockType type = blockStart.type().get();
        openBlockType = type;

        JsonObjectBuilder contentBlock = Json.createObjectBuilder();
        if (type == LlmBlockType.TOOL_CALL)
        {
            contentBlock.add("type", "tool_use");
            addIfPresent(contentBlock, "id", blockStart.toolId().asString());
            addIfPresent(contentBlock, "name", blockStart.toolName().asString());
        }
        else
        {
            contentBlock.add("type", "text").add("text", "");
        }

        JsonObject event = Json.createObjectBuilder()
            .add("type", "content_block_start")
            .add("index", blockStart.blockId())
            .add("content_block", contentBlock)
            .build();

        output.event("content_block_start", compact(event));
    }

    private void encodeBlockEnd(
        LlmBlockEndFlushExFW blockEnd,
        LlmNativeEventOutput output)
    {
        openBlockType = null;

        JsonObject event = Json.createObjectBuilder()
            .add("type", "content_block_stop")
            .add("index", blockEnd.blockId())
            .build();

        output.event("content_block_stop", compact(event));
    }

    private void encodeFinish(
        LlmFinishFlushExFW finish,
        LlmNativeEventOutput output)
    {
        String stopReason = stopReason(finish.reason().get());
        int outputTokens = Math.max(heldOutputTokens, 0);

        JsonObject event = Json.createObjectBuilder()
            .add("type", "message_delta")
            .add("delta", Json.createObjectBuilder().add("stop_reason", stopReason))
            .add("usage", Json.createObjectBuilder().add("output_tokens", outputTokens))
            .build();

        output.event("message_delta", compact(event));
        finishSent = true;
    }

    private void encodeUsage(
        LlmUsageFlushExFW usage,
        LlmNativeEventOutput output)
    {
        if (!finishSent)
        {
            heldOutputTokens = usage.outputTokens();
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

    private static LlmFinishReason finishReason(
        String stopReason)
    {
        LlmFinishReason reason;
        if ("max_tokens".equals(stopReason))
        {
            reason = LlmFinishReason.LENGTH;
        }
        else if ("tool_use".equals(stopReason))
        {
            reason = LlmFinishReason.TOOL_CALL;
        }
        else
        {
            reason = LlmFinishReason.STOP;
        }
        return reason;
    }

    private static String stopReason(
        LlmFinishReason reason)
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
}
