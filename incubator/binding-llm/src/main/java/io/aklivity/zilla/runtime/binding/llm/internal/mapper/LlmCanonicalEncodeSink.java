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

import java.util.List;
import java.util.function.BooleanSupplier;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmNativeEventOutput;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

/**
 * The pipeline's terminal {@link JsonSink}: consumes the canonical event stream {@link LlmCanonicalEmitter}
 * fires and writes the <em>target</em> native dialect's JSON via a bounded {@link JsonGeneratorEx},
 * dispatching by the accumulated canonical {@code "type"} to a dialect-specific {@code writeXxx()} (see
 * {@link #write(String)}).
 */
abstract class LlmCanonicalEncodeSink implements JsonSink
{
    private static final int GENERATOR_BUFFER_CAPACITY = 8192;

    private static final String ENVELOPE_STREAMING = "streaming";

    protected final LlmNativeEventOutput output;
    protected final JsonGeneratorEx generator;
    protected final MutableDirectBufferEx generatorBuffer;

    private final JsonEnvelope envelope;

    private String pendingKey;
    private String type;
    private int choiceIndex;
    private String id;
    private String model;
    private String role;
    private int blockId;
    private String kind;
    private String toolId;
    private String toolName;
    private String text;
    private String reason;
    private int inputTokens = -1;
    private int outputTokens = -1;

    private BooleanSupplier pendingWrite;
    private List<BooleanSupplier> plan;
    private int step;

    LlmCanonicalEncodeSink(
        JsonEnvelope envelope,
        LlmNativeEventOutput output)
    {
        this.envelope = envelope;
        this.output = output;
        this.generator = JsonEx.createGenerator();
        this.generatorBuffer = new UnsafeBufferEx(new byte[GENERATOR_BUFFER_CAPACITY]);
        this.generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
    }

    protected final boolean streaming()
    {
        DirectBufferEx value = envelope.get(ENVELOPE_STREAMING, 0);
        return value == null || "true".equals(value.getStringWithoutLengthUtf8(0, value.capacity()));
    }

