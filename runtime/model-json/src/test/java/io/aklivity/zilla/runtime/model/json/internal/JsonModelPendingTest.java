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

import static io.aklivity.zilla.runtime.engine.util.Flags.COMPLETE;
import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;
import io.aklivity.zilla.runtime.common.json.JsonTransformable;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.json.ext.JsonCache;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtContext;
import io.aklivity.zilla.runtime.model.json.ext.JsonModelExtHandler;

public class JsonModelPendingTest
{
    private static final String SCHEMA = """
        {
            "type": "object",
            "properties":
            {
                "id": { "type": "string" },
                "status": { "type": "string" },
                "tags": { "type": "array", "items": { "type": "string" } }
            },
            "required": [ "id", "status" ]
        }""";

    private static final long BINDING_ID = 0x0000_0001_0000_0007L;

    private final Deque<Runnable> dispatch = new ArrayDeque<>();
    private final List<Long> bindingIds = new ArrayList<>();

    private EngineContext context;
    private int resumed;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldResumeValueInsideObject()
    {
        shouldResume("{\"id\":\"A\",\"status\":\"wait\"}", 256, 1);
    }

    @Test
    public void shouldResumeFirstField()
    {
        shouldResume("{\"id\":\"wait\",\"status\":\"OK\"}", 256, 1);
    }

    @Test
    public void shouldResumeLastField()
    {
        shouldResume("{\"id\":\"A\",\"status\":\"OK\",\"tags\":[\"x\"],\"note\":\"wait\"}", 256, 1);
    }

    @Test
    public void shouldResumeValueInsideArray()
    {
        shouldResume("{\"id\":\"A\",\"status\":\"OK\",\"tags\":[\"a\",\"wait\",\"b\"]}", 256, 1);
    }

    @Test
    public void shouldResumeFirstAndLastElementOfArray()
    {
        shouldResume("{\"id\":\"A\",\"status\":\"OK\",\"tags\":[\"wait\",\"b\",\"wait\"]}", 256, 2);
    }

    @Test
    public void shouldResumeThenOverflowOutput()
    {
        shouldResume("{\"id\":\"A\",\"status\":\"OK\",\"tags\":[\"a\",\"wait\",\"bbbbbbbbbbbb\",\"cccccccccccc\"]}", 16, 1);
    }

    @Test
    public void shouldAcceptNoNewInputWhilePending()
    {
        byte[] in = "{\"id\":\"A\",\"status\":\"wait\"}".getBytes(UTF_8);
        ModelPipeline pipeline = newPipeline();
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);

        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.SUSPENDED, result.status());
        assertEquals(0, result.consumed());

        result = pipeline.transform(0L, 0L, 0L, FIN, new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.SUSPENDED, result.status());
        assertEquals(0, result.consumed());
        assertEquals(0, resumed);

        dispatch.poll().run();
        assertEquals(1, resumed);
    }

    private void shouldResume(
        String json,
        int dstLength,
        int waits)
    {
        byte[] in = json.getBytes(UTF_8);
        UnsafeBufferEx src = new UnsafeBufferEx(in);
        ModelPipeline pipeline = newPipeline();
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[dstLength]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int index = 0;
        int flags = COMPLETE;
        int suspensions = 0;
        ModelStatus status = null;
        while (status != ModelStatus.COMPLETE)
        {
            ModelPipelineResult result =
                pipeline.transform(0L, BINDING_ID, 0L, flags, src, index, in.length, dst, 0, dst.capacity());
            status = result.status();
            assertTrue(status.name(), status != ModelStatus.REJECTED && status != ModelStatus.UNDERFLOW);
            byte[] chunk = new byte[result.produced()];
            dst.getBytes(0, chunk);
            out.writeBytes(chunk);
            index += result.consumed();
            flags = FIN;
            if (status == ModelStatus.SUSPENDED)
            {
                suspensions++;
                assertEquals(1, dispatch.size());
                assertEquals(suspensions - 1, resumed);
                dispatch.poll().run();
                assertEquals(suspensions, resumed);
            }
        }

        assertEquals(waits, suspensions);
        assertEquals(json, out.toString(UTF_8));
        assertEquals(waits * 2, bindingIds.size());
        assertTrue(bindingIds.stream().allMatch(id -> id == BINDING_ID));
    }

    private ModelPipeline newPipeline()
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
        JsonModelHandlerImpl handler = new JsonModelHandlerImpl(model, context, List.of(waiting()));
        return handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, () -> resumed++);
    }

    private JsonModelExtContext waiting()
    {
        return (schema, config) -> new JsonModelExtHandler()
        {
            @Override
            public <T extends JsonTransformable<T>> T decode(
                T stream,
                JsonCache cache)
            {
                return stream.transform(new Waiting());
            }
        };
    }

    // completes asynchronously through the dispatch queue: a "wait" string value returns PENDING the first
    // time it is presented, and is presented again, once, when the pipeline re-enters the stage after resumption
    private final class Waiting implements JsonTransform
    {
        private boolean awaiting;
        private boolean completed;

        @Override
        public Status transform(
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status;
            if (event == JsonEvent.VALUE_STRING && "wait".contentEquals(source.getStringView()))
            {
                bindingIds.add(control.bindingId());
                Runnable resumer = control.resumer();
                dispatch.add(() ->
                {
                    completed = true;
                    resumer.run();
                });
                awaiting = true;
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
            JsonController control,
            JsonSource source,
            JsonEvent event,
            JsonSink sink)
        {
            Status status;
            if (awaiting && !completed)
            {
                status = Status.PENDING;
            }
            else if (awaiting)
            {
                awaiting = false;
                completed = false;
                bindingIds.add(control.bindingId());
                status = sink.transform(control, source, event);
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
            awaiting = false;
            completed = false;
        }

        @Override
        public boolean deterministic()
        {
            return false;
        }
    }
}
