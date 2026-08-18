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
import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufController;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnvelope;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufEvent;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSink;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSource;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransform;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufTransformable;
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
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtContext;
import io.aklivity.zilla.runtime.model.protobuf.ext.ProtobufModelExtHandler;

public class ProtobufModelEnvelopeTest
{
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String SCHEMA = """
                                            syntax = "proto3";
                                            package io.aklivity.examples.clients.proto;
                                            message SimpleMessage {
                                                string content = 1;
                                            }
                                            """;

    // a single index byte, then content="OK"
    private static final byte[] WIRE = {0x00, 0x0a, 0x02, 0x4f, 0x4b};
    // the encoder's own input: the json view of the same message, which it encodes into WIRE
    private static final byte[] JSON = "{\"content\":\"OK\"}".getBytes(UTF_8);

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

        ProtobufModelHandlerImpl handler = newHandler(List.of(echoingExt("mark", "echo")));
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

        ProtobufModelHandlerImpl handler = newHandler(List.of(capturingExt("captured")));
        ModelPipeline pipeline = handler.supplyEncoder(envelope, ModelTransform.NONE);

        transform(pipeline, JSON);

        assertEquals(1, envelope.count("captured"));
        assertEquals("OK", text(envelope.get("captured", 0)));
    }

    @Test
    public void shouldReadEnvelopeSuppliedToDecoderFromFormatNativeStageAndModelTransform()
    {
        Metadata envelope = new Metadata();
        envelope.set("mark", buffer("one"));

        // the caller composes its own generic stage over the same envelope it supplies to the pipeline, so
        // both vocabularies observe one store
        Reading reading = new Reading(envelope, "mark");
        ProtobufModelHandlerImpl handler = newHandler(List.of(echoingExt("mark", "echo")));
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

        ProtobufModelHandlerImpl handler = newHandler(List.of(observingExt(observing)));
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE);

        transform(pipeline);

        assertSame(ProtobufEnvelope.NONE, observing.envelope);
    }

    private static void transform(
        ModelPipeline pipeline)
    {
        transform(pipeline, WIRE);
    }

    private static void transform(
        ModelPipeline pipeline,
        byte[] in)
    {
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
    }

    private ProtobufModelHandlerImpl newHandler(
        List<ProtobufModelExtContext> exts)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("SimpleMessage")
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new ProtobufModelHandlerImpl(model, context, exts);
    }

    // an extension whose format-native stage echoes every value the envelope carries under one name back
    // under another, so what a stage read is observable from the envelope the caller supplied
    private static ProtobufModelExtContext echoingExt(
        String source,
        String target)
    {
        return (schema, options) -> new ProtobufModelExtHandler()
        {
            private final ProtobufTransform transform = new Echoing(source, target);

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

    // an extension whose format-native stage writes each string value it observes into the envelope
    private static ProtobufModelExtContext capturingExt(
        String name)
    {
        return (schema, options) -> new ProtobufModelExtHandler()
        {
            private final ProtobufTransform transform = new Capturing(name);

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

    private static ProtobufModelExtContext observingExt(
        ProtobufTransform observing)
    {
        return (schema, options) -> new ProtobufModelExtHandler()
        {
            @Override
            public <T extends ProtobufTransformable<T>> T decode(
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
    private static final class Echoing implements ProtobufTransform
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
            ProtobufController control,
            ProtobufSource avroSource,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            if (!echoed)
            {
                echoed = true;
                ProtobufEnvelope envelope = control.envelope();
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
    private static final class Capturing implements ProtobufTransform
    {
        private final String name;

        private Capturing(
            String name)
        {
            this.name = name;
        }

        @Override
        public Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
        {
            if (event == ProtobufEvent.VALUE)
            {
                control.envelope().set(name, source.segment());
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
    private static final class Observing implements ProtobufTransform
    {
        private ProtobufEnvelope envelope;

        @Override
        public Status transform(
            ProtobufController control,
            ProtobufSource source,
            ProtobufEvent event,
            ProtobufSink sink)
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
