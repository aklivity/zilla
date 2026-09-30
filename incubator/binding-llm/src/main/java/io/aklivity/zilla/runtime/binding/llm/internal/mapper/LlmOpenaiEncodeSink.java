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

import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

/**
 * Encodes the canonical vocabulary into OpenAI's native chunk shape, dispatching on the accumulated
 * canonical {@code "type"} in {@link #write(String)}. {@link #streaming()} (cached at
 * {@code TYPE_MESSAGE_START}) picks between emitting one native chunk per canonical action (streaming)
 * or accumulating into {@code held*} fields and writing the whole document once at {@code TYPE_END}
 * (non-streaming).
 */
final class LlmOpenaiEncodeSink extends LlmCanonicalEncodeSink implements LlmDialectTerminator
{
    private static final int NO_BLOCK = -1;
    private static final byte[] DONE_BYTES = "[DONE]".getBytes(UTF_8);
    private static final int MAX_DATA_FRAGMENT_CHARS = 1024;

    private int nextToolCallIndex;
    private int openToolCallIndex = NO_BLOCK;
    private int dataCursor;

    private boolean streaming = true;

    private String heldId;
    private String heldModel;
    private String heldRole;
    private final StringBuilder heldText;
    private final List<HeldToolCall> heldToolCalls;
    private HeldToolCall openHeldToolCall;
    private String heldFinishReasonText;
    private int heldInputTokens = -1;
    private int heldOutputTokens = -1;

    LlmOpenaiEncodeSink(
        JsonEnvelope envelope,
        LlmNativeEventOutput output)
    {
        super(envelope, output);
        this.heldText = new StringBuilder();
        this.heldToolCalls = new ArrayList<>();
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
            done = onMessageStart();
            break;
        case LlmCanonicalEvent.TYPE_BLOCK_START:
            done = onBlockStart();
            break;
        case LlmCanonicalEvent.TYPE_DATA:
            done = onData();
            break;
        case LlmCanonicalEvent.TYPE_BLOCK_END:
            done = onBlockEnd();
            break;
        case LlmCanonicalEvent.TYPE_FINISH:
            done = onFinish();
            break;
        case LlmCanonicalEvent.TYPE_USAGE:
            done = onUsage();
            break;
        case LlmCanonicalEvent.TYPE_END:
            done = onEnd();
            break;
        default:
            done = true;
            break;
        }
        return done;
    }

    private boolean onMessageStart()
    {
        boolean done;
        streaming = streaming();

        if (streaming)
        {
            done = run(messageStartSteps());
        }
        else
        {
            heldId = id();
            heldModel = model();
            heldRole = role();
            done = true;
        }
        return done;
    }

    private boolean onBlockStart()
    {
        return streaming ? writeBlockStart() : holdBlockStart();
    }

    private boolean holdBlockStart()
    {
        if (isToolCall())
        {
            openHeldToolCall = new HeldToolCall(toolId(), toolName());
            heldToolCalls.add(openHeldToolCall);
        }
        else
        {
            openHeldToolCall = null;
        }
        return true;
    }

    private boolean onData()
    {
        boolean done;
        if (streaming)
        {
            done = writeDataFragment();
        }
        else
        {
            if (openHeldToolCall != null)
            {
                openHeldToolCall.arguments.append(text());
            }
            else
            {
                heldText.append(text());
            }
            done = true;
        }
        return done;
    }

    private boolean onBlockEnd()
    {
        boolean done;
        if (streaming)
        {
            openToolCallIndex = NO_BLOCK;
            done = true;
        }
        else
        {
            openHeldToolCall = null;
            done = true;
        }
        return done;
    }

    private boolean onFinish()
    {
        boolean done;
        if (streaming)
        {
            done = run(finishSteps());
        }
        else
        {
            heldFinishReasonText = finishReasonText(reason());
            done = true;
        }
        return done;
    }

    private boolean onUsage()
    {
        boolean done;
        if (streaming)
        {
            done = run(usageSteps());
        }
        else
        {
            heldInputTokens = inputTokens();
            heldOutputTokens = outputTokens();
            done = true;
        }
        return done;
    }

    private boolean onEnd()
    {
        boolean done;
        if (streaming)
        {
            end(DONE_BYTES);
            done = true;
        }
        else
        {
            done = run(wholeDocumentSteps());
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

    private boolean writeDataFragment()
    {
        final String text = text();
        final boolean toolCall = openToolCallIndex != NO_BLOCK;
        final int toolCallIndex = openToolCallIndex;

        final int start = dataCursor;
        final int end = Math.min(text.length(), start + MAX_DATA_FRAGMENT_CHARS);
        final boolean lastFragment = end >= text.length();

        boolean fragmentDone = run(dataSteps(text.substring(start, end), toolCall, toolCallIndex));

        boolean done;
        if (!fragmentDone)
        {
            done = false;
        }
        else if (lastFragment)
        {
            dataCursor = 0;
            done = true;
        }
        else
        {
            dataCursor = end;
            done = false;
        }
        return done;
    }

    private List<BooleanSupplier> dataSteps(
        String text,
        boolean toolCall,
        int toolCallIndex)
    {
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

    private List<BooleanSupplier> wholeDocumentSteps()
    {
        final String id = heldId;
        final String model = heldModel;
        final String role = orDefault(heldRole, "assistant");
        final String text = heldText.length() > 0 ? heldText.toString() : null;
        final List<HeldToolCall> toolCalls = heldToolCalls;
        final String finishReasonText = heldFinishReasonText;
        final int inputTokens = heldInputTokens;
        final int outputTokens = heldOutputTokens;

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("object", "chat.completion"));
        if (id != null)
        {
            steps.add(() -> tryWrite("id", id));
        }
        if (model != null)
        {
            steps.add(() -> tryWrite("model", model));
        }
        steps.add(() -> tryWriteStartArray("choices"));
        steps.add(this::tryWriteStartObject);
        steps.add(() -> tryWrite("index", 0));
        steps.add(() -> tryWriteStartObject("message"));
        steps.add(() -> tryWrite("role", role));
        steps.add(text != null ? () -> tryWrite("content", text) : () -> tryWriteNull("content"));
        if (!toolCalls.isEmpty())
        {
            steps.add(() -> tryWriteStartArray("tool_calls"));
            for (HeldToolCall toolCall : toolCalls)
            {
                steps.add(this::tryWriteStartObject);
                if (toolCall.id != null)
                {
                    steps.add(() -> tryWrite("id", toolCall.id));
                }
                steps.add(() -> tryWrite("type", "function"));
                steps.add(() -> tryWriteStartObject("function"));
                if (toolCall.name != null)
                {
                    steps.add(() -> tryWrite("name", toolCall.name));
                }
                steps.add(() -> tryWrite("arguments", toolCall.arguments.toString()));
                steps.add(this::tryWriteEnd);
                steps.add(this::tryWriteEnd);
            }
            steps.add(this::tryWriteEnd);
        }
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWrite("finish_reason", finishReasonText));
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        if (inputTokens >= 0 || outputTokens >= 0)
        {
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
        }
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

    private static final class HeldToolCall
    {
        private final String id;
        private final String name;
        private final StringBuilder arguments;

        private HeldToolCall(
            String id,
            String name)
        {
            this.id = id;
            this.name = name;
            this.arguments = new StringBuilder();
        }
    }
}
