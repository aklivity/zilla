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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectTerminator;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmNativeEventOutput;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

/**
 * Encodes the canonical vocabulary into Anthropic's native event shape, dispatching on the accumulated
 * canonical {@code "type"} in {@link #write(String)}. {@link #streaming()} (cached at
 * {@code TYPE_MESSAGE_START}) picks between emitting one native chunk per canonical action (streaming)
 * or accumulating into {@code doc*} fields and writing the whole document once at {@code TYPE_END}
 * (non-streaming).
 */
public final class LlmAnthropicEncodeSink extends LlmCanonicalEncodeSink implements LlmDialectTerminator
{
    private static final JsonObject EMPTY_INPUT = Json.createObjectBuilder().build();
    private static final int MAX_DATA_FRAGMENT_CHARS = 1024;

    private LlmCanonicalBlockKind openBlockType;
    private int openBlockId;
    private int dataCursor;

    private int heldOutputTokens = -1;
    private boolean finishSent;

    private boolean streaming = true;

    private String docId;
    private String docModel;
    private String docRole;
    private final List<HeldBlock> docBlocks;
    private HeldBlock openDocBlock;
    private String docStopReason;
    private int docInputTokens = -1;
    private int docOutputTokens = -1;

    public LlmAnthropicEncodeSink(
        JsonEnvelope envelope,
        LlmNativeEventOutput output)
    {
        super(envelope, output);
        this.docBlocks = new ArrayList<>();
    }

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
            docId = id();
            docModel = model();
            docRole = role();
            done = true;
        }
        return done;
    }

    private boolean onBlockStart()
    {
        boolean done;
        if (streaming)
        {
            done = writeBlockStart();
        }
        else
        {
            openDocBlock = new HeldBlock(isToolCall() ? LlmCanonicalBlockKind.TOOL_CALL : LlmCanonicalBlockKind.TEXT,
                toolId(), toolName());
            docBlocks.add(openDocBlock);
            done = true;
        }
        return done;
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
            openDocBlock.text.append(text());
            done = true;
        }
        return done;
    }

    private boolean onBlockEnd()
    {
        boolean done;
        if (streaming)
        {
            done = writeBlockEnd();
        }
        else
        {
            openDocBlock = null;
            done = true;
        }
        return done;
    }

    private boolean onFinish()
    {
        boolean done;
        if (streaming)
        {
            done = writeFinish();
        }
        else
        {
            docStopReason = stopReason(reason());
            done = true;
        }
        return done;
    }

    private boolean onUsage()
    {
        boolean done;
        if (streaming)
        {
            if (!finishSent)
            {
                heldOutputTokens = outputTokens();
            }
            done = true;
        }
        else
        {
            docInputTokens = inputTokens();
            docOutputTokens = outputTokens();
            done = true;
        }
        return done;
    }

    private boolean onEnd()
    {
        return streaming ? run(endSteps()) : run(wholeDocumentSteps());
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
        if (id != null)
        {
            steps.add(() -> tryWrite("id", id));
        }
        steps.add(() -> tryWrite("type", "message"));
        steps.add(() -> tryWrite("role", role));
        if (model != null)
        {
            steps.add(() -> tryWrite("model", model));
        }
        // A real Anthropic client (anthropic-sdk-python's streaming accumulator, confirmed
        // against it directly) initializes its per-message state from message_start's
        // content/stop_reason/stop_sequence/usage and then patches usage.output_tokens on it
        // in place at message_delta -- omitting any of these crashes the accumulator, even
        // though input_tokens isn't known yet for a source dialect that discloses it late
        // (OpenAI, via LlmUsageFlushEx, never before its terminal chunk)
        steps.add(() -> tryWriteStartArray("content"));
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWriteNull("stop_reason"));
        steps.add(() -> tryWriteNull("stop_sequence"));
        steps.add(() -> tryWriteStartObject("usage"));
        steps.add(() -> tryWrite("input_tokens", 0));
        steps.add(() -> tryWrite("output_tokens", 0));
        steps.add(this::tryWriteEnd);
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

    private boolean writeDataFragment()
    {
        final String text = text();
        final boolean toolCall = openBlockType == LlmCanonicalBlockKind.TOOL_CALL;
        final int blockId = openBlockId;

        final int start = dataCursor;
        final int end = Math.min(text.length(), start + MAX_DATA_FRAGMENT_CHARS);
        final boolean lastFragment = end >= text.length();

        boolean fragmentDone = run(dataSteps(text.substring(start, end), toolCall, blockId));

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
        int blockId)
    {
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

    private List<BooleanSupplier> wholeDocumentSteps()
    {
        final String id = docId;
        final String model = docModel;
        final String role = orDefault(docRole, "assistant");
        final List<HeldBlock> blocks = docBlocks;
        final String stopReason = docStopReason;
        final int inputTokens = docInputTokens;
        final int outputTokens = docOutputTokens;

        List<BooleanSupplier> steps = new ArrayList<>();
        steps.add(this::tryWriteStartObject);
        if (id != null)
        {
            steps.add(() -> tryWrite("id", id));
        }
        steps.add(() -> tryWrite("type", "message"));
        steps.add(() -> tryWrite("role", role));
        if (model != null)
        {
            steps.add(() -> tryWrite("model", model));
        }
        steps.add(() -> tryWriteStartArray("content"));
        for (HeldBlock block : blocks)
        {
            steps.add(this::tryWriteStartObject);
            if (block.kind == LlmCanonicalBlockKind.TOOL_CALL)
            {
                steps.add(() -> tryWrite("type", "tool_use"));
                if (block.toolId != null)
                {
                    steps.add(() -> tryWrite("id", block.toolId));
                }
                if (block.toolName != null)
                {
                    steps.add(() -> tryWrite("name", block.toolName));
                }
                steps.add(() -> tryWriteInput(block.text.toString()));
            }
            else
            {
                steps.add(() -> tryWrite("type", "text"));
                steps.add(() -> tryWrite("text", block.text.toString()));
            }
            steps.add(this::tryWriteEnd);
        }
        steps.add(this::tryWriteEnd);
        steps.add(() -> tryWrite("stop_reason", stopReason));
        steps.add(() -> tryWriteStartObject("usage"));
        steps.add(() -> tryWrite("input_tokens", inputTokens));
        steps.add(() -> tryWrite("output_tokens", outputTokens));
        steps.add(this::tryWriteEnd);
        steps.add(this::tryWriteEnd);
        steps.add(() -> emitted(null));
        return steps;
    }

    private boolean tryWriteInput(
        String arguments)
    {
        boolean fits = fits(arguments.length());
        if (fits)
        {
            generator.write("input", arguments.isEmpty() ? EMPTY_INPUT : parseObject(arguments));
        }
        return fits;
    }

    private static JsonObject parseObject(
        String data)
    {
        try (JsonReader reader = Json.createReader(new StringReader(data)))
        {
            return reader.readObject();
        }
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

    private static final class HeldBlock
    {
        private final LlmCanonicalBlockKind kind;
        private final String toolId;
        private final String toolName;
        private final StringBuilder text;

        private HeldBlock(
            LlmCanonicalBlockKind kind,
            String toolId,
            String toolName)
        {
            this.kind = kind;
            this.toolId = toolId;
            this.toolName = toolName;
            this.text = new StringBuilder();
        }
    }
}
