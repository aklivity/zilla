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

import org.agrona.collections.Int2IntHashMap;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * Decodes OpenAI's native streaming chunk sequence into the canonical vocabulary, driven directly by real
 * {@link JsonEvent}s from a long-lived {@link io.aklivity.zilla.runtime.common.json.JsonPipeline} (one
 * instance per response stream, reused for every native chunk over the stream's lifetime -- see
 * {@link LlmCanonicalEmitter}). Replaces the old {@code ChunkWalker}/{@code Position}-enum loop over a
 * private {@code JsonParserEx}: the same walk now happens one real parser event at a time.
 * <p>
 * OpenAI's {@code index} counts tool calls only, unlike the canonical (Anthropic-shaped) block index, which
 * counts every content block including text; {@code blockIdByToolCallIndex} translates between the two
 * spaces for the stream's lifetime. OpenAI also has no explicit block-close event, so a canonical
 * {@code blockEnd} is synthesized lazily, only once the next tool call starts or the stream finishes.
 * <p>
 * {@code messageStarted}/{@code nextBlockId}/{@code openBlockId}/{@code blockIdByToolCallIndex}/
 * {@code nextToolCallIndex}/{@code openToolCallIndex} live for the whole response stream, across every
 * native chunk -- {@link #reset()} is never called between chunks under normal operation (see
 * {@code LlmClientFactory}'s document-boundary policy: {@code nextDocument()} advances between chunks
 * without cascading a reset to this stage), so these fields need no special preservation.
 */
final class LlmOpenaiDecodeTransform extends LlmCanonicalEmitter implements LlmDialectEvent
{
    private static final int NO_BLOCK = -1;

    private boolean messageStarted;
    private int nextBlockId = 1;
    private int openBlockId = NO_BLOCK;
    private final Int2IntHashMap blockIdByToolCallIndex;

    private int nextToolCallIndex;
    private int openToolCallIndex = NO_BLOCK;

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

    private String chunkId;
    private String chunkModel;
    private int chunkChoiceIndex;
    private boolean chunkHasDelta;
    private String chunkRole;
    private String chunkContent;
    private boolean chunkHasToolCall;
    private int chunkToolCallIndex;
    private String chunkToolCallId;
    private String chunkToolCallName;
    private String chunkToolCallArguments;
    private String chunkFinishReason;
    private int chunkInputTokens = -1;
    private int chunkOutputTokens = -1;

    LlmOpenaiDecodeTransform()
    {
        this.blockIdByToolCallIndex = new Int2IntHashMap(NO_BLOCK);
    }

    // OpenAI's decode behavior does not depend on the native SSE event name (its "[DONE]" terminator is
    // handled out-of-band, before this pipeline is ever invoked -- see LlmClientFactory), so this is a no-op.
    @Override
    public void event(
        String name)
    {
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status;
        if (event == JsonEvent.END_DOCUMENT)
        {
            onDocumentEnd();
            status = fireQueued(sink);
            clearChunk();
        }
        else
        {
            onEvent(control, source, event);
            status = Status.ADVANCED;
        }
        return status;
    }

    private void onEvent(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        onStructural(control, source, event);
        onContainerTransition(source, event);
        onLeafValue(control, source, event);
    }

