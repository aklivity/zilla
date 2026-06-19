/*
 * Copyright 2021-2024 Aklivity Inc
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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroValidatorModelPipelineTest
{
    private static final String SCHEMA = "{\"fields\":[{\"name\":\"id\",\"type\":\"string\"}," +
        "{\"name\":\"status\",\"type\":\"string\"}]," +
        "\"name\":\"Event\",\"namespace\":\"io.aklivity.example\",\"type\":\"record\"}";

    // id="id0" (len 3) then status="positive" (len 8)
    private static final byte[] AVRO = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};

    private EngineContext context;
    private AvroModelConfiguration config;

    @Before
    public void init()
    {
        config = new AvroModelConfiguration(new Configuration());
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldValidateWholeValue()
    {
        AvroValidatorModelHandler handler = newHandler();
        ModelPipeline pipeline = handler.supplyPipeline(ModelVisitor.NONE);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(AVRO), 0, AVRO.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(AVRO.length, result.consumed());
        // validation produces no output for the caller; the original bytes are forwarded
        assertEquals(0, result.produced());
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        AvroValidatorModelHandler handler = newHandler();
        // two per-stream pipelines vended by one per-worker handler share the scratch buffer but must not
        // share parse state; interleaving two distinct values exercises that isolation
        ModelPipeline a = handler.supplyPipeline(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyPipeline(ModelVisitor.NONE);

        byte[] a1 = {0x06, 0x69, 0x64, 0x30};
        byte[] a2tail = {0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, ModelPipeline.FLAGS_INIT,
            new UnsafeBuffer(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());

        // stream B: a whole value validated in the middle of A — would corrupt A if state were shared
        ModelPipelineResult rb = b.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(AVRO), 0, AVRO.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        assertEquals(AVRO.length, rb.consumed());

        // stream A: finish, prepending A's unconsumed remainder
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, ModelPipeline.FLAGS_FIN,
            new UnsafeBuffer(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
    }

    @Test
    public void shouldRejectInvalid()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        AvroValidatorModelHandler handler = newHandler();
        ModelPipeline pipeline = handler.supplyPipeline(ModelVisitor.NONE);

        // truncated: the status field is missing under FLAGS_FIN
        byte[] invalid = {0x06, 0x69, 0x64, 0x30, 0x10};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(invalid), 0, invalid.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    private AvroValidatorModelHandler newHandler()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
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
        return new AvroValidatorModelHandler(config, model, context);
    }

    private static byte[] concat(
        byte[] head,
        int headOffset,
        byte[] tail)
    {
        int headLength = head.length - headOffset;
        byte[] result = new byte[headLength + tail.length];
        System.arraycopy(head, headOffset, result, 0, headLength);
        System.arraycopy(tail, 0, result, headLength, tail.length);
        return result;
    }
}
