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
package io.aklivity.zilla.runtime.model.avro.internal;

import java.util.ArrayDeque;
import java.util.Deque;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroException;
import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// The model-avro adapter for the engine's format-agnostic ModelTransform SPI: an AvroTransform stage that
// presents each scalar record field (at any nesting depth through records) to a single ModelTransform as a
// path plus its extraction rendering, and turns the answer back into valid Avro events. A length-delimited
// value (string/bytes/fixed) split across input windows arrives as a leading event with deferred bytes
// followed by SEGMENT continuations; those are coalesced into the field buffer until no bytes remain
// deferred. Numeric and boolean values render to their ASCII text, matching the extraction the model has
// always surfaced.
//
// Two modes, selected by ModelTransform.identity():
//  - observing (identity) — every event forwards downstream as it arrives, byte-for-byte, and each completed
//    field is offered to the transform purely for its own accumulation. This is the extraction fast path and
//    reproduces the input bytes exactly.
//  - mediating (not identity) — the events of a scalar field nested only through records are withheld while
//    its value is captured, then the transform's answer is emitted in their place: FIELD re-emits the
//    captured bytes, REPLACED emits the substitute, DECLINED emits a structurally valid placeholder for the
//    field's type. Composite fields, Avro nulls, and anything nested through an array or map are never
//    withheld; they forward verbatim, as does a union branch index once the branch turns out not to select a
//    withheld scalar. Array/map traversal is opaque on purpose: an array repeats one element shape per entry,
//    so a single field path can't address "the nth entry" the way it addresses a fixed record field — nothing
//    below an array/map boundary is offered to the transform today.
final class AvroModelTransform implements AvroTransform
{
    private static final int NO_BRANCH = -1;
    private static final String ROOT = "$";

    private static final int STEP_BRANCH = 0;
    private static final int STEP_VALUE = 1;
    private static final int STEP_DONE = 2;

    private static final byte[] EMPTY = new byte[0];

    private final ModelTransform transform;
    private final boolean mediating;
    private final Deque<String> prefixes;
    private final MutableDirectBufferEx captured;
    private final MutableDirectBufferEx substitute;
    private final Value value;
    private final Control control;
    private final Terminal terminal;
    private final Discard discard;
    private final Emitter emitter;

    private int depth;
    private int opaque;
    private String prefix;
    private String pendingField;
    private boolean capturing;
    private int capturedLength;
    private AvroType capturedType;
    private String field;
    private int branch;
    private boolean emitting;
    private boolean replay;
    private ModelEvent pending;
    private AvroSink downstream;

    // a NONE transform needs no adapter at all: AvroStream drops AvroTransform.NONE rather than binding it,
    // so the assembled pipeline carries no stage and the caller never branches on whether one is wired
    static AvroTransform of(
        ModelTransform transform)
    {
        return transform == ModelTransform.NONE ? AvroTransform.NONE : new AvroModelTransform(transform);
    }

    private AvroModelTransform(
        ModelTransform transform)
    {
        this.transform = transform;
        this.mediating = !transform.identity();
        this.prefixes = new ArrayDeque<>();
        this.prefix = ROOT;
        this.captured = new ExpandableDirectByteBufferEx();
        this.substitute = new ExpandableDirectByteBufferEx();
        this.value = new Value();
        this.control = new Control();
        this.terminal = new Terminal();
        this.discard = new Discard();
        this.emitter = new Emitter();
        this.branch = NO_BRANCH;
    }

    @Override
    public Status transform(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink)
    {
        this.downstream = sink;

        ModelSink generic = mediating ? terminal : discard;
        Status status = mediating
            ? mediate(control, source, event, sink)
            : observe(control, source, event, sink);
        if (event == AvroEvent.START_MESSAGE && status == Status.ADVANCED)
        {
            status = open(generic);
        }
        else
        {
            status = close(status, generic);
        }
        return status;
    }

