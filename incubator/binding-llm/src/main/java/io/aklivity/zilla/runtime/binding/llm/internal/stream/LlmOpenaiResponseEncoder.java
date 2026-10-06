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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.engine.util.Flags;

final class LlmOpenaiResponseEncoder
{
    private static final int EVENT_CAPACITY = 8;
    private static final int NO_EVENT = -1;

    private static final int KIND_NONE = 0;
    private static final int KIND_TEXT = 1;
    private static final int KIND_REFUSAL = 2;
    private static final int KIND_TOOL_CALL = 3;
    private static final int KIND_SKIPPED = 4;

    private static final int MEMBER_NONE = 0;
    private static final int MEMBER_CONTENT = 1;
    private static final int MEMBER_REFUSAL = 2;
    private static final int MEMBER_TOOL_CALLS = 3;

    private static final int STATE_PRELUDE = 0;
    private static final int STATE_CHOICE = 1;
    private static final int STATE_TAIL = 2;

    private static final int NONE = -1;
    private static final int MESSAGE_FIRST = 0;

    private static final byte[] DONE = "[DONE]".getBytes(UTF_8);
    private static final byte[] OBJECT_START = "{".getBytes(UTF_8);
    private static final byte[] ID_START = "\"id\":\"".getBytes(UTF_8);
    private static final byte[] OBJECT_COMPLETION = "\"object\":\"chat.completion\"".getBytes(UTF_8);
    private static final byte[] OBJECT_CHUNK = "\"object\":\"chat.completion.chunk\"".getBytes(UTF_8);
    private static final byte[] MODEL_START = "\"model\":\"".getBytes(UTF_8);
    private static final byte[] CHOICES_START = "\"choices\":[".getBytes(UTF_8);
    private static final byte[] CHOICES_END = "]".getBytes(UTF_8);
    private static final byte[] MESSAGE_START = "{\"index\":0,\"message\":{\"role\":\"assistant\"".getBytes(UTF_8);
    private static final byte[] MESSAGE_END = "}".getBytes(UTF_8);
    private static final byte[] CHOICE_END = "}".getBytes(UTF_8);
    private static final byte[] FINISH_REASON = ",\"finish_reason\":\"".getBytes(UTF_8);
    private static final byte[] FINISH_REASON_NULL = ",\"finish_reason\":null".getBytes(UTF_8);
    private static final String BLOCK_FINISH_REASON = "finish_reason";
    private static final String BLOCK_MODEL = "model";
    private static final String BLOCK_USAGE = "usage";
    private static final byte[] CONTENT_START = ",\"content\":\"".getBytes(UTF_8);
    private static final byte[] REFUSAL_START = ",\"refusal\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALLS_START = ",\"tool_calls\":[".getBytes(UTF_8);
    private static final byte[] TOOL_CALLS_END = "]".getBytes(UTF_8);
    private static final byte[] TOOL_CALL_START = "{\"id\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALL_TYPE = "\",\"type\":\"function\",\"function\":{\"name\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALL_ARGUMENTS = "\",\"arguments\":\"".getBytes(UTF_8);
    private static final byte[] TOOL_CALL_END = "\"}}".getBytes(UTF_8);
    private static final byte[] COMMA = ",".getBytes(UTF_8);
    private static final byte[] QUOTE = "\"".getBytes(UTF_8);
    private static final byte[] ROLE_CHUNK = "{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}"
        .getBytes(UTF_8);
    private static final byte[] DELTA_START = "{\"index\":0,\"delta\":{".getBytes(UTF_8);
    private static final byte[] DELTA_END = "},\"finish_reason\":null}".getBytes(UTF_8);
    private static final byte[] DELTA_CONTENT = "\"content\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_REFUSAL = "\"refusal\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_TOOL_CALLS = "\"tool_calls\":[{\"index\":".getBytes(UTF_8);
    private static final byte[] DELTA_TOOL_ID = ",\"id\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_TOOL_TYPE = "\",\"type\":\"function\",\"function\":{\"name\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_TOOL_ARGUMENTS_EMPTY = "\",\"arguments\":\"\"}}]".getBytes(UTF_8);
    private static final byte[] DELTA_TOOL_FUNCTION = ",\"function\":{\"arguments\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_TOOL_END = "\"}}]".getBytes(UTF_8);
    private static final byte[] DELTA_FINISH_START = "{\"index\":0,\"delta\":{},\"finish_reason\":\"".getBytes(UTF_8);
    private static final byte[] DELTA_FINISH_END = "\"}".getBytes(UTF_8);
    private static final byte[] USAGE_START = ",\"usage\":{".getBytes(UTF_8);
    private static final byte[] USAGE_END = "}".getBytes(UTF_8);
    private static final byte[] PROMPT_TOKENS = "\"prompt_tokens\":".getBytes(UTF_8);
    private static final byte[] COMPLETION_TOKENS = "\"completion_tokens\":".getBytes(UTF_8);
    private static final byte[] TOTAL_TOKENS = "\"total_tokens\":".getBytes(UTF_8);
    private static final byte[] PROMPT_DETAILS = "\"prompt_tokens_details\":{\"cached_tokens\":".getBytes(UTF_8);
    private static final byte[] COMPLETION_DETAILS = "\"completion_tokens_details\":{\"reasoning_tokens\":".getBytes(UTF_8);
    private static final byte[] DETAILS_END = "}".getBytes(UTF_8);
    private static final byte[] ERROR_START = "{\"error\":{\"message\":\"".getBytes(UTF_8);
    private static final byte[] ERROR_TYPE = "\",\"type\":\"".getBytes(UTF_8);
    private static final byte[] ERROR_PARAM = ",\"param\":".getBytes(UTF_8);
    private static final byte[] ERROR_CODE = ",\"code\":null}}".getBytes(UTF_8);
    private static final byte[] NULL = "null".getBytes(UTF_8);
    private static final byte[] DELTA_CLOSE = "}".getBytes(UTF_8);
    private static final byte[] CHUNK_FINISH = ",\"finish_reason\":null}".getBytes(UTF_8);
    private static final byte[] MEMBER_COLON = "\":".getBytes(UTF_8);
    private static final byte[] REPLACEMENT = "\\ufffd".getBytes(UTF_8);

