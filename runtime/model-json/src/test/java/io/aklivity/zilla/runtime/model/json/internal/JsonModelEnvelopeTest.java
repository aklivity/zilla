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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonTransformable;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtContext;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtHandler;

public class JsonModelEnvelopeTest
{
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String SCHEMA = """
        {
            "type": "object",
            "properties":
            {
                "id": { "type": "string" }
            },
            "required": [ "id" ]
        }""";

    private static final byte[] JSON = "{\"id\":\"id0\"}".getBytes(UTF_8);

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldReadEnvelopeSuppliedToDecoderFromFormatNativeStage()
    {
        Metadata envelope = new Metadata();
        envelope.set("mark", buffer("one"));
        envelope.set("mark", buffer("two"));

        JsonModelHandlerImpl handler = newHandler(List.of(echoingExt("mark", "echo")));
        ModelPipeline pipeline = handler.supplyDecoder(envelope, ModelTransform.NONE);

        transform(pipeline);

        assertEquals(2, envelope.count("echo"));
        assertEquals("one", text(envelope.get("echo", 0)));
        assertEquals("two", text(envelope.get("echo", 1)));
    }

    @Test
    public void shouldWriteEnvelopeSuppliedToEncoderFromFormatNativeStage()
    {
        Metadata envelope = new Metadata();

        JsonModelHandlerImpl handler = newHandler(List.of(capturingExt("captured")));
        ModelPipeline pipeline = handler.supplyEncoder(envelope, ModelTransform.NONE);

        transform(pipeline);

        assertEquals(1, envelope.count("captured"));
        assertEquals("id0", text(envelope.get("captured", 0)));
    }

    @Test
    public void shouldReadEnvelopeSuppliedToDecoderFromFormatNativeStageAndModelTransform()
    {
        Metadata envelope = new Metadata();
        envelope.set("mark", buffer("one"));

        // the caller composes its own generic stage over the same envelope it supplies to the pipeline, so
        // both vocabularies observe one store
        Reading reading = new Reading(envelope, "mark");
        JsonModelHandlerImpl handler = newHandler(List.of(echoingExt("mark", "echo")));
        ModelPipeline pipeline = handler.supplyDecoder(envelope, reading);

        transform(pipeline);

        assertEquals(List.of("one"), reading.read);
        assertEquals(1, envelope.count("echo"));
        assertEquals("one", text(envelope.get("echo", 0)));
    }

    @Test
    public void shouldSupplyNoneToFormatNativeStageWhenCallerSuppliesNone()
    {
        Observing observing = new Observing();

        JsonModelHandlerImpl handler = newHandler(List.of(observingExt(observing)));
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE);

        transform(pipeline);

        assertSame(JsonEnvelope.NONE, observing.envelope);
    }

    private static void transform(
        ModelPipeline pipeline)
    {
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(JSON), 0, JSON.length, dst, 0, dst.capacity());
    }

    private JsonModelHandlerImpl newHandler(
        List<JsonModelExtContext> exts)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        JsonModelConfig model = JsonModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .subject(null)
                    .version("latest")
                    .id(0)
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new JsonModelHandlerImpl(model, context, exts);
    }

    // an extension whose format-native stage echoes every value the envelope carries under one name back
    // under another, so what a stage read is observable from the envelope the caller supplied
    private static JsonModelExtContext echoingExt(
        String source,
        String target)
    {
        return (schema, options) -> new JsonModelExtHandler()
        {
            private final JsonTransform transform = new Echoing(source, target);

            @Override
            public <T extends JsonTransformable<T>> T decode(
                T transformable)
            {
                return transformable.transform(transform);
            }

            @Override
            public <T extends JsonTransformable<T>> T encode(
                T transformable)
            {
                return transformable.transform(transform);
            }
        };
    }

    // an extension whose format-native stage writes each string value it observes into the envelope
    private static JsonModelExtContext capturingExt(
        String name)
    {
        return (schema, options) -> new JsonModelExtHandler()
        {
            private final JsonTransform transform = new Capturing(name);

            @Override
            public <T extends JsonTransformable<T>> T decode(
                T transformable)
            {
                return transformable.transform(transform);
            }

            @Override
            public <T extends JsonTransformable<T>> T encode(
                T transformable)
            {
                return transformable.transform(transform);
            }
        };
    }

    private static JsonModelExtContext observingExt(
        JsonTransform observing)
    {
        return (schema, options) -> new JsonModelExtHandler()
        {
            @Override
            public <T extends JsonTransformable<T>> T decode(
                T transformable)
            {
                return transformable.transform(observing);
            }
        };
    }

    private static DirectBufferEx buffer(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }

    private static String text(
        DirectBufferEx value)
    {
        return value.getStringWithoutLengthUtf8(0, value.capacity());
    }

    // the envelope a caller supplies to the pipeline, backed by a copy of each value it is given
    private static final class Metadata implements ModelEnvelope
    {
        private final Map<String, List<DirectBufferEx>> values = new LinkedHashMap<>();

        @Override
        public int count(
            String name)
        {
            List<DirectBufferEx> named = values.get(name);
            return named != null ? named.size() : 0;
        }

        @Override
        public DirectBufferEx get(
            String name,
            int index)
        {
            List<DirectBufferEx> named = values.get(name);
            return named != null && index < named.size() ? named.get(index) : null;
        }

        @Override
        public void set(
            String name,
            DirectBufferEx value)
        {
            byte[] copy = new byte[value.capacity()];
            value.getBytes(0, copy);
            values.computeIfAbsent(name, n -> new ArrayList<>()).add(new UnsafeBufferEx(copy));
        }
    }

    // format-native stage: copies every value under one name to another, once per datum
    private static final class Echoing implements JsonTransform
    {
        private final String source;
        private final String target;

        private boolean echoed;

        private Echoing(
            String source,
            String target)
        {
            this.source = source;
            this.target = target;
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource avroSource,
            JsonEvent event,
            JsonSink sink)
        {
            if (!echoed)
            {
                echoed = true;
                JsonEnvelope envelope = control.envelope();
                int count = envelope.count(source);
                for (int index = 0; index < count; index++)
                {
                    envelope.set(target, envelope.get(source, index));
                }
            }
            return sink.transform(control, avroSource, event);
        }

        @Override
        public void reset()
        {
            echoed = false;
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // format-native stage: writes each string value it observes into the envelope under one name
    private static final class Capturing implements JsonTransform
    {
        private final String name;

        private Capturing(
            String name)
        {
            this.name = name;
        }

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            if (event == JsonEvent.VALUE_STRING)
            {
                control.envelope().set(name, buffer(source.getString()));
            }
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // format-native stage: captures the envelope the pipeline supplies, without touching its contents
    private static final class Observing implements JsonTransform
    {
        private JsonEnvelope envelope;

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            envelope = control.envelope();
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }

    // a generic stage the caller composes over the same envelope it supplies to the pipeline
    private static final class Reading implements ModelTransform
    {
        private final ModelEnvelope envelope;
        private final String name;
        private final List<String> read = new ArrayList<>();

        private Reading(
            ModelEnvelope envelope,
            String name)
        {
            this.envelope = envelope;
            this.name = name;
        }

        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            if (read.isEmpty())
            {
                int count = envelope.count(name);
                for (int index = 0; index < count; index++)
                {
                    read.add(text(envelope.get(name, index)));
                }
            }
            return sink.transform(control, source, event);
        }

        @Override
        public boolean identity()
        {
            return true;
        }
    }
}
