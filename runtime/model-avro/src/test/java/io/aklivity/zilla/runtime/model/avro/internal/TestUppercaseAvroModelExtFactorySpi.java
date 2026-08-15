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

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroLocation;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.avro.AvroTransformable;
import io.aklivity.zilla.runtime.common.avro.AvroType;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExt;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtContext;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtFactorySpi;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtHandler;

// Test-only AvroModelExt, registered only under src/test/resources/META-INF/services so it never ships
// in the production jar. Exercises the same AvroModelExt composition mechanism a real installed
// extension relies on -- apply-on-decode, fragment accumulation across multiple STRING chunks, drain
// across a SUSPENDED terminal generator, and reject via AvroPipeline.Status.REJECTED -- without
// model-avro needing to know anything about what a real extension might do with it. Uppercases the
// string value of any field literally named "secret"; a captured value of "REJECT" rejects the datum.
public final class TestUppercaseAvroModelExtFactorySpi implements AvroModelExtFactorySpi
{
    private static final String TARGET_FIELD = "secret";
    private static final String REJECT_VALUE = "REJECT";

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public AvroModelExt create(
        Configuration config)
    {
        return new AvroModelExt()
        {
            @Override
            public String name()
            {
                return "test";
            }

            @Override
            public AvroModelExtContext supply(
                EngineContext context)
            {
                return (schema, options) -> new AvroModelExtHandler()
                {
                    private final UppercaseTransform transform = new UppercaseTransform();

                    @Override
                    public AvroTransformable transform(
                        AvroTransformable stream)
                    {
                        return stream.transform(transform);
                    }
                };
            }
        };
    }

    private static final class UppercaseTransform implements AvroTransform
    {
        private static final byte[] EMPTY = new byte[0];

        private final Substitute substitute;
        private final StringBuilder captured;

        private boolean targeting;
        private boolean emitting;

        private UppercaseTransform()
        {
            this.substitute = new Substitute();
            this.captured = new StringBuilder();
        }

        @Override
        public Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event,
            AvroSink sink)
        {
            Status status;
            switch (event)
            {
            case START_MESSAGE:
                targeting = false;
                captured.setLength(0);
                status = sink.transform(control, source, event);
                break;
            case FIELD_NAME:
                targeting = TARGET_FIELD.equals(source.getField());
                status = sink.transform(control, source, event);
                break;
            case STRING:
                status = targeting
                    ? onTargetString(source, sink)
                    : sink.transform(control, source, event);
                break;
            default:
                status = sink.transform(control, source, event);
                break;
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
            targeting = false;
            emitting = false;
            captured.setLength(0);
        }

        // mirrors AvroSource#deferredBytes(): a STRING value larger than the input window arrives over
        // several chunks, so the complete original text is accumulated until the final chunk before the
        // uppercased (or rejected) substitute can be computed
        private Status onTargetString(
            AvroSource source,
            AvroSink sink)
        {
            Status status;
            captured.append(source.getString());

            if (source.deferredBytes() == 0)
            {
                String original = captured.toString();
                captured.setLength(0);
                targeting = false;
                if (REJECT_VALUE.equals(original))
                {
                    status = Status.REJECTED;
                }
                else
                {
                    byte[] upper = original.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
                    substitute.string(source.type(), upper);
                    status = emit(sink);
                }
            }
            else
            {
                status = Status.ADVANCED;
            }
            return status;
        }

        private Status emit(
            AvroSink sink)
        {
            Status status = sink.transform(substitute, substitute, AvroEvent.STRING);
            emitting = status == Status.SUSPENDED;
            return status;
        }

        // the synthesized view fed to the downstream sink in place of the original string value
        private static final class Substitute implements AvroSource, AvroController
        {
            private final UnsafeBufferEx content;
            private final UnsafeBufferEx view;

            private AvroEvent event;
            private AvroType type;
            private int length;
            private int progress;

            private Substitute()
            {
                this.content = new UnsafeBufferEx(EMPTY);
                this.view = new UnsafeBufferEx(EMPTY);
            }

            private void string(
                AvroType type,
                byte[] bytes)
            {
                this.event = AvroEvent.STRING;
                this.type = type;
                this.progress = 0;
                this.length = bytes.length;
                content.wrap(bytes, 0, bytes.length);
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
                return false;
            }

            @Override
            public int getInt()
            {
                return 0;
            }

            @Override
            public long getLong()
            {
                return 0L;
            }

            @Override
            public float getFloat()
            {
                return 0f;
            }

            @Override
            public double getDouble()
            {
                return 0d;
            }

            @Override
            public String getString()
            {
                return "";
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
                view.wrap(content, progress, length - progress);
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
}