    private final boolean streaming;
    private final byte[] id;
    private final byte[] model;
    private final byte[] carry;

    private MutableDirectBuffer out;
    private int position;

    private int[] eventOffsets;
    private int[] eventLengths;
    private int[] eventFlags;
    private String[] eventTypes;
    private int eventCount;
    private boolean eventOpen;
    private boolean eventStarted;
    private String eventType;
    private int eventStart;

    private int kind;
    private int member;
    private boolean contentWritten;
    private boolean refusalWritten;
    private int toolCalls;
    private int carried;
    private int state;
    private boolean finishReasonBlock;
    private boolean modelBlock;
    private boolean usageBlock;
    private boolean blockOpen;
    private boolean follow;
    private boolean chunkOpen;
    private boolean messageClosed;
    private boolean afterRender;
    private boolean afterWritten;

    LlmOpenaiResponseEncoder(
        boolean streaming,
        String id,
        String model)
    {
        this.eventOffsets = new int[EVENT_CAPACITY];
        this.eventLengths = new int[EVENT_CAPACITY];
        this.eventFlags = new int[EVENT_CAPACITY];
        this.eventTypes = new String[EVENT_CAPACITY];
        this.eventStart = NO_EVENT;
        this.streaming = streaming;
        this.state = STATE_PRELUDE;
        this.id = id != null ? escape(id) : null;
        this.model = model != null ? escape(model) : null;
        this.carry = new byte[3];
    }

    int eventCount()
    {
        return eventCount;
    }

    int eventOffset(
        int index)
    {
        return eventOffsets[index];
    }

    int eventLength(
        int index)
    {
        return eventLengths[index];
    }

    int eventFlags(
        int index)
    {
        return eventFlags[index];
    }

    String eventType(
        int index)
    {
        return eventTypes[index];
    }

    void flushEvents(
        int position)
    {
        if (eventOpen && eventStart != NO_EVENT)
        {
            emitEvent(position, false);
            eventStart = NO_EVENT;
        }
    }

