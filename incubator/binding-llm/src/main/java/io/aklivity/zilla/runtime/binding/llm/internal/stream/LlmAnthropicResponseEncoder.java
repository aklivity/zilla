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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;

final class LlmAnthropicResponseEncoder
{
    private static final int KIND_NONE = 0;
    private static final int KIND_TEXT = 1;
    private static final int KIND_REASONING = 2;
    private static final int KIND_TOOL_CALL = 3;
    private static final int KIND_SKIPPED = 4;

    private static final int NONE = -1;
    private static final int MESSAGE_FIRST = 0;


    private static final byte[] EVENT_MESSAGE_START = "event: message_start\ndata: ".getBytes(UTF_8);
    private static final byte[] EVENT_BLOCK_START = "event: content_block_start\ndata: ".getBytes(UTF_8);
    private static final byte[] EVENT_BLOCK_DELTA = "event: content_block_delta\ndata: ".getBytes(UTF_8);
    private static final byte[] EVENT_BLOCK_STOP = "event: content_block_stop\ndata: ".getBytes(UTF_8);
    private static final byte[] EVENT_MESSAGE_DELTA = "event: message_delta\ndata: ".getBytes(UTF_8);
    private static final byte[] EVENT_MESSAGE_STOP = "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
        .getBytes(UTF_8);
    private static final byte[] EVENT_ERROR = "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"".getBytes(UTF_8);
    private static final byte[] ERROR_MESSAGE = "\",\"message\":\"".getBytes(UTF_8);
    private static final byte[] ERROR_END = "\"}}\n\n".getBytes(UTF_8);
    private static final byte[] SSE_END = "\n\n".getBytes(UTF_8);
    private static final String BLOCK_STOP_SEQUENCE = "stop_sequence";
    private static final byte[] OBJECT_START = "{".getBytes(UTF_8);
    private static final byte[] OBJECT_END = "}".getBytes(UTF_8);
    private static final byte[] MESSAGE_ID = "\"id\":\"".getBytes(UTF_8);
    private static final byte[] MESSAGE_TYPE = "\"type\":\"message\",\"role\":\"assistant\"".getBytes(UTF_8);
    private static final byte[] MODEL_START = "\"model\":\"".getBytes(UTF_8);
    private static final byte[] CONTENT_START = "\"content\":[".getBytes(UTF_8);
    private static final byte[] CONTENT_END = "]".getBytes(UTF_8);
    private static final byte[] STREAM_MESSAGE_START = "{\"type\":\"message_start\",\"message\":{".getBytes(UTF_8);
    private static final byte[] STREAM_MESSAGE_FIELDS = ",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,"
        .getBytes(UTF_8);
    private static final byte[] STREAM_MESSAGE_USAGE = "\"usage\":{\"input_tokens\":0,\"output_tokens\":0}}}"
        .getBytes(UTF_8);
    private static final byte[] BLOCK_START = "{\"type\":\"content_block_start\",\"index\":".getBytes(UTF_8);
    private static final byte[] BLOCK_TEXT = ",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}".getBytes(UTF_8);
    private static final byte[] BLOCK_THINKING =
        ",\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"\"}}".getBytes(UTF_8);
    private static final byte[] BLOCK_TOOL_ID = ",\"content_block\":{\"type\":\"tool_use\",\"id\":\"".getBytes(UTF_8);
    private static final byte[] BLOCK_TOOL_NAME = "\",\"name\":\"".getBytes(UTF_8);
    private static final byte[] BLOCK_TOOL_INPUT = "\",\"input\":{}}}".getBytes(UTF_8);
    private static final byte[] DELTA_START = "{\"type\":\"content_block_delta\",\"index\":".getBytes(UTF_8);
    private static final byte[] DELTA_TEXT = ",\"delta\":{\"type\":\"text_delta\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_THINKING = ",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_JSON = ",\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_END = "\"}}".getBytes(UTF_8);
    private static final byte[] STOP_START = "{\"type\":\"content_block_stop\",\"index\":".getBytes(UTF_8);
    private static final byte[] STOP_END = "}".getBytes(UTF_8);
    private static final byte[] MESSAGE_DELTA = "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"".getBytes(UTF_8);
    private static final byte[] STOP_SEQUENCE_MEMBER = "\",\"stop_sequence\":".getBytes(UTF_8);
    private static final byte[] MESSAGE_DELTA_USAGE = "},".getBytes(UTF_8);
    private static final byte[] NULL = "null".getBytes(UTF_8);
    private static final byte[] MEMBER_NAME_END = "\":".getBytes(UTF_8);
    private static final byte[] STOP_REASON = ",\"stop_reason\":\"".getBytes(UTF_8);
    private static final byte[] MEMBER_SEPARATOR = ",".getBytes(UTF_8);
    private static final byte[] USAGE_START = "\"usage\":{\"input_tokens\":".getBytes(UTF_8);
    private static final byte[] USAGE_CACHE_WRITE = ",\"cache_creation_input_tokens\":".getBytes(UTF_8);
    private static final byte[] USAGE_CACHE_READ = ",\"cache_read_input_tokens\":".getBytes(UTF_8);
    private static final byte[] USAGE_OUTPUT = ",\"output_tokens\":".getBytes(UTF_8);
    private static final byte[] TEXT_BLOCK = "{\"type\":\"text\",\"text\":\"".getBytes(UTF_8);
    private static final byte[] TEXT_BLOCK_END = "\"}".getBytes(UTF_8);
    private static final byte[] THINKING_BLOCK = "{\"type\":\"thinking\",\"thinking\":\"".getBytes(UTF_8);
    private static final byte[] THINKING_BLOCK_END = "\",\"signature\":\"\"}".getBytes(UTF_8);
    private static final byte[] TOOL_BLOCK = "{\"type\":\"tool_use\",\"id\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_BLOCK_NAME = "\",\"name\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_BLOCK_INPUT = "\",\"input\":".getBytes(UTF_8);
    private static final byte[] TOOL_INPUT_EMPTY = "{}".getBytes(UTF_8);
    private static final byte[] COMMA = ",".getBytes(UTF_8);
    private static final byte[] QUOTE = "\"".getBytes(UTF_8);
    private static final byte[] REPLACEMENT = "\\ufffd".getBytes(UTF_8);