    @Override
    public Status resume(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink)
    {
        this.downstream = sink;

        Status status;
        if (!emitting)
        {
            status = sink.resume(control, source, event);
        }
        else if (replay)
        {
            // the held-back union branch was mid-emission; once it lands, the event that released it is
            // still owed its own handling
            status = emitter.emit(sink);
            emitting = status == Status.SUSPENDED;
            if (status == Status.ADVANCED)
            {
                replay = false;
                status = mediate(control, source, event, sink);
            }
        }
        else
        {
            status = map(transform.resume(this.control, value, pending, terminal));
            emitting = status == Status.SUSPENDED;
        }
        return close(status, mediating ? terminal : discard);
    }

    // discards the adapter's own in-flight state only: the wired transform's per-value lifecycle is framed
    // by START_VALUE / END_VALUE, and whatever it accumulates for its owner outlives the pipeline reset the
    // owner performs once it has read the accumulation back out
    @Override
    public void reset()
    {
        depth = 0;
        opaque = 0;
        prefix = ROOT;
        prefixes.clear();
        pendingField = null;
        capturing = false;
        capturedLength = 0;
        capturedType = null;
        field = null;
        branch = NO_BRANCH;
        emitting = false;
        replay = false;
        emitter.reset();
        control.diagnostic = null;
    }

    @Override
    public boolean identity()
    {
        return transform.identity();
    }

    // stored on the shared Control so every transform.transform(...) call this adapter drives during the
    // current message sees the authorization the owning pipeline received for it
    void authorization(
        long authorization)
    {
        control.authorization = authorization;
    }

    // observing mode: track as a side-effect before forwarding — the downstream consumes a length-delimited
    // value off the source as it writes it, so the bytes must be captured while the source still exposes them
    private Status observe(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink)
    {
        boolean complete = track(source, event);
        Status status = sink.transform(control, source, event);
        if (status != Status.REJECTED && complete)
        {
            deliver(ModelEvent.FIELD, discard);
        }
        return status;
    }

    // mediating mode: withhold a top-level scalar field's events until its value is whole, then emit the
    // transform's answer in their place
    private Status mediate(
        AvroController control,
        AvroSource source,
        AvroEvent event,
        AvroSink sink)
    {
        Status status = Status.ADVANCED;
        boolean withheld = capturing && withholdable(event);

        if (!withheld)
        {
            status = release(sink);
        }

        if (status == Status.ADVANCED)
        {
            boolean complete = track(source, event);
            if (withheld)
            {
                status = complete ? deliver(ModelEvent.FIELD, terminal) : Status.ADVANCED;
            }
            else
            {
                status = sink.transform(control, source, event);
            }
        }
        return status;
    }

    // the events a withheld scalar field is made of: its optional union branch index and its value
    private static boolean withholdable(
        AvroEvent event)
    {
        return switch (event)
        {
        case UNION_BRANCH, BOOLEAN, INT, LONG, FLOAT, DOUBLE, ENUM, STRING, BYTES, FIXED, SEGMENT -> true;
        default -> false;
        };
    }

    // opens the field run for one value, once the message framing has been forwarded
    private Status open(
        ModelSink sink)
    {
        return map(transform.transform(control, value.wrap(null, null, 0, 0), ModelEvent.START_VALUE, sink));
    }

    // closes the field run when the datum completes. COMPLETED is the only signal available: the pump stops
    // the moment the terminal sink closes the top-level record, so END_MESSAGE is never pulled from the
    // parser and cannot be the trigger. A close that merely advances leaves the datum's COMPLETED intact.
    private Status close(
        Status status,
        ModelSink sink)
    {
        Status closed = status;
        if (status == Status.COMPLETED)
        {
            Status flushed = map(transform.flush(control, value.wrap(null, null, 0, 0), sink));
            if (flushed == Status.ADVANCED)
            {
                flushed = map(transform.transform(control, value, ModelEvent.END_VALUE, sink));
            }
            closed = flushed == Status.ADVANCED ? status : flushed;
        }
        return closed;
    }

    // hands the whole captured value to the transform; in mediating mode the terminal sink emits whatever
    // the chain answers with, so a SUSPENDED here means the answer is still being written out
    private Status deliver(
        ModelEvent event,
        ModelSink sink)
    {
        pending = event;
        Status status =
            map(transform.transform(control, value.wrap(field, captured, 0, capturedLength), event, sink));
        emitting = status == Status.SUSPENDED;
        replay = false;
        return status;
    }