    void clearEvents()
    {
        eventCount = 0;
    }

    int begin(
        MutableDirectBuffer out,
        int offset)
    {
        wrap(out, offset);

        if (streaming)
        {
            chunkStart();
            put(ROLE_CHUNK);
            chunkEnd();
        }
        else
        {
            put(OBJECT_START);
            if (id != null)
            {
                put(ID_START);
                put(id);
                put(QUOTE);
                put(COMMA);
            }
            put(OBJECT_COMPLETION);
        }

        return position - offset;
    }

    int section(
        MutableDirectBuffer out,
        int offset,
        LlmSectionType type,
        int message,
        String toolCallId,
        String toolName,
        boolean follow)
    {
        wrap(out, offset);

        kind = message == MESSAGE_FIRST && !messageClosed && state != STATE_TAIL ? kindOf(type) : KIND_SKIPPED;
        this.follow = follow && streaming && (kind == KIND_TEXT || kind == KIND_REFUSAL);

        if (!streaming && kind != KIND_SKIPPED && state == STATE_PRELUDE)
        {
            openChoices();
        }

        switch (kind)
        {
        case KIND_TEXT:
            sectionText();
            break;
        case KIND_REFUSAL:
            sectionRefusal();
            break;
        case KIND_TOOL_CALL:
            sectionToolCall(toolCallId, toolName);
            break;
        default:
            break;
        }

        return position - offset;
    }

    int data(
        MutableDirectBuffer out,
        int offset,
        DirectBuffer payload,
        int payloadOffset,
        int payloadLength,
        boolean fin)
    {
        wrap(out, offset);

        if (kind == KIND_TEXT || kind == KIND_REFUSAL || kind == KIND_TOOL_CALL)
        {
            if (streaming && follow && fin)
            {
                dataChunkOpen(payload, payloadOffset, payloadLength);
            }
            else if (streaming)
            {
                dataChunk(payload, payloadOffset, payloadLength);
            }
            else
            {
                putEscaped(payload, payloadOffset, payloadLength);
            }
        }

        return position - offset;
    }

    int blockBegin(
        MutableDirectBuffer out,
        int offset,
        String block,
        boolean render)
    {
        wrap(out, offset);

        blockOpen = render;

        if (render)
        {
            if (streaming)
            {
                chunkStart();
                put(CHOICES_END);
            }
            else
            {
                if (state == STATE_CHOICE)
                {
                    closeChoice(null);
                }

                modelBlock |= state == STATE_PRELUDE && BLOCK_MODEL.equals(block);
                usageBlock |= BLOCK_USAGE.equals(block);
            }

            put(COMMA);
            put(QUOTE);
            put(escape(block));
            put(MEMBER_COLON);
        }

        return position - offset;
    }

    int blockData(
        MutableDirectBuffer out,
        int offset,
        DirectBuffer payload,
        int payloadOffset,
        int payloadLength)
    {
        wrap(out, offset);

        if (blockOpen)
        {
            out.putBytes(position, payload, payloadOffset, payloadLength);
            position += payloadLength;
        }

        return position - offset;
    }

    int blockEnd(
        MutableDirectBuffer out,
        int offset)
    {
        wrap(out, offset);

        if (blockOpen && streaming)
        {
            put(OBJECT_END);
            eventEnd(position);
        }

        blockOpen = false;

        return position - offset;
    }

    int end(
        MutableDirectBuffer out,
        int offset)
    {
        wrap(out, offset);

        if (follow && !chunkOpen && (kind == KIND_TEXT || kind == KIND_REFUSAL))
        {
            dataChunkOpen(null, 0, 0);
        }

        if (streaming && carried > 0 && kind != KIND_NONE && kind != KIND_SKIPPED)
        {
            carried = 0;
            dataChunkStart();
            put(REPLACEMENT);
            dataChunkEnd();
        }

        if (!streaming && kind == KIND_TOOL_CALL)
        {
            put(TOOL_CALL_END);
        }

        kind = KIND_NONE;
        follow = false;

        return position - offset;
    }