    @Override
    public final Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status;
        switch (event)
        {
        case START_DOCUMENT:
            clearFields();
            status = Status.ADVANCED;
            break;
        case KEY_NAME:
            pendingKey = source.getString();
            status = Status.ADVANCED;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            applyField(source, event);
            status = Status.ADVANCED;
            break;
        case START_OBJECT:
        case END_OBJECT:
            status = Status.ADVANCED;
            break;
        case END_DOCUMENT:
            status = dispatch();
            break;
        default:
            status = Status.ADVANCED;
            break;
        }
        return status;
    }

    @Override
    public final Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        return continueDispatch();
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    protected abstract boolean write(
        String type);

    protected final boolean inProgress()
    {
        return plan != null;
    }

    protected final boolean run(
        List<BooleanSupplier> steps)
    {
        if (plan == null)
        {
            plan = steps;
        }

        boolean done = false;
        boolean suspended = false;
        while (!done && !suspended)
        {
            if (step >= plan.size())
            {
                done = true;
            }
            else if (plan.get(step).getAsBoolean())
            {
                step++;
            }
            else
            {
                suspended = true;
            }
        }

        if (done)
        {
            plan = null;
            step = 0;
        }
        return done;
    }

    private Status dispatch()
    {
        pendingWrite = () -> write(type);
        return continueDispatch();
    }

    private Status continueDispatch()
    {
        Status status;
        if (pendingWrite.getAsBoolean())
        {
            pendingWrite = null;
            status = Status.COMPLETED;
        }
        else
        {
            status = Status.SUSPENDED;
        }
        return status;
    }

    private void applyField(
        JsonSource source,
        JsonEvent event)
    {
        if (pendingKey == null)
        {
            return;
        }

        switch (pendingKey)
        {
        case LlmCanonicalEvent.TYPE:
            type = source.getString();
            break;
        case LlmCanonicalEvent.CHOICE_INDEX:
            choiceIndex = source.getInt();
            break;
        case LlmCanonicalEvent.ID:
            id = source.getString();
            break;
        case LlmCanonicalEvent.MODEL:
            model = source.getString();
            break;
        case LlmCanonicalEvent.ROLE:
            role = source.getString();
            break;
        case LlmCanonicalEvent.BLOCK_ID:
            blockId = source.getInt();
            break;
        case LlmCanonicalEvent.KIND:
            kind = source.getString();
            break;
        case LlmCanonicalEvent.TOOL_ID:
            toolId = source.getString();
            break;
        case LlmCanonicalEvent.TOOL_NAME:
            toolName = source.getString();
            break;
        case LlmCanonicalEvent.TEXT:
            text = source.getString();
            break;
        case LlmCanonicalEvent.REASON:
            reason = source.getString();
            break;
        case LlmCanonicalEvent.INPUT_TOKENS:
            inputTokens = source.getInt();
            break;
        case LlmCanonicalEvent.OUTPUT_TOKENS:
            outputTokens = source.getInt();
            break;
        default:
            break;
        }
        pendingKey = null;
    }

    private void clearFields()
    {
        type = null;
        choiceIndex = 0;
        id = null;
        model = null;
        role = null;
        blockId = 0;
        kind = null;
        toolId = null;
        toolName = null;
        text = null;
        reason = null;
        inputTokens = -1;
        outputTokens = -1;
    }

    protected final boolean isToolCall()
    {
        return LlmCanonicalEvent.KIND_TOOL_CALL.equals(kind);
    }

    protected final int choiceIndex()
    {
        return choiceIndex;
    }

    protected final String id()
    {
        return id;
    }

    protected final String model()
    {
        return model;
    }

    protected final String role()
    {
        return role;
    }

    protected final int blockId()
    {
        return blockId;
    }

    protected final String toolId()
    {
        return toolId;
    }

    protected final String toolName()
    {
        return toolName;
    }

    protected final String text()
    {
        return text;
    }

    protected final LlmCanonicalFinishReason reason()
    {
        return LlmCanonicalFinishReason.valueOf(reason);
    }

    protected final int inputTokens()
    {
        return inputTokens;
    }

    protected final int outputTokens()
    {
        return outputTokens;
    }

    protected final boolean fits(
        int chars)
    {
        return generator.remaining() >= chars * 4 + 16;
    }

    protected final boolean tryWriteStartObject()
    {
        return generator.writeStartObjectEx();
    }

    protected final boolean tryWriteStartObject(
        String key)
    {
        boolean fits = fits(key.length());
        if (fits)
        {
            generator.writeStartObject(key);
        }
        return fits;
    }

    protected final boolean tryWriteStartArray(
        String key)
    {
        boolean fits = fits(key.length());
        if (fits)
        {
            generator.writeStartArray(key);
        }
        return fits;
    }

    protected final boolean tryWriteEnd()
    {
        return generator.writeEndEx();
    }

    protected final boolean tryWrite(
        String key,
        String value)
    {
        boolean fits = fits(key.length() + value.length());
        if (fits)
        {
            generator.write(key, value);
        }
        return fits;
    }

    protected final boolean tryWrite(
        String key,
        int value)
    {
        boolean fits = fits(key.length());
        if (fits)
        {
            generator.write(key, value);
        }
        return fits;
    }

    protected final boolean tryWriteNull(
        String key)
    {
        boolean fits = fits(key.length());
        if (fits)
        {
            generator.writeNull(key);
        }
        return fits;
    }

    protected final void emit(
        String nativeEventName)
    {
        output.event(nativeEventName, generatorBuffer, 0, generator.length());
        generator.reset();
        generator.wrap(generatorBuffer, 0, generatorBuffer.capacity());
    }

    /**
     * Emits a dialect terminator (e.g. OpenAI's {@code [DONE]}) that is not itself JSON, bypassing the
     * generator entirely.
     */
    final void end(
        byte[] bytes)
    {
        output.event(null, new UnsafeBufferEx(bytes), 0, bytes.length);
    }
}