    private final boolean streaming;
    private final byte[] id;
    private final byte[] model;
    private final byte[] carry;
    private final Map<String, byte[]> members;
    private final int membersMax;

    private MutableDirectBuffer out;
    private int position;

    private int kind;
    private int blocks;
    private boolean inputWritten;
    private int toolCalls;
    private int carried;
    private int membersLength;
    private String blockName;
    private byte[] blockBytes;
    private int blockLength;
    private boolean blockOpen;

    LlmAnthropicResponseEncoder(
        boolean streaming,
        String id,
        String model,
        int membersMax)
    {
        this.streaming = streaming;
        this.id = id != null ? escape(id) : null;
        this.model = model != null ? escape(model) : null;
        this.carry = new byte[3];
        this.members = new LinkedHashMap<>();
        this.membersMax = membersMax;
    }

    int begin(
        MutableDirectBuffer out,
        int offset)
    {
        wrap(out, offset);

        if (streaming)
        {
            put(EVENT_MESSAGE_START);
            put(STREAM_MESSAGE_START);
            messageIdentity();
            put(STREAM_MESSAGE_FIELDS);
            put(STREAM_MESSAGE_USAGE);
            put(SSE_END);
        }
        else
        {
            put(OBJECT_START);
            messageIdentity();
            put(COMMA);
            put(CONTENT_START);
        }

        return position - offset;
    }

    int section(
        MutableDirectBuffer out,
        int offset,
        LlmSectionType type,
        int message,
        String toolCallId,
        String toolName)
    {
        wrap(out, offset);

        kind = message == MESSAGE_FIRST ? kindOf(type) : KIND_SKIPPED;

        if (kind != KIND_SKIPPED)
        {
            if (streaming)
            {
                carried = 0;
                sectionStream(toolCallId, toolName);
            }
            else
            {
                sectionBlock(toolCallId, toolName);
            }
        }

        return position - offset;
    }

