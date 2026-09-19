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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Encodes the canonical vocabulary into Anthropic's native streaming event shape -- replaces the old
 * {@code LlmAnthropicEventMapper}'s {@code encode*} public-method-per-action shape with one
 * {@link #write(String)} dispatching on the accumulated canonical {@code "type"} value.
 * <p>
 * {@code openBlockType}/{@code openBlockId}/{@code heldOutputTokens}/{@code finishSent} live for the whole
 * response stream, across every native chunk.
 */
final class LlmAnthropicEncodeSink extends LlmCanonicalEncodeSink implements LlmDialectTerminator
{
    private LlmCanonicalBlockKind openBlockType;
    private int openBlockId;

    private int heldOutputTokens = -1;
    private boolean finishSent;

    LlmAnthropicEncodeSink(
        LlmNativeEventOutput output)
    {
        super(output);
    }

    // Anthropic's own stream termination (message_stop) is itself a JSON document that reaches this sink
    // like any other canonical action -- but the SOURCE dialect this sink is paired with may terminate
    // out-of-band instead (e.g. OpenAI's literal "[DONE]", bypassed around the pipeline entirely), and an
    // Anthropic-speaking target still needs its own native message_stop for that termination. Drives the
    // exact same write this sink would produce for a canonical "end" action.
    @Override
    public void terminate()
    {
        run(endSteps());
    }

    @Override
    protected boolean write(
        String type)
    {
        boolean done;
        switch (type)
        {
        case LlmCanonicalEvent.TYPE_MESSAGE_START:
            done = run(messageStartSteps());
            break;
        case LlmCanonicalEvent.TYPE_BLOCK_START:
            done = writeBlockStart();
            break;
        case LlmCanonicalEvent.TYPE_DATA:
            done = run(dataSteps());
            break;
        case LlmCanonicalEvent.TYPE_BLOCK_END:
            done = writeBlockEnd();
            break;
        case LlmCanonicalEvent.TYPE_FINISH:
            done = writeFinish();
            break;
        case LlmCanonicalEvent.TYPE_USAGE:
            if (!finishSent)
            {
                heldOutputTokens = outputTokens();
            }
            done = true;
            break;
        case LlmCanonicalEvent.TYPE_END:
            done = run(endSteps());
            break;
        default:
            done = true;
            break;
        }
        return done;
    }

    private List<BooleanSupplier> messageStartSteps()
    {
        final String id = id();
        final String model = model();
        final String role = orDefault(role(), "assistant");

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("type", "message_start"));
        steps.add(() -> tryWriteStartObject("message"));
        steps.add(() -> tryWrite("id", id));
        steps.add(() -> tryWrite("type", "message"));
        steps.add(() -> tryWrite("role", role));
        if (model != null)
        {
            steps.add(() -> tryWrite("model", model));
        }
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted("message_start"));
        return steps;
    }

    private boolean writeBlockStart()
    {
        boolean done;
        if (inProgress())
        {
            done = run(List.of());
        }
        else
        {
            openBlockType = isToolCall() ? LlmCanonicalBlockKind.TOOL_CALL : LlmCanonicalBlockKind.TEXT;
            openBlockId = blockId();
            done = run(blockStartSteps(openBlockType, blockId(), toolId(), toolName()));
        }
        return done;
    }

    private List<BooleanSupplier> blockStartSteps(
        LlmCanonicalBlockKind kind,
        int blockId,
        String toolId,
        String toolName)
    {
        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("type", "content_block_start"));
        steps.add(() -> tryWrite("index", blockId));
        steps.add(() -> tryWriteStartObject("content_block"));
        if (kind == LlmCanonicalBlockKind.TOOL_CALL)
        {
            steps.add(() -> tryWrite("type", "tool_use"));
            if (toolId != null)
            {
                steps.add(() -> tryWrite("id", toolId));
            }
            if (toolName != null)
            {
                steps.add(() -> tryWrite("name", toolName));
            }
        }
        else
        {
            steps.add(() -> tryWrite("type", "text"));
            steps.add(() -> tryWrite("text", ""));
        }
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted("content_block_start"));
        return steps;
    }

    private List<BooleanSupplier> dataSteps()
    {
        final String text = text();
        final boolean toolCall = openBlockType == LlmCanonicalBlockKind.TOOL_CALL;
        final int blockId = openBlockId;

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("type", "content_block_delta"));
        steps.add(() -> tryWrite("index", blockId));
        steps.add(() -> tryWriteStartObject("delta"));
        if (toolCall)
        {
            steps.add(() -> tryWrite("type", "input_json_delta"));
            steps.add(() -> tryWrite("partial_json", text));
        }
        else
        {
            steps.add(() -> tryWrite("type", "text_delta"));
            steps.add(() -> tryWrite("text", text));
        }
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted("content_block_delta"));
        return steps;
    }

    private boolean writeBlockEnd()
    {
        if (!inProgress())
        {
            openBlockType = null;
        }
        return run(blockEndSteps(blockId()));
    }

    private List<BooleanSupplier> blockEndSteps(
        int blockId)
    {
        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("type", "content_block_stop"));
        steps.add(() -> tryWrite("index", blockId));
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted("content_block_stop"));
        return steps;
    }

    private boolean writeFinish()
    {
        final String stopReason = stopReason(reason());
        final int outputTokens = Math.max(heldOutputTokens, 0);

        boolean done = run(finishSteps(stopReason, outputTokens));
        if (done)
        {
            finishSent = true;
        }
        return done;
    }

    private List<BooleanSupplier> finishSteps(
        String stopReason,
        int outputTokens)
    {
        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("type", "message_delta"));
        steps.add(() -> tryWriteStartObject("delta"));
        steps.add(() -> tryWrite("stop_reason", stopReason));
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWriteStartObject("usage"));
        steps.add(() -> tryWrite("output_tokens", outputTokens));
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted("message_delta"));
        return steps;
    }

    private List<BooleanSupplier> endSteps()
    {
        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("type", "message_stop"));
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted("message_stop"));
        return steps;
    }

    private boolean emitted(
        String nativeEventName)
    {
        emit(nativeEventName);
        return true;
    }

    private static String orDefault(
        String value,
        String fallback)
    {
        return value != null ? value : fallback;
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
}