    int afterBegin(
        MutableDirectBuffer out,
        int offset,
        String block,
        boolean container,
        boolean render)
    {
        wrap(out, offset);

        afterRender = render && (streaming
            ? chunkOpen
            : kind == KIND_NONE && state != STATE_TAIL && !(container && messageClosed));
        afterWritten = false;

        if (afterRender)
        {
            if (!streaming && state == STATE_PRELUDE)
            {
                openChoices();
            }

            if (!streaming && container)
            {
                closeMember();
            }
            else if (!streaming && !messageClosed)
            {
                closeMember();
                put(MESSAGE_END);
                messageClosed = true;
            }

            finishReasonBlock |= !streaming && BLOCK_FINISH_REASON.equals(block);

            put(COMMA);
            put(QUOTE);
            put(escape(block));
            put(MEMBER_COLON);
        }

        return position - offset;
    }

    int afterData(
        MutableDirectBuffer out,
        int offset,
        DirectBuffer payload,
        int payloadOffset,
        int payloadLength)
    {
        wrap(out, offset);

        if (afterRender && payloadLength > 0)
        {
            out.putBytes(position, payload, payloadOffset, payloadLength);
            position += payloadLength;
            afterWritten = true;
        }

        return position - offset;
    }

    int afterEnd(
        MutableDirectBuffer out,
        int offset)
    {
        wrap(out, offset);

        if (afterRender && !afterWritten)
        {
            put(NULL);
        }

        afterRender = false;

        if (chunkOpen)
        {
            closeChunk();
        }

        return position - offset;
    }