    int data(
        MutableDirectBuffer out,
        int offset,
        DirectBuffer payload,
        int payloadOffset,
        int payloadLength)
    {
        wrap(out, offset);

        if (kind != KIND_NONE && kind != KIND_SKIPPED)
        {
            if (streaming)
            {
                dataChunk(payload, payloadOffset, payloadLength);
            }
            else if (kind == KIND_TOOL_CALL)
            {
                put(payload, payloadOffset, payloadLength);
                inputWritten = inputWritten || payloadLength > 0;
            }
            else
            {
                putEscaped(payload, payloadOffset, payloadLength);
            }
        }

        return position - offset;
    }

    void blockBegin(
        String block,
        boolean render)
    {
        blockOpen = render;
        blockName = block;
        blockLength = 0;
    }

    void blockData(
        DirectBuffer payload,
        int offset,
        int length)
    {
        if (blockOpen)
        {
            if (membersLength + blockLength + length <= membersMax)
            {
                if (blockBytes == null || blockBytes.length < blockLength + length)
                {
                    blockBytes = blockBytes == null
                        ? new byte[Math.max(256, length)]
                        : Arrays.copyOf(blockBytes, Math.max(blockBytes.length * 2, blockLength + length));
                }

                payload.getBytes(offset, blockBytes, blockLength, length);
                blockLength += length;
            }
            else
            {
                blockOpen = false;
            }
        }
    }

    void blockEnd()
    {
        if (blockOpen)
        {
            final byte[] previous = members.put(blockName, Arrays.copyOf(blockBytes, blockLength));

            membersLength += blockLength - (previous != null ? previous.length : 0);
        }

        blockOpen = false;
    }

    int end(
        MutableDirectBuffer out,
        int offset)
    {
        wrap(out, offset);

        if (kind != KIND_NONE && kind != KIND_SKIPPED)
        {
            if (streaming)
            {
                if (carried > 0)
                {
                    carried = 0;
                    dataChunkStart();
                    put(REPLACEMENT);
                    dataChunkEnd();
                }

                put(EVENT_BLOCK_STOP);
                put(STOP_START);
                putNumber(blocks - 1);
                put(STOP_END);
                put(SSE_END);
            }
            else
            {
                sectionBlockEnd();
            }
        }

        kind = KIND_NONE;

        return position - offset;
    }

    int finish(
        MutableDirectBuffer out,
        int offset,
        String finishReason,
        int inputTokens,
        int cacheWriteTokens,
        int cacheReadTokens,
        int outputTokens,
        int reasoningTokens,
        int totalTokens)
    {
        wrap(out, offset);

        final byte[] reason = stopReason(finishReason);

        if (streaming)
        {
            put(EVENT_MESSAGE_DELTA);
            put(MESSAGE_DELTA);
            put(reason);
            put(STOP_SEQUENCE_MEMBER);
            putStopSequence();
            putBlocks();
            put(MESSAGE_DELTA_USAGE);
            putUsage(inputTokens, cacheWriteTokens, cacheReadTokens, outputTokens);
            put(OBJECT_END);
            put(SSE_END);
            put(EVENT_MESSAGE_STOP);
        }
        else
        {
            put(CONTENT_END);
            put(STOP_REASON);
            put(reason);
            put(STOP_SEQUENCE_MEMBER);
            putStopSequence();
            put(MEMBER_SEPARATOR);
            putUsage(inputTokens, cacheWriteTokens, cacheReadTokens, outputTokens);
            putBlocks();
            put(OBJECT_END);
        }

        return position - offset;
    }

    int error(
        MutableDirectBuffer out,
        int offset,
        String type,
        String param,
        String message)
    {
        wrap(out, offset);

        put(EVENT_ERROR);
        put(escape(type != null ? type : "api_error"));
        put(ERROR_MESSAGE);
        put(escape(message != null ? message : ""));
        put(ERROR_END);

        return position - offset;
    }

    private void messageIdentity()
    {
        if (id != null)
        {
            put(MESSAGE_ID);
            put(id);
            put(QUOTE);
            put(COMMA);
        }

        put(MESSAGE_TYPE);

        if (model != null)
        {
            put(COMMA);
            put(MODEL_START);
            put(model);
            put(QUOTE);
        }
    }

