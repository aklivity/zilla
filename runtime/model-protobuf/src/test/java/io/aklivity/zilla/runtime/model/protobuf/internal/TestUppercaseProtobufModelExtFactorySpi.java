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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransformable;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExt;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtContext;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtFactorySpi;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtHandler;

/**
 * A generic, business-agnostic test-only extension registered solely under {@code src/test} so it never
 * ships in the production jar. It uppercases every scalar string field's value, unconditionally, with no
 * configuration of its own -- proving the {@link ProtobufModelExtFactorySpi} composition mechanism works
 * end-to-end through a live engine, the same mechanism a real installed extension (e.g. zilla-plus's
 * disclosure) relies on, without model-protobuf needing to know anything about what a real extension might
 * do with it.
 */
public final class TestUppercaseProtobufModelExtFactorySpi implements ProtobufModelExtFactorySpi
{
    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public ProtobufModelExt create(
        Configuration config)
    {
        return new ProtobufModelExt()
        {
            @Override
            public String name()
            {
                return "test-uppercase";
            }

            @Override
            public ProtobufModelExtContext supply(
                EngineContext context)
            {
                return (schema, options) -> new ProtobufModelExtHandler()
                {
                    private final Uppercase transform = new Uppercase();

                    @Override
                    public <T extends ProtobufTransformable<T>> T decode(
                        T transformable)
                    {
                        return transformable.transform(transform);
                    }

                    @Override
                    public <T extends ProtobufTransformable<T>> T encode(
                        T transformable)
                    {
                        return transformable.transform(transform);
                    }
                };
            }
        };
    }

    // withholds a scalar string field's value until it is whole, then substitutes the uppercased bytes in
    // its place; mirrors the chunked-accumulation, withhold-then-substitute pattern a real extension (e.g.
    // zilla-plus's disclosure redact/mask/hash) uses over the same ProtobufTransform contract
    private static final class Uppercase implements ProtobufTransform
    {
        private static final byte[] EMPTY = new byte[0];

        private final Substitute substitute;
        private final ExpandableDirectByteBufferEx captured;

        private ProtobufController upstream;
        private ProtobufField pending;
        private boolean emitting;
        private int capturedLength;

        private Uppercase()
        {
            this.substitute = new Substitute();
            this.captured = new ExpandableDirectByteBufferEx();
        }

        @Override
        public Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            this.upstream = control;
            Status status;
            switch (event)
            {
            case FIELD:
                status = onField(control, source, sink);
                break;
            case VALUE:
            case SEGMENT:
                status = onValue(control, source, event, sink);
                break;
            default:
                status = sink.transform(control, source, event);
                break;
            }
            return status;
        }

        @Override
        public Status resume(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            this.upstream = control;
            Status status;
            if (emitting)
            {
                status = sink.resume(substitute, substitute, substitute.event);
                emitting = status == Status.SUSPENDED;
            }
            else
            {
                status = sink.resume(control, source, event);
            }
            return status;
        }

        @Override
        public void reset()
        {
            pending = null;
            emitting = false;
            capturedLength = 0;
        }

        private Status onField(
            ProtobufController control,
            ProtobufSource source,
            ProtobufSink sink)
        {
            ProtobufField field = source.field();
            pending = uppercasable(field) ? field : null;
            capturedLength = 0;
            return sink.transform(control, source, ProtobufEvent.FIELD);
        }

        private Status onValue(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            Status status;
            if (pending == null)
            {
                status = sink.transform(control, source, event);
            }
            else
            {
                DirectBufferEx segment = source.segment();
                int length = segment.capacity();
                captured.putBytes(capturedLength, segment, 0, length);
                capturedLength += length;

                if (source.deferredBytes() == 0)
                {
                    byte[] upper = new byte[capturedLength];
                    for (int i = 0; i < capturedLength; i++)
                    {
                        byte value = captured.getByte(i);
                        upper[i] = value >= 'a' && value <= 'z' ? (byte) (value - 0x20) : value;
                    }
                    substitute.set(pending, upper);
                    status = emit(sink);
                    pending = null;
                    capturedLength = 0;
                }
                else
                {
                    status = Status.ADVANCED;
                }
            }
            return status;
        }

        private Status emit(
            ProtobufSink sink)
        {
            Status status = sink.transform(substitute, substitute, substitute.event);
            emitting = status == Status.SUSPENDED;
            return status;
        }

        private static boolean uppercasable(
            ProtobufField field)
        {
            return field != null && field.type() == ProtobufType.STRING && !field.repeated();
        }

        // the synthesized view fed to the downstream sink in place of an uppercased value
        private final class Substitute implements ProtobufSource, ProtobufController
        {
            private final UnsafeBufferEx content;
            private final UnsafeBufferEx view;

            private ProtobufEvent event;
            private ProtobufField field;
            private int length;
            private int progress;

            private Substitute()
            {
                this.content = new UnsafeBufferEx(EMPTY);
                this.view = new UnsafeBufferEx(EMPTY);
            }

            private void set(
                ProtobufField field,
                byte[] bytes)
            {
                this.event = ProtobufEvent.VALUE;
                this.field = field;
                this.progress = 0;
                this.length = bytes.length;
                content.wrap(bytes, 0, bytes.length);
            }

            @Override
            public void segmentable()
            {
            }

            @Override
            public long authorization()
            {
                return upstream.authorization();
            }

            @Override
            public void consumed(
                int sourceBytes)
            {
                progress += sourceBytes;
            }

            @Override
            public ProtobufField field()
            {
                return field;
            }

            @Override
            public ProtobufMessage message()
            {
                return null;
            }

            @Override
            public int fieldNumber()
            {
                return field != null ? field.number() : -1;
            }

            @Override
            public ProtobufWireType wireType()
            {
                return field != null ? field.type().wireType() : null;
            }

            @Override
            public long longValue()
            {
                return 0L;
            }

            @Override
            public double doubleValue()
            {
                return 0d;
            }

            @Override
            public float floatValue()
            {
                return 0f;
            }

            @Override
            public DirectBufferEx segment()
            {
                view.wrap(content, progress, length - progress);
                return view;
            }

            @Override
            public int deferredBytes()
            {
                return 0;
            }
        }
    }
}
