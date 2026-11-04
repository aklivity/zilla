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

import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSource;
import io.aklivity.zilla.runtime.common.avro.AvroTransform;
import io.aklivity.zilla.runtime.common.avro.AvroTransformable;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.avro.ext.AvroCache;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtContext;
import io.aklivity.zilla.runtime.model.avro.ext.AvroModelExtHandler;

public class AvroModelPendingTest
{
    private static final String RECORD_SCHEMA = """
        {
            "fields":
            [
                { "name": "id", "type": "string" },
                { "name": "text", "type": "string" },
                { "name": "tail", "type": "string" }
            ],
            "name": "Event",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final String ARRAY_SCHEMA = """
        {
            "fields":
            [
                { "name": "items", "type": { "type": "array", "items": "string" } },
                { "name": "tail", "type": "string" }
            ],
            "name": "Event",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final String MAP_SCHEMA = """
        {
            "fields":
            [
                { "name": "attributes", "type": { "type": "map", "values": "string" } },
                { "name": "tail", "type": "string" }
            ],
            "name": "Event",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final long ENTRY_BINDING_ID = 7L;
    private static final long REENTRY_BINDING_ID = 8L;

    private EngineContext context;
    private AvroModelConfiguration config;
    private PendingTransform pending;
    private int resumed;

    @Before
    public void init()
    {
        config = new AvroModelConfiguration(new Configuration());
        context = mock(EngineContext.class);
        pending = new PendingTransform();
        resumed = 0;
    }

    @Test
    public void shouldSuspendDecodeWhilePendingInsideRecord()
    {
        byte[] value = concat(string("id0"), string("wait-text"), string("tail"));
        ModelPipeline pipeline = decoder(RECORD_SCHEMA, ModelTransform.NONE);

        assertArrayEquals(value, assertSuspendedThenResumed(pipeline, value, 256));
    }

    @Test
    public void shouldSuspendEncodeWhilePendingInsideRecord()
    {
        byte[] value = concat(string("id0"), string("wait-text"), string("tail"));
        ModelPipeline pipeline =
            newHandler(RECORD_SCHEMA).supplyEncoder(ModelEnvelope.NONE, ModelTransform.NONE, this::onResumed);

        assertArrayEquals(value, assertSuspendedThenResumed(pipeline, value, 256));
    }

    @Test
    public void shouldSuspendDecodeWhilePendingInsideArray()
    {
        byte[] value = concat(varint(1), string("a"), varint(1), string("wait-b"), varint(0), string("tail"));
        ModelPipeline pipeline = decoder(ARRAY_SCHEMA, ModelTransform.NONE);

        assertArrayEquals(value, assertSuspendedThenResumed(pipeline, value, 256));
    }

    @Test
    public void shouldSuspendDecodeWhilePendingInsideMap()
    {
        byte[] value = concat(varint(1), string("key"), string("wait-value"), varint(0), string("tail"));
        ModelPipeline pipeline = decoder(MAP_SCHEMA, ModelTransform.NONE);

        assertArrayEquals(value, assertSuspendedThenResumed(pipeline, value, 256));
    }

    @Test
    public void shouldSuspendWhileCapturedStringIsPending()
    {
        byte[] value = concat(string("id0"), string("wait-text"), string("tail"));
        byte[] expected = concat(string("id0"), string("WAIT-TEXT"), string("tail"));
        ModelPipeline pipeline = decoder(RECORD_SCHEMA, new Uppercase());

        assertArrayEquals(expected, assertSuspendedThenResumed(pipeline, value, 256));
    }

    @Test
    public void shouldOverflowAfterResumption()
    {
        byte[] value = concat(string("id0"), string("wait-text"), string("tail"));
        ModelPipeline pipeline = decoder(RECORD_SCHEMA, ModelTransform.NONE);
        UnsafeBufferEx src = new UnsafeBufferEx(value);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[6]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, COMPLETE, src, 0, value.length, dst, 0, dst.capacity());
        drain(dst, result.produced(), drained);
        assertEquals(ModelStatus.SUSPENDED, result.status());

        pending.complete();
        assertEquals(1, resumed);

        int overflows = 0;
        do
        {
            result = pipeline.transform(0L, 0L, 0L, FIN, src, 0, value.length, dst, 0, dst.capacity());
            drain(dst, result.produced(), drained);
            overflows += result.status() == ModelStatus.OVERFLOW ? 1 : 0;
        }
        while (result.status() == ModelStatus.OVERFLOW);

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(true, overflows > 0);
        assertArrayEquals(value, drained.toByteArray());
        assertEquals(List.of("STRING:wait-text", "resume:STRING"), pending.presented);
    }

    @Test
    public void shouldAcceptNoNewInputWhileSuspended()
    {
        byte[] value = concat(string("id0"), string("wait-text"), string("tail"));
        ModelPipeline pipeline = decoder(RECORD_SCHEMA, ModelTransform.NONE);
        UnsafeBufferEx src = new UnsafeBufferEx(value);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);

        ModelPipelineResult first = pipeline.transform(0L, 0L, 0L, COMPLETE, src, 0, value.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.SUSPENDED, first.status());

        byte[] extra = string("more");
        ModelPipelineResult second = pipeline.transform(0L, 0L, 0L, FIN,
            new UnsafeBufferEx(extra), 0, extra.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.SUSPENDED, second.status());
        assertEquals(0, second.consumed());
        assertEquals(0, second.produced());
        assertEquals(0, resumed);
    }

    private byte[] assertSuspendedThenResumed(
        ModelPipeline pipeline,
        byte[] value,
        int capacity)
    {
        UnsafeBufferEx src = new UnsafeBufferEx(value);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[capacity]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();

        ModelPipelineResult result = pipeline.transform(0L, ENTRY_BINDING_ID, 0L, COMPLETE,
            src, 0, value.length, dst, 0, dst.capacity());
        drain(dst, result.produced(), drained);
        assertEquals(ModelStatus.SUSPENDED, result.status());
        assertEquals(0, result.consumed());
        assertEquals(0, resumed);

        pending.complete();
        assertEquals(1, resumed);

        result = pipeline.transform(0L, REENTRY_BINDING_ID, 0L, FIN, src, 0, value.length, dst, 0, dst.capacity());
        drain(dst, result.produced(), drained);
        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(1, pending.lookups);
        assertEquals(List.of(ENTRY_BINDING_ID, REENTRY_BINDING_ID), pending.bindingIds);

        return drained.toByteArray();
    }

    private ModelPipeline decoder(
        String schema,
        ModelTransform transform)
    {
        return newHandler(schema).supplyDecoder(ModelEnvelope.NONE, transform, this::onResumed);
    }

    private void onResumed()
    {
        resumed++;
    }

    private AvroModelHandlerImpl newHandler(
        String schema)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(schema)
                .build()
            .build();
        AvroModelConfig model = AvroModelConfig.builder()
            .catalog()
                .name("test0")
                    .schema()
                        .strategy("topic")
                        .version("latest")
                        .subject("test-value")
                        .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        AvroModelExtContext ext = (s, options) -> new AvroModelExtHandler()
        {
            @Override
            public <T extends AvroTransformable<T>> T decode(
                T transformable,
                AvroCache cache)
            {
                return transformable.transform(pending);
            }

            @Override
            public <T extends AvroTransformable<T>> T encode(
                T transformable)
            {
                return transformable.transform(pending);
            }
        };
        return new AvroModelHandlerImpl(config, model, context, List.of(ext));
    }

    private static void drain(
        MutableDirectBufferEx dst,
        int produced,
        ByteArrayOutputStream sink)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(0, chunk);
        sink.writeBytes(chunk);
    }

    private static byte[] string(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        return concat(varint(bytes.length), bytes);
    }

    private static byte[] varint(
        long count)
    {
        long zigzag = (count << 1) ^ (count >> 63);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((zigzag & ~0x7FL) != 0)
        {
            out.write((int) ((zigzag & 0x7F) | 0x80));
            zigzag >>>= 7;
        }
        out.write((int) zigzag);
        return out.toByteArray();
    }

    private static byte[] concat(
        byte[]... parts)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts)
        {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    // stands in for a transform whose answer arrives from another thread through the engine's signaler: the
    // string value "wait..." starts a lookup, and complete() is the dispatch that finishes it on the I/O
    // thread by running the callback the pipeline supplied
    private static final class PendingTransform implements AvroTransform
    {
        private final List<String> presented = new ArrayList<>();
        private final List<Long> bindingIds = new ArrayList<>();

        private Runnable resumer;
        private boolean waiting;
        private boolean answered;
        private int lookups;

        @Override
        public Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event,
            AvroSink sink)
        {
            Status status;
            if (event == AvroEvent.STRING && source.getString().startsWith("wait"))
            {
                presented.add("STRING:" + source.getString());
                resumer = control.resumer();
                bindingIds.add(control.bindingId());
                lookups++;
                waiting = true;
                status = Status.PENDING;
            }
            else
            {
                status = sink.transform(control, source, event);
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
            if (waiting)
            {
                if (answered)
                {
                    presented.add("resume:" + event);
                    bindingIds.add(control.bindingId());
                    waiting = false;
                    answered = false;
                    status = sink.transform(control, source, event);
                }
                else
                {
                    status = Status.PENDING;
                }
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
            waiting = false;
            answered = false;
        }

        @Override
        public boolean deterministic()
        {
            return false;
        }

        private void complete()
        {
            answered = true;
            resumer.run();
        }
    }

    private static final class Uppercase implements ModelTransform
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event,
            ModelSink sink)
        {
            ModelStatus status;
            if (source.getPath() != null && source.getPath().endsWith(".text"))
            {
                String text = source.getValue().getStringWithoutLengthUtf8(0, source.getValue().capacity());
                byte[] upper = text.toUpperCase(Locale.ROOT).getBytes(UTF_8);
                status = sink.transform(control, new ModelSource()
                {
                    @Override
                    public String getPath()
                    {
                        return source.getPath();
                    }

                    @Override
                    public DirectBufferEx getValue()
                    {
                        return new UnsafeBufferEx(upper);
                    }
                }, ModelEvent.REPLACED);
            }
            else
            {
                status = sink.transform(control, source, event);
            }
            return status;
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public boolean deterministic()
        {
            return true;
        }
    }
}