    private void sectionStream(
        String toolCallId,
        String toolName)
    {
        put(EVENT_BLOCK_START);
        put(BLOCK_START);
        putNumber(blocks++);

        switch (kind)
        {
        case KIND_TEXT:
            put(BLOCK_TEXT);
            break;
        case KIND_REASONING:
            put(BLOCK_THINKING);
            break;
        default:
            toolCalls++;
            put(BLOCK_TOOL_ID);
            put(escape(toolCallId != null ? toolCallId : ""));
            put(BLOCK_TOOL_NAME);
            put(escape(toolName != null ? toolName : ""));
            put(BLOCK_TOOL_INPUT);
            break;
        }

        put(SSE_END);
    }

    private void sectionBlock(
        String toolCallId,
        String toolName)
    {
        if (blocks++ > 0)
        {
            put(COMMA);
        }

        switch (kind)
        {
        case KIND_TEXT:
            put(TEXT_BLOCK);
            break;
        case KIND_REASONING:
            put(THINKING_BLOCK);
            break;
        default:
            toolCalls++;
            inputWritten = false;
            put(TOOL_BLOCK);
            put(escape(toolCallId != null ? toolCallId : ""));
            put(TOOL_BLOCK_NAME);
            put(escape(toolName != null ? toolName : ""));
            put(TOOL_BLOCK_INPUT);
            break;
        }
    }

    private void sectionBlockEnd()
    {
        switch (kind)
        {
        case KIND_TEXT:
            put(TEXT_BLOCK_END);
            break;
        case KIND_REASONING:
            put(THINKING_BLOCK_END);
            break;
        default:
            if (!inputWritten)
            {
                put(TOOL_INPUT_EMPTY);
            }
            put(OBJECT_END);
            break;
        }
    }

    private void dataChunkStart()
    {
        put(EVENT_BLOCK_DELTA);
        put(DELTA_START);
        putNumber(blocks - 1);

        switch (kind)
        {
        case KIND_TEXT:
            put(DELTA_TEXT);
            break;
        case KIND_REASONING:
            put(DELTA_THINKING);
            break;
        default:
            put(DELTA_JSON);
            break;
        }
    }

    private void dataChunkEnd()
    {
        put(DELTA_END);
        put(SSE_END);
    }

    private void putUsage(
        int inputTokens,
        int cacheWriteTokens,
        int cacheReadTokens,
        int outputTokens)
    {
        put(USAGE_START);
        putNumber(inputTokens != NONE ? inputTokens : 0);

        if (cacheWriteTokens != NONE)
        {
            put(USAGE_CACHE_WRITE);
            putNumber(cacheWriteTokens);
        }

        if (cacheReadTokens != NONE)
        {
            put(USAGE_CACHE_READ);
            putNumber(cacheReadTokens);
        }

        put(USAGE_OUTPUT);
        putNumber(outputTokens != NONE ? outputTokens : 0);
        put(OBJECT_END);
    }

    private byte[] stopReason(
        String finishReason)
    {
        String reason = toolCalls > 0 ? "tool_use" : "end_turn";

        if (finishReason != null)
        {
            reason = switch (finishReason)
            {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool-call" -> "tool_use";
            case "content-filter" -> "refusal";
            default -> finishReason;
            };
        }

        return escape(reason);
    }

    private static int kindOf(
        LlmSectionType type)
    {
        return switch (type)
        {
        case ASSISTANT_TEXT, ASSISTANT_REFUSAL -> KIND_TEXT;
        case ASSISTANT_REASONING -> KIND_REASONING;
        case TOOL_CALL -> KIND_TOOL_CALL;
        default -> KIND_SKIPPED;
        };
    }

    private void wrap(
        MutableDirectBuffer out,
        int offset)
    {
        this.out = out;
        this.position = offset;
    }