    // emits a union branch index held back for a field that turned out not to be a withheld scalar
    private Status release(
        AvroSink sink)
    {
        Status status = Status.ADVANCED;
        if (branch != NO_BRANCH)
        {
            emitter.wrapBranch(branch);
            branch = NO_BRANCH;
            status = emitter.emit(sink);
            emitting = status == Status.SUSPENDED;
            replay = emitting;
        }
        return status;
    }

    // updates the walk state and renders the value of a top-level field into the capture buffer, mirroring
    // the extraction rendering: raw bytes for a length-delimited value, ASCII text for a scalar
    private boolean track(
        AvroSource source,
        AvroEvent event)
    {
        boolean complete = false;
        switch (event)
        {
        case START_MESSAGE:
            depth = 0;
            opaque = 0;
            prefix = ROOT;
            prefixes.clear();
            pendingField = null;
            capturing = false;
            field = null;
            branch = NO_BRANCH;
            break;
        case START_RECORD:
            // entering the value of the field named by pendingField, unless this is the root record itself
            // (depth 0) or we're already inside an array/map, where field paths aren't tracked
            if (opaque == 0 && depth > 0)
            {
                prefixes.push(prefix);
                prefix = prefix + "." + pendingField;
            }
            depth++;
            capturing = false;
            field = null;
            break;
        case START_ARRAY:
        case START_MAP:
            opaque++;
            depth++;
            capturing = false;
            field = null;
            break;
        case END_RECORD:
            depth--;
            if (opaque == 0 && depth > 0)
            {
                prefix = prefixes.pop();
            }
            capturing = false;
            field = null;
            break;
        case END_ARRAY:
        case END_MAP:
            opaque--;
            depth--;
            capturing = false;
            field = null;
            break;
        case FIELD_NAME:
            pendingField = source.getField();
            capturing = opaque == 0;
            field = capturing ? prefix + "." + pendingField : null;
            capturedLength = 0;
            capturedType = null;
            branch = NO_BRANCH;
            break;
        case UNION_BRANCH:
            if (capturing)
            {
                branch = source.getInt();
            }
            break;
        case STRING:
        case BYTES:
        case FIXED:
        case SEGMENT:
            if (capturing)
            {
                // a SEGMENT continuation carries no type of its own; the leading event already supplied it
                if (capturedType == null)
                {
                    capturedType = source.type();
                }
                DirectBufferEx segment = source.getSegment();
                int length = segment.capacity();
                captured.putBytes(capturedLength, segment, 0, length);
                capturedLength += length;
                complete = source.deferredBytes() == 0;
                capturing = !complete;
            }
            break;
        case INT:
        case ENUM:
            if (capturing)
            {
                capturedType = source.type();
                capturedLength = captured.putIntAscii(0, source.getInt());
                capturing = false;
                complete = true;
            }
            break;
        case LONG:
            if (capturing)
            {
                capturedType = source.type();
                capturedLength = captured.putLongAscii(0, source.getLong());
                capturing = false;
                complete = true;
            }
            break;
        case FLOAT:
            if (capturing)
            {
                capturedType = source.type();
                capturedLength = captured.putStringWithoutLengthAscii(0, String.valueOf(source.getFloat()));
                capturing = false;
                complete = true;
            }
            break;
        case DOUBLE:
            if (capturing)
            {
                capturedType = source.type();
                capturedLength = captured.putStringWithoutLengthAscii(0, String.valueOf(source.getDouble()));
                capturing = false;
                complete = true;
            }
            break;
        case BOOLEAN:
            if (capturing)
            {
                capturedType = source.type();
                capturedLength = captured.putStringWithoutLengthAscii(0, String.valueOf(source.getBoolean()));
                capturing = false;
                complete = true;
            }
            break;
        case MAP_KEY:
        case END_MESSAGE:
            break;
        default:
            capturing = false;
            break;
        }
        return complete;
    }

