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

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Shared fan-out machinery for a dialect's decode {@link JsonTransform}: accumulates canonical actions
 * (each a flat object such as {@code {"type":"messageStart",...}}) via the {@code queueXxx} methods, then
 * fires the whole queue to {@code sink} as genuine {@link JsonEvent#START_DOCUMENT}...{@link
 * JsonEvent#END_DOCUMENT} cycles -- one complete cycle per queued action, several in a row within one
 * {@link #fireQueued(JsonSink)} call -- via {@link #fireQueued(JsonSink)}, called by a subclass once it
 * decides its native input document is fully observed (typically on the native document's own real
 * {@code END_DOCUMENT}).
 * <p>
 * A single reusable synthetic {@link JsonSource}/{@link JsonController} carries each field's key or value
 * text (or number) to {@code sink}; a resumable text write ({@link JsonEvent#KEY_NAME} or
 * {@link JsonEvent#VALUE_STRING}) tracks its own progress across repeated {@link #resume} calls exactly like
 * {@code runtime/model-json}'s {@code JsonModelFieldTransform.TextSource}, generalized to a longer, queued
 * sequence of actions instead of one key-then-value pair.
 * </p>
 */
abstract class LlmCanonicalEmitter implements JsonTransform
{
    private final Deque<List<Field>> pendingActions;
    private final Synthetic synthetic;

    private List<Field> currentAction;
    private int fieldIndex;
    private Step step;

    LlmCanonicalEmitter()
    {
        this.pendingActions = new ArrayDeque<>();
        this.synthetic = new Synthetic();
        this.step = Step.DONE;
    }

    @Override
    public final Status resume(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        return drive(sink, true);
    }

    @Override
    public void reset()
    {
        pendingActions.clear();
        currentAction = null;
        fieldIndex = 0;
        step = Step.DONE;
    }

    protected final void queueMessageStart(
        int choiceIndex,
        String id,
        String model,
        String role)
    {
        List<Field> action = newAction(LlmCanonicalEvent.TYPE_MESSAGE_START);
        addField(action, LlmCanonicalEvent.CHOICE_INDEX, choiceIndex);
        if (id != null)
        {
            addField(action, LlmCanonicalEvent.ID, id);
        }
        if (model != null)
        {
            addField(action, LlmCanonicalEvent.MODEL, model);
        }
        addField(action, LlmCanonicalEvent.ROLE, role);
        pendingActions.add(action);
    }

    protected final void queueBlockStart(
        int choiceIndex,
        int blockId,
        LlmCanonicalBlockKind kind,
        String toolId,
        String toolName)
    {
        List<Field> action = newAction(LlmCanonicalEvent.TYPE_BLOCK_START);
        addField(action, LlmCanonicalEvent.CHOICE_INDEX, choiceIndex);
        addField(action, LlmCanonicalEvent.BLOCK_ID, blockId);
        addField(action, LlmCanonicalEvent.KIND,
            kind == LlmCanonicalBlockKind.TOOL_CALL ? LlmCanonicalEvent.KIND_TOOL_CALL : LlmCanonicalEvent.KIND_TEXT);
        if (toolId != null)
        {
            addField(action, LlmCanonicalEvent.TOOL_ID, toolId);
        }
        if (toolName != null)
        {
            addField(action, LlmCanonicalEvent.TOOL_NAME, toolName);
        }
        pendingActions.add(action);
    }

    protected final void queueData(
        String text)
    {
        List<Field> action = newAction(LlmCanonicalEvent.TYPE_DATA);
        addField(action, LlmCanonicalEvent.TEXT, text);
        pendingActions.add(action);
    }

    protected final void queueBlockEnd(
        int choiceIndex,
        int blockId)
    {
        List<Field> action = newAction(LlmCanonicalEvent.TYPE_BLOCK_END);
        addField(action, LlmCanonicalEvent.CHOICE_INDEX, choiceIndex);
        addField(action, LlmCanonicalEvent.BLOCK_ID, blockId);
        pendingActions.add(action);
    }

    protected final void queueFinish(
        int choiceIndex,
        LlmCanonicalFinishReason reason)
    {
        List<Field> action = newAction(LlmCanonicalEvent.TYPE_FINISH);
        addField(action, LlmCanonicalEvent.CHOICE_INDEX, choiceIndex);
        addField(action, LlmCanonicalEvent.REASON, reason.name());
        pendingActions.add(action);
    }

    protected final void queueUsage(
        int inputTokens,
        int outputTokens)
    {
        List<Field> action = newAction(LlmCanonicalEvent.TYPE_USAGE);
        addField(action, LlmCanonicalEvent.INPUT_TOKENS, inputTokens);
        addField(action, LlmCanonicalEvent.OUTPUT_TOKENS, outputTokens);
        pendingActions.add(action);
    }

    protected final void queueEnd()
    {
        pendingActions.add(newAction(LlmCanonicalEvent.TYPE_END));
    }

    protected final Status fireQueued(
        JsonSink sink)
    {
        return drive(sink, false);
    }

    private static List<Field> newAction(
        String type)
    {
        List<Field> action = new ArrayList<>();
        addField(action, LlmCanonicalEvent.TYPE, type);
        return action;
    }

    private static void addField(
        List<Field> action,
        String key,
        String value)
    {
        Field field = new Field();
        field.key = key;
        field.valueEvent = JsonEvent.VALUE_STRING;
        field.stringValue = value;
        action.add(field);
    }

    private static void addField(
        List<Field> action,
        String key,
        int value)
    {
        Field field = new Field();
        field.key = key;
        field.valueEvent = JsonEvent.VALUE_NUMBER;
        field.intValue = value;
        action.add(field);
    }

    private Status drive(
        JsonSink sink,
        boolean resuming)
    {
        // Status.COMPLETED, not ADVANCED, when nothing was ever queued: fireQueued() is only ever called
        // at the real native document's own END_DOCUMENT, so an empty queue still means that real document
        // finished cleanly -- there is simply nothing this dialect pair renders for it.
        Status status = Status.COMPLETED;
        boolean looping = true;
        boolean firstIteration = true;

        while (looping)
        {
            if (step == Step.DONE)
            {
                if (currentAction == null)
                {
                    currentAction = pendingActions.poll();
                    fieldIndex = 0;
                    if (currentAction == null)
                    {
                        looping = false;
                        break;
                    }
                }
                step = Step.DOCUMENT_START;
            }

            final boolean resumeThisWrite = resuming && firstIteration;
            firstIteration = false;

            final JsonEvent event = eventFor(step);
            final Object src = resumeThisWrite ? synthetic : wrapSourceFor(step);

            status = resumeThisWrite
                ? sink.resume(synthetic, synthetic, event)
                : sink.transform(synthetic, synthetic, event);

            if (src != synthetic)
            {
                throw new IllegalStateException();
            }

            if (status == Status.SUSPENDED || status == Status.REJECTED)
            {
                looping = false;
            }
            else
            {
                advanceStep();
            }
        }

        return status;
    }

    private void advanceStep()
    {
        switch (step)
        {
        case DOCUMENT_START:
            step = Step.OBJECT_START;
            break;
        case OBJECT_START:
            step = fieldIndex < currentAction.size() ? Step.FIELD_KEY : Step.OBJECT_END;
            break;
        case FIELD_KEY:
            step = Step.FIELD_VALUE;
            break;
        case FIELD_VALUE:
            fieldIndex++;
            step = fieldIndex < currentAction.size() ? Step.FIELD_KEY : Step.OBJECT_END;
            break;
        case OBJECT_END:
            step = Step.DOCUMENT_END;
            break;
        case DOCUMENT_END:
            currentAction = null;
            fieldIndex = 0;
            step = Step.DONE;
            break;
        default:
            break;
        }
    }

    private JsonEvent eventFor(
        Step step)
    {
        JsonEvent event;
        switch (step)
        {
        case DOCUMENT_START:
            event = JsonEvent.START_DOCUMENT;
            break;
        case OBJECT_START:
            event = JsonEvent.START_OBJECT;
            break;
        case FIELD_KEY:
            event = JsonEvent.KEY_NAME;
            break;
        case FIELD_VALUE:
            event = currentAction.get(fieldIndex).valueEvent;
            break;
        case OBJECT_END:
            event = JsonEvent.END_OBJECT;
            break;
        default:
            event = JsonEvent.END_DOCUMENT;
            break;
        }
        return event;
    }

    private Synthetic wrapSourceFor(
        Step step)
    {
        switch (step)
        {
        case FIELD_KEY:
            synthetic.wrapText(currentAction.get(fieldIndex).key);
            break;
        case FIELD_VALUE:
            Field field = currentAction.get(fieldIndex);
            if (field.valueEvent == JsonEvent.VALUE_STRING)
            {
                synthetic.wrapText(field.stringValue);
            }
            else
            {
                synthetic.wrapInt(field.intValue);
            }
            break;
        default:
            synthetic.wrapNone();
            break;
        }
        return synthetic;
    }

    private enum Step
    {
        DONE,
        DOCUMENT_START,
        OBJECT_START,
        FIELD_KEY,
        FIELD_VALUE,
        OBJECT_END,
        DOCUMENT_END
    }

    private static final class Field
    {
        private String key;
        private JsonEvent valueEvent;
        private String stringValue;
        private int intValue;
    }

    // Reused for every synthetic key/value write this emitter drives -- a text write's progress survives
    // across a SUSPENDED write's later resume() (see LlmCanonicalEmitter.drive()) because that resume path
    // never re-wraps this instance, exactly mirroring the KEY/VALUE resumable-write idiom in
    // runtime/model-json's JsonModelFieldTransform.
    private static final class Synthetic implements JsonSource, JsonController
    {
        private CharSequence text;
        private int progress;
        private int intValue;

        void wrapText(
            CharSequence text)
        {
            this.text = text;
            this.progress = 0;
        }

        void wrapInt(
            int value)
        {
            this.text = null;
            this.intValue = value;
        }

        void wrapNone()
        {
            this.text = null;
        }

        @Override
        public void segmentable()
        {
        }

        @Override
        public void consumed(
            int sourceChars)
        {
            progress += sourceChars;
        }

        @Override
        public String getString()
        {
            return getStringView().toString();
        }

        @Override
        public CharSequence getStringView()
        {
            return text.subSequence(progress, text.length());
        }

        @Override
        public BigDecimal getBigDecimal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIntegralNumber()
        {
            return true;
        }

        @Override
        public int getInt()
        {
            return intValue;
        }

        @Override
        public long getLong()
        {
            return intValue;
        }

        @Override
        public JsonLocation getLocation()
        {
            return null;
        }

        @Override
        public DirectBufferEx getSegment()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonVerbatim getVerbatim(
            int limit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void skipValue()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deferredBytes()
        {
            return false;
        }
    }
}
