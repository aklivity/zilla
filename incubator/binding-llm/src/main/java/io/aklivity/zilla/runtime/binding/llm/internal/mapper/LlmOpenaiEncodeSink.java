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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Encodes the canonical vocabulary into OpenAI's native streaming chunk shape -- replaces the old
 * {@code LlmOpenaiEventMapper}'s {@code encode*} public-method-per-action shape with one
 * {@link #write(String)} dispatching on the accumulated canonical {@code "type"} value.
 * <p>
 * OpenAI's {@code index} counts tool calls only, unlike the canonical (Anthropic-shaped) block index; this
 * dialect never reads the canonical {@code blockId} back, since its own native shape addresses a tool call
 * purely by its own {@code index} sequence ({@code nextToolCallIndex}/{@code openToolCallIndex}), which
 * lives for the whole response stream, across every native chunk.
 */
final class LlmOpenaiEncodeSink extends LlmCanonicalEncodeSink implements LlmDialectTerminator
{
    private static final int NO_BLOCK = -1;
    private static final byte[] DONE_BYTES = "[DONE]".getBytes(UTF_8);

    private int nextToolCallIndex;
    private int openToolCallIndex = NO_BLOCK;

    LlmOpenaiEncodeSink(
        LlmNativeEventOutput output)
    {
        super(output);
    }

    @Override
    public void terminate()
    {
        end(DONE_BYTES);
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
            openToolCallIndex = NO_BLOCK;
            done = true;
            break;
        case LlmCanonicalEvent.TYPE_FINISH:
            done = run(finishSteps());
            break;
        case LlmCanonicalEvent.TYPE_USAGE:
            done = run(usageSteps());
            break;
        case LlmCanonicalEvent.TYPE_END:
            end(DONE_BYTES);
            done = true;
            break;
        default:
            done = true;
            break;
        }
        return done;
    }

    private List<BooleanSupplier> messageStartSteps()
    {
        final int choiceIndex = choiceIndex();
        final String id = id();
        final String model = model();
        final String role = orDefault(role(), "assistant");

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("object", "chat.completion.chunk"));
        steps.add(() -> tryWriteStartArray("choices"));
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("index", choiceIndex));
        steps.add(() -> tryWriteStartObject("delta"));
        steps.add(() -> tryWrite("role", role));
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWriteNull("finish_reason"));
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWrite("id", id));
        if (model != null)
        {
            steps.add(() -> tryWrite("model", model));
        }
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted(null));
        return steps;
    }

    private boolean writeBlockStart()
    {
        boolean done;
        if (inProgress())
        {
            done = run(List.of());
        }
        else if (isToolCall())
        {
            final int toolCallIndex = nextToolCallIndex++;
            openToolCallIndex = toolCallIndex;
            done = run(toolCallStartSteps(toolCallIndex));
        }
        else
        {
            openToolCallIndex = NO_BLOCK;
            done = true;
        }
        return done;
    }

    private List<BooleanSupplier> toolCallStartSteps(
        int toolCallIndex)
    {
        final int choiceIndex = choiceIndex();
        final String toolId = toolId();
        final String toolName = toolName();

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("object", "chat.completion.chunk"));
        steps.add(() -> tryWriteStartArray("choices"));
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("index", choiceIndex));
        steps.add(() -> tryWriteStartObject("delta"));
        steps.add(() -> tryWriteStartArray("tool_calls"));
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("index", toolCallIndex));
        steps.add(() -> tryWrite("type", "function"));
        steps.add(() -> tryWriteStartObject("function"));
        steps.add(() -> tryWrite("arguments", ""));
        if (toolName != null)
        {
            steps.add(() -> tryWrite("name", toolName));
        }
        steps.add(this::tryWriteEnd);
        if (toolId != null)
        {
            steps.add(() -> tryWrite("id", toolId));
        }
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWriteNull("finish_reason"));
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted(null));
        return steps;
    }

    private List<BooleanSupplier> dataSteps()
    {
        final String text = text();
        final boolean toolCall = openToolCallIndex != NO_BLOCK;
        final int toolCallIndex = openToolCallIndex;

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("object", "chat.completion.chunk"));
        steps.add(() -> tryWriteStartArray("choices"));
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("index", 0));
        steps.add(() -> tryWriteStartObject("delta"));
        if (toolCall)
        {
            steps.add(() -> tryWriteStartArray("tool_calls"));
            steps.add(this::tryWriteStartObject);
            steps.add(() -> tryWrite("index", toolCallIndex));
            steps.add(() -> tryWriteStartObject("function"));
            steps.add(() -> tryWrite("arguments", text));
            steps.add(this::tryWriteEnd);
            steps.add(this::tryWriteEnd);
            steps.add(this::tryWriteEnd);
        }
        else
        {
            steps.add(() -> tryWrite("content", text));
        }
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWriteNull("finish_reason"));
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted(null));
        return steps;
    }

    private List<BooleanSupplier> finishSteps()
    {
        final int choiceIndex = choiceIndex();
        final String finishReasonText = finishReasonText(reason());

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("object", "chat.completion.chunk"));
        steps.add(() -> tryWriteStartArray("choices"));
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("index", choiceIndex));
        steps.add(() -> tryWriteStartObject("delta"));
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWrite("finish_reason", finishReasonText));
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted(null));
        return steps;
    }

    private List<BooleanSupplier> usageSteps()
    {
        final int inputTokens = inputTokens();
        final int outputTokens = outputTokens();

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("object", "chat.completion.chunk"));
        steps.add(() -> tryWriteStartArray("choices"));
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWriteStartObject("usage"));
        if (inputTokens >= 0)
        {
            steps.add(() -> tryWrite("prompt_tokens", inputTokens));
        }
        if (outputTokens >= 0)
        {
            steps.add(() -> tryWrite("completion_tokens", outputTokens));
        }
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted(null));
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
}