    private Status map(
        ModelStatus status)
    {
        if (status == ModelStatus.REJECTED)
        {
            String diagnostic = control.diagnostic;
            control.diagnostic = null;
            throw new AvroException(diagnostic != null ? diagnostic : "transform rejected value");
        }
        return switch (status)
        {
        case OVERFLOW -> Status.SUSPENDED;
        case COMPLETE -> Status.COMPLETED;
        default -> Status.ADVANCED;
        };
    }

    private static ModelStatus unmap(
        Status status)
    {
        return switch (status)
        {
        case SUSPENDED -> ModelStatus.OVERFLOW;
        case REJECTED -> ModelStatus.REJECTED;
        case COMPLETED -> ModelStatus.COMPLETE;
        default -> ModelStatus.OK;
        };
    }

    // the generic view of the field in flight, over the capture buffer the adapter owns. contentView and
    // emptyView are kept separate rather than one shared instance re-wrapped either way: START_VALUE and
    // flush() wrap(null, ...) between every field's wrap(buffer, ...), and a shared UnsafeBufferEx has to
    // re-derive its MemorySegment whenever the array it last wrapped changes — which alternating between a
    // real buffer and EMPTY forces on every call. emptyView wraps EMPTY exactly once, ever, so switching
    // which view is current is a reference assignment, not a re-wrap.
    private static final class Value implements ModelSource
    {
        private final UnsafeBufferEx contentView;
        private final UnsafeBufferEx emptyView;

        private DirectBufferEx view;
        private String path;

        private Value()
        {
            this.contentView = new UnsafeBufferEx(EMPTY);
            this.emptyView = new UnsafeBufferEx(EMPTY);
            this.view = emptyView;
        }

        @Override
        public String getPath()
        {
            return path;
        }

        @Override
        public DirectBufferEx getValue()
        {
            return view;
        }

        private Value wrap(
            String path,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            this.path = path;
            if (buffer != null)
            {
                contentView.wrap(buffer, index, length);
                view = contentView;
            }
            else
            {
                view = emptyView;
            }
            return this;
        }
    }

    private static final class Control implements ModelController
    {
        private long authorization;
        private String diagnostic;

        @Override
        public long authorization()
        {
            return authorization;
        }

        @Override
        public void reject(
            String diagnostic)
        {
            this.diagnostic = diagnostic;
        }
    }