    int finish(
        MutableDirectBuffer out,
        int offset,
        String finishReason,
        int inputTokens,
        int cacheReadTokens,
        int outputTokens,
        int reasoningTokens,
        int totalTokens)
    {
        wrap(out, offset);

        final byte[] reason = finishReason(finishReason);

        if (chunkOpen)
        {
            closeChunk();
        }

        if (streaming)
        {
            chunkStart();
            put(DELTA_FINISH_START);
            put(reason);
            put(DELTA_FINISH_END);

            final boolean usage = inputTokens != NONE || outputTokens != NONE || totalTokens != NONE;

            chunkEnd();

            if (usage)
            {
                chunkStart();
                chunkUsageEnd(inputTokens, cacheReadTokens, outputTokens, reasoningTokens, totalTokens);
            }

            eventBegin(position, null);
            put(DONE);
            eventEnd(position);
        }
        else
        {
            if (state == STATE_PRELUDE)
            {
                openChoices();
            }

            if (state == STATE_CHOICE)
            {
                closeChoice(reason);
            }

            if (!usageBlock)
            {
                putUsage(inputTokens, cacheReadTokens, outputTokens, reasoningTokens, totalTokens);
            }

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

        if (chunkOpen)
        {
            closeChunk();
        }

        eventBegin(position, null);
        put(ERROR_START);
        put(escape(message != null ? message : ""));
        put(ERROR_TYPE);
        put(escape(type != null ? type : "server_error"));
        put(QUOTE);
        put(ERROR_PARAM);
        if (param != null)
        {
            put(QUOTE);
            put(escape(param));
            put(QUOTE);
        }
        else
        {
            put(NULL);
        }
        put(ERROR_CODE);
        eventEnd(position);

        return position - offset;
    }

    private static final byte[] OBJECT_END = "}".getBytes(UTF_8);

    private void eventBegin(
        int position,
        String type)
    {
        this.eventOpen = true;
        this.eventStarted = false;
        this.eventType = type;
        this.eventStart = position;
    }

    private void eventEnd(
        int position)
    {
        if (eventOpen)
        {
            emitEvent(position, true);
            eventOpen = false;
            eventStart = NO_EVENT;
        }
    }

    private void emitEvent(
        int position,
        boolean fin)
    {
        final int length = position - eventStart;

        if (length > 0 || fin && eventStarted)
        {
            if (eventCount == eventOffsets.length)
            {
                final int capacity = eventCount << 1;
                eventOffsets = Arrays.copyOf(eventOffsets, capacity);
                eventLengths = Arrays.copyOf(eventLengths, capacity);
                eventFlags = Arrays.copyOf(eventFlags, capacity);
                eventTypes = Arrays.copyOf(eventTypes, capacity);
            }

            final int init = eventStarted ? Flags.NONE : Flags.INIT;

            eventOffsets[eventCount] = eventStart;
            eventLengths[eventCount] = length;
            eventFlags[eventCount] = fin ? Flags.fin(init) : init;
            eventTypes[eventCount] = eventStarted ? null : eventType;
            eventCount++;

            eventStarted = true;
        }
    }

    private void wrap(
        MutableDirectBuffer out,
        int offset)
    {
        this.out = out;
        this.position = offset;
        if (eventOpen && eventStart == NO_EVENT)
        {
            eventStart = offset;
        }
    }

    private static int kindOf(
        LlmSectionType type)
    {
        return switch (type)
        {
        case ASSISTANT_TEXT -> KIND_TEXT;
        case ASSISTANT_REFUSAL -> KIND_REFUSAL;
        case TOOL_CALL -> KIND_TOOL_CALL;
        default -> KIND_SKIPPED;
        };
    }

    private void sectionText()
    {
        if (streaming)
        {
            carried = 0;
        }
        else if (member == MEMBER_CONTENT)
        {
            kind = KIND_TEXT;
        }
        else if (member == MEMBER_NONE && !contentWritten)
        {
            put(CONTENT_START);
            member = MEMBER_CONTENT;
            contentWritten = true;
        }
        else
        {
            kind = KIND_SKIPPED;
        }
    }

    private void sectionRefusal()
    {
        if (streaming)
        {
            carried = 0;
        }
        else if (member != MEMBER_REFUSAL)
        {
            if (refusalWritten)
            {
                kind = KIND_SKIPPED;
            }
            else
            {
                closeMember();
                put(REFUSAL_START);
                member = MEMBER_REFUSAL;
                refusalWritten = true;
            }
        }
    }

    private void sectionToolCall(
        String toolCallId,
        String toolName)
    {
        final int index = toolCalls++;

        if (streaming)
        {
            carried = 0;
            chunkStart();
            put(DELTA_START);
            put(DELTA_TOOL_CALLS);
            putNumber(index);
            put(DELTA_TOOL_ID);
            put(escape(toolCallId != null ? toolCallId : ""));
            put(DELTA_TOOL_TYPE);
            put(escape(toolName != null ? toolName : ""));
            put(DELTA_TOOL_ARGUMENTS_EMPTY);
            put(DELTA_END);
            chunkEnd();
        }
        else
        {
            if (member == MEMBER_TOOL_CALLS)
            {
                put(COMMA);
            }
            else
            {
                closeMember();
                put(TOOL_CALLS_START);
                member = MEMBER_TOOL_CALLS;
            }

            put(TOOL_CALL_START);
            put(escape(toolCallId != null ? toolCallId : ""));
            put(TOOL_CALL_TYPE);
            put(escape(toolName != null ? toolName : ""));
            put(TOOL_CALL_ARGUMENTS);
        }
    }

    private void openChoices()
    {
        if (!modelBlock && model != null)
        {
            put(COMMA);
            put(MODEL_START);
            put(model);
            put(QUOTE);
        }

        put(COMMA);
        put(CHOICES_START);
        put(MESSAGE_START);
        state = STATE_CHOICE;
    }

    private void closeChoice(
        byte[] reason)
    {
        closeMember();

        if (!messageClosed)
        {
            put(MESSAGE_END);
            messageClosed = true;
        }

        if (!finishReasonBlock)
        {
            if (reason != null)
            {
                put(FINISH_REASON);
                put(reason);
                put(QUOTE);
            }
            else
            {
                put(FINISH_REASON_NULL);
            }
        }

        put(CHOICE_END);
        put(CHOICES_END);
        state = STATE_TAIL;
    }

    private void closeMember()
    {
        switch (member)
        {
        case MEMBER_CONTENT, MEMBER_REFUSAL:
            put(QUOTE);
            break;
        case MEMBER_TOOL_CALLS:
            put(TOOL_CALLS_END);
            break;
        default:
            break;
        }

        member = MEMBER_NONE;
    }

    private void dataChunkOpen(
        DirectBuffer payload,
        int offset,
        int length)
    {
        chunkStart();
        put(DELTA_START);
        put(kind == KIND_TEXT ? DELTA_CONTENT : DELTA_REFUSAL);
        putEscaped(carry, 0, carried);
        carried = 0;

        if (length > 0)
        {
            putEscaped(payload, offset, length);
        }

        put(QUOTE);
        put(DELTA_CLOSE);
        chunkOpen = true;
    }

    private void closeChunk()
    {
        put(CHUNK_FINISH);
        put(CHOICES_END);
        put(OBJECT_END);
        eventEnd(position);
        chunkOpen = false;
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

    private void dataChunkStart()
    {
        chunkStart();
        put(DELTA_START);

        switch (kind)
        {
        case KIND_TEXT:
            put(DELTA_CONTENT);
            break;
        case KIND_REFUSAL:
            put(DELTA_REFUSAL);
            break;
        default:
            put(DELTA_TOOL_CALLS);
            putNumber(toolCalls - 1);
            put(DELTA_TOOL_FUNCTION);
            break;
        }
    }

    private void dataChunkEnd()
    {
        if (kind == KIND_TOOL_CALL)
        {
            put(DELTA_TOOL_END);
        }
        else
        {
            put(QUOTE);
        }

        put(DELTA_END);
        chunkEnd();
    }

    private void chunkStart()
    {
        eventBegin(position, null);
        put(OBJECT_START);
        if (id != null)
        {
            put(ID_START);
            put(id);
            put(QUOTE);
            put(COMMA);
        }
        put(OBJECT_CHUNK);
        if (model != null)
        {
            put(COMMA);
            put(MODEL_START);
            put(model);
            put(QUOTE);
        }
        put(COMMA);
        put(CHOICES_START);
    }

    private void chunkEnd()
    {
        put(CHOICES_END);
        put(OBJECT_END);
        eventEnd(position);
    }

    private void chunkUsageEnd(
        int inputTokens,
        int cacheReadTokens,
        int outputTokens,
        int reasoningTokens,
        int totalTokens)
    {
        put(CHOICES_END);
        putUsage(inputTokens, cacheReadTokens, outputTokens, reasoningTokens, totalTokens);
        put(OBJECT_END);
        eventEnd(position);
    }

    private void putUsage(
        int inputTokens,
        int cacheReadTokens,
        int outputTokens,
        int reasoningTokens,
        int totalTokens)
    {
        final int total = totalTokens != NONE || inputTokens == NONE || outputTokens == NONE
            ? totalTokens
            : inputTokens + outputTokens;

        if (inputTokens != NONE || outputTokens != NONE || total != NONE)
        {
            put(USAGE_START);

            boolean separator = false;

            if (inputTokens != NONE)
            {
                put(PROMPT_TOKENS);
                putNumber(inputTokens);
                separator = true;
            }

            if (outputTokens != NONE)
            {
                putSeparator(separator);
                put(COMPLETION_TOKENS);
                putNumber(outputTokens);
                separator = true;
            }

            if (total != NONE)
            {
                putSeparator(separator);
                put(TOTAL_TOKENS);
                putNumber(total);
                separator = true;
            }

            if (cacheReadTokens != NONE)
            {
                putSeparator(separator);
                put(PROMPT_DETAILS);
                putNumber(cacheReadTokens);
                put(DETAILS_END);
                separator = true;
            }

            if (reasoningTokens != NONE)
            {
                putSeparator(separator);
                put(COMPLETION_DETAILS);
                putNumber(reasoningTokens);
                put(DETAILS_END);
            }

            put(USAGE_END);
        }
    }

    private void putSeparator(
        boolean separator)
    {
        if (separator)
        {
            put(COMMA);
        }
    }

    private byte[] finishReason(
        String finishReason)
    {
        String reason = toolCalls > 0 ? "tool_calls" : "stop";

        if (finishReason != null)
        {
            reason = switch (finishReason)
            {
            case "tool-call" -> "tool_calls";
            case "content-filter" -> "content_filter";
            default -> finishReason;
            };
        }

        return escape(reason);
    }

    private void put(
        byte[] bytes)
    {
        out.putBytes(position, bytes);
        position += bytes.length;
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
