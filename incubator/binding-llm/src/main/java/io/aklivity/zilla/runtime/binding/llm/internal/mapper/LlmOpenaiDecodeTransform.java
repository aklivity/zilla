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
 * Decodes OpenAI's native chunk sequence into the canonical vocabulary, driven directly by real
 * {@link JsonEvent}s from a long-lived {@link io.aklivity.zilla.runtime.common.json.JsonPipeline} (one
 * instance per response stream, reused for every native chunk over the stream's lifetime -- see
 * {@link LlmCanonicalEmitter}). Replaces the old {@code ChunkWalker}/{@code Position}-enum loop over a
 * private {@code JsonParserEx}: the same walk now happens one real parser event at a time.
 * <p>
 * A streaming delta carries its per-choice payload under {@code delta}; a non-streaming whole document
 * carries the exact same shape (role/content/tool_calls at the same relative position) under {@code
 * message} instead -- {@link #onKeyName(JsonController, JsonSource)} treats the two as aliases, so the same
 * structural walk below decodes both without otherwise knowing which one it is looking at.
 * <p>
 * OpenAI's {@code index} counts tool calls only, unlike the canonical (Anthropic-shaped) block index, which
 * counts every content block including text; {@code blockIdByToolCallIndex} maps between the two
 * spaces for the stream's lifetime. A streaming delta always carries an explicit {@code index}; a
 * non-streaming {@code tool_calls} array entry never does, so entering a tool call defaults it to the
 * entry's own array position, overwritten by an explicit {@code index} key when one arrives. OpenAI also has
 * no explicit block-close event, so a canonical {@code blockEnd} is synthesized as soon as the current tool
 * call object closes (a streaming delta's {@code tool_calls} array holds at most one entry, so this fires at
 * most once per chunk there; a non-streaming document's array can hold several, each closing and reopening a
 * block in turn) or once the stream finishes.
 * <p>
 * The canonical TEXT block opens lazily, on the first real text content (or immediately before the first
 * tool-call block, if no text precedes it) rather than unconditionally at message start -- a tool-call-only
 * response must not produce a spurious empty leading text block.
 * <p>
 * {@code messageStarted}/{@code nextBlockId}/{@code openBlockId}/{@code blockIdByToolCallIndex} live for
 * the whole response stream, across every native chunk --
 * {@link #reset()} is never called between chunks under normal operation (see {@code LlmClientFactory}'s
 * document-boundary policy: {@code nextDocument()} advances between chunks without cascading a reset to this
 * stage), so these fields need no special preservation.
 */
final class LlmOpenaiDecodeTransform extends LlmCanonicalEmitter implements LlmDialectEvent
{
    private static final int NO_BLOCK = -1;

    private boolean messageStarted;
    private int nextBlockId;
    private int openBlockId = NO_BLOCK;
    private final Int2IntHashMap blockIdByToolCallIndex;

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

    private String chunkId;
    private String chunkModel;
    private int chunkChoiceIndex;
    private boolean chunkHasChoicePayload;
    private boolean chunkWholeDocument;
    private String chunkRole;
    private String chunkContent;
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
                onToolCallEnd();
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
            else if (matches(key, "message"))
            {
                choiceKeyPosition = Position.DELTA_KEY;
                chunkWholeDocument = true;
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
                position = Position.TOOL_CALL;
                toolCallKeyPosition = Position.ROOT;
                chunkToolCallIndex = toolCallsArrayIndex;
            }
        }
        else if (position == Position.TOOL_CALL)
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
                chunkHasChoicePayload = true;
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
    // own streaming semantics always resolve to, regardless of the order this chunk's own keys arrived in --
    // in particular, a whole non-streaming document's text always precedes its tool calls in the canonical
    // block order (matching the old decodeMessage()'s field-based assembly), regardless of which of
    // "content"/"tool_calls" the native document happens to carry first -- see flushTextIfPending(), called
    // from here and eagerly from onToolCallEnd() so a tool call never queues its block ahead of pending text.
    // A tool call's own block start/data/end already fired eagerly as its array entry closed (see
    // onToolCallEnd(), called from onStructural()) -- a non-streaming document's tool_calls array can hold
    // several entries, each needing its own block, so that cannot wait for this method the way a single
    // streaming delta's at-most-one entry could -- and messageStart must fire before any of them, so
    // ensureMessageStarted() (not just this method) is also called eagerly from onToolCallEnd(), never
    // deferred to this method the way it safely can be for a streaming delta's at-most-one entry.
    private void onDocumentEnd()
    {
        ensureMessageStarted();

        flushTextIfPending();

        if (chunkFinishReason != null)
        {
            closeOpenBlock();
            queueFinish(0, finishReason(chunkFinishReason));
        }

        if (chunkInputTokens != -1 || chunkOutputTokens != -1)
        {
            queueUsage(chunkInputTokens, chunkOutputTokens);
        }

        // A streaming response ends out-of-band, via its own literal terminator (OpenAI's "[DONE]",
        // matched and handled before this pipeline is ever invoked -- see LlmClientFactory) rather than a
        // queued canonical action; a whole non-streaming document has no such terminator, so this is the
        // only place its own TYPE_END ever queues, telling the encode sink to build and flush the document
        // it has been accumulating.
        if (chunkWholeDocument)
        {
            queueEnd();
        }
    }

    // A genuine streaming delta's very first chunk (role-announcing, no content yet) still opens the
    // canonical TEXT block eagerly here, exactly as every dialect's own real streaming protocol does
    // (Anthropic's own content_block_start always precedes its first text delta, empty-bodied or not) --
    // only a non-streaming whole document defers opening it until real text is seen (or skips it entirely
    // for a tool-call-only response), since its target native shape has no "empty placeholder block" concept
    // at all. chunkWholeDocument (true only when this chunk's payload arrived under "message", never
    // "delta") is exactly that signal.
    private void ensureMessageStarted()
    {
        if (!messageStarted)
        {
            messageStarted = true;
            String role = chunkHasChoicePayload ? orDefault(chunkRole, "assistant") : "assistant";
            queueMessageStart(chunkChoiceIndex, chunkId, chunkModel, role);
            if (!chunkWholeDocument)
            {
                ensureTextBlockOpen();
            }
        }
    }

    private void flushTextIfPending()
    {
        if (chunkContent != null)
        {
            if (!chunkContent.isEmpty())
            {
                ensureTextBlockOpen();
                queueData(chunkContent);
            }
            chunkContent = null;
        }
    }

    private void ensureTextBlockOpen()
    {
        if (openBlockId == NO_BLOCK)
        {
            int blockId = nextBlockId++;
            queueBlockStart(chunkChoiceIndex, blockId, LlmCanonicalBlockKind.TEXT, null, null);
            openBlockId = blockId;
        }
    }

    // Fires as soon as one tool_calls array entry closes -- a streaming delta's array holds at most one
    // entry, so this fires at most once per chunk there; a non-streaming document's array can hold several,
    // each opening (closing whatever block preceded it) and leaving its own block open in turn.
    private void onToolCallEnd()
    {
        ensureMessageStarted();
        flushTextIfPending();

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

        chunkToolCallId = null;
        chunkToolCallName = null;
        chunkToolCallArguments = null;
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

        chunkId = null;
        chunkModel = null;
        chunkChoiceIndex = 0;
        chunkHasChoicePayload = false;
        chunkWholeDocument = false;
        chunkRole = null;
        chunkContent = null;
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