    // the downstream in observing mode: the events were already forwarded verbatim, so an answer has
    // nowhere to land
    private static final class Discard implements ModelSink
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            return ModelStatus.OK;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // the downstream in mediating mode: turns the chain's answer back into Avro events
    private final class Terminal implements ModelSink
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            ModelStatus status = ModelStatus.OK;
            switch (event)
            {
            case FIELD:
            case REPLACED:
                status = unmap(emit(source));
                break;
            case DECLINED:
                status = unmap(decline());
                break;
            default:
                break;
            }
            return status;
        }

        @Override
        public ModelStatus resume(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            return unmap(emitter.emit(downstream));
        }

        @Override
        public boolean identity()
        {
            return true;
        }

        private Status emit(
            ModelSource source)
        {
            DirectBufferEx answer = source.getValue();
            if (answer == value.view)
            {
                emitter.wrapValue(branch, capturedType, captured, 0, capturedLength);
            }
            else
            {
                int length = answer.capacity();
                substitute.putBytes(0, answer, 0, length);
                emitter.wrapValue(branch, capturedType, substitute, 0, length);
            }
            branch = NO_BRANCH;
            return emitter.emit(downstream);
        }

        // only the format knows what a structurally valid value of this field's type looks like
        private Status decline()
        {
            AvroKind kind = capturedType != null ? capturedType.kind() : AvroKind.NULL;
            int length;
            switch (kind)
            {
            case BOOLEAN:
                length = substitute.putStringWithoutLengthAscii(0, "false");
                break;
            case INT:
            case LONG:
            case ENUM:
                length = substitute.putStringWithoutLengthAscii(0, "0");
                break;
            case FLOAT:
            case DOUBLE:
                length = substitute.putStringWithoutLengthAscii(0, "0.0");
                break;
            case FIXED:
                length = capturedType.size();
                substitute.setMemory(0, length, (byte) 0);
                break;
            default:
                length = 0;
                break;
            }
            emitter.wrapValue(branch, capturedType, substitute, 0, length);
            branch = NO_BRANCH;
            return emitter.emit(downstream);
        }
    }

    // the Avro view of the answer: a union branch index, then the value itself, streamed into the bounded
    // downstream exactly as the parser streams a value the sink reads in place
    private final class Emitter implements AvroSource, AvroController
    {
        private final UnsafeBufferEx view;

        private AvroType type;
        private DirectBufferEx buffer;
        private int offset;
        private int length;
        private int progress;
        private int branchIndex;
        private int step;
        private boolean valueless;

        private Emitter()
        {
            this.view = new UnsafeBufferEx(EMPTY);
            this.step = STEP_DONE;
        }

        private void wrapValue(
            int branchIndex,
            AvroType type,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            this.branchIndex = branchIndex;
            this.type = type;
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
            this.progress = 0;
            this.valueless = false;
            this.step = branchIndex != NO_BRANCH ? STEP_BRANCH : STEP_VALUE;
        }

        private void wrapBranch(
            int branchIndex)
        {
            this.branchIndex = branchIndex;
            this.type = null;
            this.buffer = null;
            this.offset = 0;
            this.length = 0;
            this.progress = 0;
            this.valueless = true;
            this.step = STEP_BRANCH;
        }

        private void reset()
        {
            step = STEP_DONE;
        }

        private Status emit(
            AvroSink sink)
        {
            Status status = Status.ADVANCED;
            if (step == STEP_BRANCH)
            {
                status = sink.transform(this, this, AvroEvent.UNION_BRANCH);
                if (status == Status.ADVANCED)
                {
                    step = valueless ? STEP_DONE : STEP_VALUE;
                }
            }
            if (status == Status.ADVANCED && step == STEP_VALUE)
            {
                status = sink.transform(this, this, event());
                if (status != Status.SUSPENDED)
                {
                    step = STEP_DONE;
                }
            }
            return status;
        }

        private AvroEvent event()
        {
            return switch (type != null ? type.kind() : AvroKind.NULL)
            {
            case BOOLEAN -> AvroEvent.BOOLEAN;
            case INT -> AvroEvent.INT;
            case LONG -> AvroEvent.LONG;
            case FLOAT -> AvroEvent.FLOAT;
            case DOUBLE -> AvroEvent.DOUBLE;
            case BYTES -> AvroEvent.BYTES;
            case STRING -> AvroEvent.STRING;
            case FIXED -> AvroEvent.FIXED;
            case ENUM -> AvroEvent.ENUM;
            default -> AvroEvent.NULL;
            };
        }

        @Override
        public void segmentable()
        {
        }

        @Override
        public void consumed(
            int sourceBytes)
        {
            progress += sourceBytes;
        }

        @Override
        public boolean getBoolean()
        {
            return Boolean.parseBoolean(buffer.getStringWithoutLengthAscii(offset, length));
        }

        @Override
        public int getInt()
        {
            return step == STEP_BRANCH ? branchIndex : buffer.parseIntAscii(offset, length);
        }

        @Override
        public long getLong()
        {
            return buffer.parseLongAscii(offset, length);
        }

        @Override
        public float getFloat()
        {
            return Float.parseFloat(buffer.getStringWithoutLengthAscii(offset, length));
        }

        @Override
        public double getDouble()
        {
            return Double.parseDouble(buffer.getStringWithoutLengthAscii(offset, length));
        }

        @Override
        public String getString()
        {
            return buffer.getStringWithoutLengthUtf8(offset, length);
        }

        @Override
        public CharSequence getStringView()
        {
            return getString();
        }

        @Override
        public String getField()
        {
            return null;
        }

        @Override
        public String getKey()
        {
            return null;
        }

        @Override
        public DirectBufferEx getSegment()
        {
            view.wrap(buffer, offset + progress, length - progress);
            return view;
        }

        @Override
        public int deferredBytes()
        {
            return 0;
        }

        @Override
        public AvroType type()
        {
            return type;
        }

        @Override
        public AvroLocation getLocation()
        {
            return null;
        }
    }
}