    private void dataChunk(
        DirectBuffer payload,
        int offset,
        int length)
    {
        final int limit = offset + length;
        final int boundary = boundary(payload, offset, limit);
        final int complete = carried + (boundary - offset);

        if (complete > 0)
        {
            dataChunkStart();
            putEscaped(carry, 0, carried);
            putEscaped(payload, offset, boundary - offset);
            dataChunkEnd();
        }

        carried = 0;

        for (int i = boundary; i < limit; i++)
        {
            carry[carried++] = payload.getByte(i);
        }
    }

    private int boundary(
        DirectBuffer payload,
        int offset,
        int limit)
    {
        int boundary = limit;

        for (int back = 1; back <= 3 && limit - back >= offset && boundary == limit; back++)
        {
            final int value = payload.getByte(limit - back) & 0xFF;

            if ((value & 0xC0) != 0x80)
            {
                final int expected = value >= 0xF0 ? 4 : value >= 0xE0 ? 3 : value >= 0xC0 ? 2 : 1;

                if (expected > back)
                {
                    boundary = limit - back;
                }
                else
                {
                    back = 4;
                }
            }
        }

        return boundary;
    }

    private void put(
        DirectBuffer source,
        int offset,
        int length)
    {
        out.putBytes(position, source, offset, length);
        position += length;
    }

    private void put(
        byte[] bytes)
    {
        out.putBytes(position, bytes);
        position += bytes.length;
    }

    private void putStopSequence()
    {
        final byte[] value = members.remove(BLOCK_STOP_SEQUENCE);

        if (value != null)
        {
            membersLength -= value.length;
            put(value);
        }
        else
        {
            put(NULL);
        }
    }

    private void putBlocks()
    {
        for (Map.Entry<String, byte[]> member : members.entrySet())
        {
            put(MEMBER_SEPARATOR);
            put(QUOTE);
            put(escape(member.getKey()));
            put(MEMBER_NAME_END);
            put(member.getValue());
        }

        members.clear();
        membersLength = 0;
    }

    private void putNumber(
        int value)
    {
        position += out.putIntAscii(position, value);
    }

    private void putEscaped(
        byte[] source,
        int offset,
        int length)
    {
        for (int i = 0; i < length; i++)
        {
            putEscaped(source[offset + i]);
        }
    }

    private void putEscaped(
        DirectBuffer source,
        int offset,
        int length)
    {
        for (int i = 0; i < length; i++)
        {
            putEscaped(source.getByte(offset + i));
        }
    }

    private void putEscaped(
        byte value)
    {
        switch (value)
        {
        case '"':
            putPair('"');
            break;
        case '\\':
            putPair('\\');
            break;
        case '\n':
            putPair('n');
            break;
        case '\r':
            putPair('r');
            break;
        case '\t':
            putPair('t');
            break;
        case '\b':
            putPair('b');
            break;
        case '\f':
            putPair('f');
            break;
        default:
            if (value >= 0 && value < 0x20)
            {
                out.putByte(position++, (byte) '\\');
                out.putByte(position++, (byte) 'u');
                out.putByte(position++, (byte) '0');
                out.putByte(position++, (byte) '0');
                out.putByte(position++, (byte) Character.forDigit(value >> 4, 16));
                out.putByte(position++, (byte) Character.forDigit(value & 0x0f, 16));
            }
            else
            {
                out.putByte(position++, value);
            }
            break;
        }
    }

    private void putPair(
        char value)
    {
        out.putByte(position++, (byte) '\\');
        out.putByte(position++, (byte) value);
    }

    private static byte[] escape(
        String value)
    {
        final StringBuilder escaped = new StringBuilder(value.length() + 8);

        for (int i = 0; i < value.length(); i++)
        {
            final char c = value.charAt(i);

            switch (c)
            {
            case '"':
                escaped.append("\\\"");
                break;
            case '\\':
                escaped.append("\\\\");
                break;
            case '\n':
                escaped.append("\\n");
                break;
            case '\r':
                escaped.append("\\r");
                break;
            case '\t':
                escaped.append("\\t");
                break;
            default:
                if (c < 0x20)
                {
                    escaped.append(String.format("\\u%04x", (int) c));
                }
                else
                {
                    escaped.append(c);
                }
                break;
            }
        }

        return escaped.toString().getBytes(UTF_8);
    }
}
