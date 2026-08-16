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
package io.aklivity.zilla.runtime.model.json.internal;

import java.math.BigDecimal;
import java.util.Locale;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonTransformable;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExt;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtContext;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtFactorySpi;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtHandler;

// Test-only JsonModelExt, registered only under src/test/resources/META-INF/services so it never ships
// in the production jar. Exercises the same JsonModelExt composition mechanism a real installed
// extension relies on -- apply on both decode and encode, fragment accumulation across multiple
// VALUE_STRING chunks, drain across a SUSPENDED terminal generator, and reject via
// JsonPipeline.Status.REJECTED -- without model-json needing to know anything about what a real
// extension might do with it. Uppercases the string value of any field literally named "text"; a
// captured value of "REJECT" rejects the datum.
public final class TestUppercaseJsonModelExtFactorySpi implements JsonModelExtFactorySpi
{
    private static final String TARGET_FIELD = "text";
    private static final String REJECT_VALUE = "REJECT";

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public JsonModelExt create(
        Configuration config)
    {
        return new JsonModelExt()
        {
            @Override
            public String name()
            {
                return "test";
            }

            @Override
            public JsonModelExtContext supply(
                EngineContext context)
            {
                return (schema, options) -> new JsonModelExtHandler()
                {
                    private final UppercaseTransform transform = new UppercaseTransform();

                    @Override
                    public <T extends JsonTransformable<T>> T decode(
                        T stream)
                    {
                        return stream.transform(transform);
                    }

                    @Override
                    public <T extends JsonTransformable<T>> T encode(
                        T stream)
                    {
                        return stream.transform(transform);
                    }
                };
            }
        };
    }

    private static final class UppercaseTransform implements JsonTransform
    {
        private final Substitute substitute;
        private final StringBuilder captured;

        private boolean targeting;
        private boolean emitting;

        // declines both segmentable() and verbatim() (no-op, does not forward either upstream), so every
        // event downstream is offered is always canonically re-rendered -- this transform inspects
        // KEY_NAME/VALUE_STRING content and substitutes a plain-String-backed Substitute, neither of
        // which supports a raw byte segment or verbatim splice
        private final JsonController mediator = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }

            @Override
            public void consumed(
                int sourceChars)
            {
                upstream.consumed(sourceChars);
            }
        };

        private JsonController upstream;

        private UppercaseTransform()
        {
            this.substitute = new Substitute();
            this.captured = new StringBuilder();
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            upstream = control;
            Status status;
            switch (event)
            {
            case START_DOCUMENT:
                targeting = false;
                captured.setLength(0);
                status = sink.transform(mediator, source, event);
                break;
            case KEY_NAME:
                targeting = TARGET_FIELD.equals(source.getString());
                status = sink.transform(mediator, source, event);
                break;
            case VALUE_STRING:
                status = targeting
                    ? onTargetString(source, sink)
                    : sink.transform(mediator, source, event);
                break;
            default:
                status = sink.transform(mediator, source, event);
                break;
            }
            return status;
        }

        @Override
        public Status resume(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status;
            if (emitting)
            {
                status = sink.resume(substitute, substitute, substitute.event);
                emitting = status == Status.SUSPENDED;
            }
            else
            {
                status = sink.resume(mediator, source, event);
            }
            return status;
        }

        @Override
        public Status flush(
            JsonController control,
            JsonSource source,
            JsonSink sink)
        {
            upstream = control;
            return sink.flush(mediator, source);
        }

        @Override
        public void reset()
        {
            targeting = false;
            emitting = false;
            captured.setLength(0);
        }

        // mirrors JsonSource#deferredBytes(): a string value larger than the input window arrives over
        // several chunks, so the complete original text is accumulated until the final chunk before the
        // uppercased (or rejected) substitute can be computed
        private Status onTargetString(
            JsonSource source,
            JsonSink sink)
        {
            Status status;
            captured.append(source.getStringView());

            if (!source.deferredBytes())
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
                    substitute.string(original.toUpperCase(Locale.ROOT));
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
            JsonSink sink)
        {
            Status status = sink.transform(substitute, substitute, JsonEvent.VALUE_STRING);
            emitting = status == Status.SUSPENDED;
            return status;
        }

        // the synthesized view fed to the downstream sink in place of the original string value
        private static final class Substitute implements JsonSource, JsonController
        {
            private JsonEvent event;
            private String content;
            private int progress;

            private void string(
                String value)
            {
                this.event = JsonEvent.VALUE_STRING;
                this.content = value;
                this.progress = 0;
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
                return content.subSequence(progress, content.length());
            }

            @Override
            public BigDecimal getBigDecimal()
            {
                return BigDecimal.ZERO;
            }

            @Override
            public boolean isIntegralNumber()
            {
                return true;
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
}