    private void onStructural(
        JsonController control,
        JsonSource source,
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
            onKeyName(control, source);
            break;
        default:
            break;
        }
    }

    private void onKeyName(
        JsonController control,
        JsonSource source)
    {
        if (source.deferredBytes())
        {
            control.consumed(0);
            return;
        }

        CharSequence key = source.getStringView();
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

    private void onContainerTransition(
        JsonSource source,
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
            if (event == JsonEvent.VALUE_STRING && !source.deferredBytes())
            {
                chunkId = source.getString();
                position = Position.ROOT;
            }
            break;
        case ROOT_MODEL:
            if (event == JsonEvent.VALUE_STRING && !source.deferredBytes())
            {
                chunkModel = source.getString();
                position = Position.ROOT;
            }
            break;
        default:
            break;
        }
    }

    private void onLeafValue(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        if (source.deferredBytes())
        {
            control.consumed(0);
            return;
        }

        if (position == Position.CHOICE && inChoice0)
        {
            onChoiceLeafValue(source, event);
        }
        else if (position == Position.DELTA)
        {
            onDeltaLeafValue(source, event);
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
                    chunkHasToolCall = true;
                }
            }
        }
        else if (position == Position.TOOL_CALL && inToolCall0)
        {
            onToolCallLeafValue(source, event);
        }
        else if (position == Position.FUNCTION)
        {
            onFunctionLeafValue(source, event);
        }
        else if (position == Position.USAGE)
        {
            onUsageLeafValue(source, event);
        }
    }

    private void onChoiceLeafValue(
        JsonSource source,
        JsonEvent event)
    {
        switch (choiceKeyPosition)
        {
        case CHOICE_INDEX:
            if (event == JsonEvent.VALUE_NUMBER)
            {
                chunkChoiceIndex = source.getInt();
                choiceKeyPosition = Position.ROOT;
            }
            break;
        case DELTA_KEY:
            if (event == JsonEvent.START_OBJECT)
            {
                chunkHasDelta = true;
                position = Position.DELTA;
                deltaKeyPosition = Position.ROOT;
            }
            break;
        case FINISH_REASON:
            if (event == JsonEvent.VALUE_STRING)
            {
                chunkFinishReason = source.getString();
                choiceKeyPosition = Position.ROOT;
            }
            break;
        default:
            break;
        }
    }

    private void onDeltaLeafValue(
        JsonSource source,
        JsonEvent event)
    {
        switch (deltaKeyPosition)
        {
        case DELTA_ROLE:
            if (event == JsonEvent.VALUE_STRING)
            {
                chunkRole = source.getString();
                deltaKeyPosition = Position.ROOT;
            }
            break;
        case DELTA_CONTENT:
            if (event == JsonEvent.VALUE_STRING)
            {
                chunkContent = source.getString();
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
        JsonSource source,
        JsonEvent event)
    {
        switch (toolCallKeyPosition)
        {
        case TOOL_CALL_INDEX:
            if (event == JsonEvent.VALUE_NUMBER)
            {
                chunkToolCallIndex = source.getInt();
                toolCallKeyPosition = Position.ROOT;
            }
            break;
        case TOOL_CALL_ID:
            if (event == JsonEvent.VALUE_STRING)
            {
                chunkToolCallId = source.getString();
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
        JsonSource source,
        JsonEvent event)
    {
        switch (functionKeyPosition)
        {
        case FUNCTION_NAME:
            if (event == JsonEvent.VALUE_STRING)
            {
                chunkToolCallName = source.getString();
                functionKeyPosition = Position.ROOT;
            }
            break;
        case FUNCTION_ARGUMENTS:
            if (event == JsonEvent.VALUE_STRING)
            {
                chunkToolCallArguments = source.getString();
                functionKeyPosition = Position.ROOT;
            }
            break;
        default:
            break;
        }
    }

    private void onUsageLeafValue(
        JsonSource source,
        JsonEvent event)
    {
        switch (usageKeyPosition)
        {
        case USAGE_PROMPT_TOKENS:
            if (event == JsonEvent.VALUE_NUMBER)
            {
                chunkInputTokens = source.getInt();
                usageKeyPosition = Position.ROOT;
            }
            break;
        case USAGE_COMPLETION_TOKENS:
            if (event == JsonEvent.VALUE_NUMBER)
            {
                chunkOutputTokens = source.getInt();
                usageKeyPosition = Position.ROOT;
            }
            break;
        default:
            break;
        }
    }

    // Queues canonical actions from the fully-accumulated chunk, in the same fixed order the dialect's
    // own streaming semantics always resolve to, regardless of the order this chunk's own keys arrived in.
    private void onDocumentEnd()
    {
        if (!messageStarted)
        {
            messageStarted = true;
            String role = chunkHasDelta ? orDefault(chunkRole, "assistant") : "assistant";
            queueMessageStart(chunkChoiceIndex, chunkId, chunkModel, role);
            queueBlockStart(chunkChoiceIndex, 0, LlmCanonicalBlockKind.TEXT, null, null);
            openBlockId = 0;
        }

        if (chunkContent != null && !chunkContent.isEmpty())
        {
            queueData(chunkContent);
        }

        if (chunkHasToolCall)
        {
            onToolCallDelta();
        }

        if (chunkFinishReason != null)
        {
            closeOpenBlock();
            queueFinish(0, finishReason(chunkFinishReason));
        }

        if (chunkInputTokens != -1 || chunkOutputTokens != -1)
        {
            queueUsage(chunkInputTokens, chunkOutputTokens);
        }
    }

    private void onToolCallDelta()
    {
        int blockId = blockIdByToolCallIndex.get(chunkToolCallIndex);
        if (blockId == NO_BLOCK)
        {
            closeOpenBlock();

            blockId = nextBlockId++;
            blockIdByToolCallIndex.put(chunkToolCallIndex, blockId);

            queueBlockStart(0, blockId, LlmCanonicalBlockKind.TOOL_CALL, chunkToolCallId, chunkToolCallName);
            openBlockId = blockId;
        }

        if (chunkToolCallArguments != null && !chunkToolCallArguments.isEmpty())
        {
            queueData(chunkToolCallArguments);
        }
    }

    private void closeOpenBlock()
    {
        if (openBlockId != NO_BLOCK)
        {
            queueBlockEnd(0, openBlockId);
            openBlockId = NO_BLOCK;
        }
    }

    private void clearChunk()
    {
        depth = 0;
        position = Position.ROOT;
        choiceKeyPosition = Position.ROOT;
        deltaKeyPosition = Position.ROOT;
        toolCallKeyPosition = Position.ROOT;
        functionKeyPosition = Position.ROOT;
        usageKeyPosition = Position.ROOT;
        choicesArrayIndex = -1;
        toolCallsArrayIndex = -1;
        inChoice0 = false;
        inToolCall0 = false;

        chunkId = null;
        chunkModel = null;
        chunkChoiceIndex = 0;
        chunkHasDelta = false;
        chunkRole = null;
        chunkContent = null;
        chunkHasToolCall = false;
        chunkToolCallIndex = 0;
        chunkToolCallId = null;
        chunkToolCallName = null;
        chunkToolCallArguments = null;
        chunkFinishReason = null;
        chunkInputTokens = -1;
        chunkOutputTokens = -1;
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

    private static String orDefault(
        String value,
        String fallback)
    {
        return value != null ? value : fallback;
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
