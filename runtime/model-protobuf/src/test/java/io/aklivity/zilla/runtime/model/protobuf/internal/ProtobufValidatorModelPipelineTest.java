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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

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
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufValidatorModelPipelineTest
{
    private static final String SCHEMA = """
                                            syntax = "proto3";
                                            package io.aklivity.examples.clients.proto;
                                            message SimpleMessage {
                                                string content = 1;
                                                optional string date_time = 2;
                                            }
                                            """;

    // message index 0, then content="OK" (field 1) and date_time="01012024" (field 2)
    private static final byte[] WIRE =
        {0x00, 0x0a, 0x02, 0x4f, 0x4b, 0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldValidateWholeValue()
    {
        ProtobufValidatorModelHandler handler = newHandler();
        ModelPipeline pipeline = handler.supplyPipeline(ModelVisitor.NONE);

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(WIRE.length, result.consumed());
        // validation produces no output for the caller; the original bytes are forwarded
        assertEquals(0, result.produced());

        // reset and reuse the same pipeline for the next value
        pipeline.reset();
        ModelPipelineResult reused = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, reused.status());
        assertEquals(WIRE.length, reused.consumed());
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        ProtobufValidatorModelHandler handler = newHandler();
        // two per-stream pipelines vended by one per-worker handler share the scratch buffer but must not
        // share parse state; interleaving two distinct values exercises that isolation
        ModelPipeline a = handler.supplyPipeline(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyPipeline(ModelVisitor.NONE);

        byte[] a1 = {0x00, 0x0a, 0x02, 0x4f, 0x4b};
        byte[] a2tail = {0x12, 0x08, 0x30, 0x31, 0x30, 0x31, 0x32, 0x30, 0x32, 0x34};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);

        // stream A: first fragment, incomplete -> UNDERFLOW
        ModelPipelineResult ra1 = a.transform(0L, 0L, ModelPipeline.FLAGS_INIT,
            new UnsafeBuffer(a1), 0, a1.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.UNDERFLOW, ra1.status());

        // stream B: a whole value validated in the middle of A — would corrupt A if state were shared
        ModelPipelineResult rb = b.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(WIRE), 0, WIRE.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, rb.status());
        assertEquals(WIRE.length, rb.consumed());

        // stream A: finish, prepending A's unconsumed remainder
        byte[] a2 = concat(a1, ra1.consumed(), a2tail);
        ModelPipelineResult ra2 = a.transform(0L, 0L, ModelPipeline.FLAGS_FIN,
            new UnsafeBuffer(a2), 0, a2.length, dst, 0, dst.capacity());
        assertEquals(ModelStatus.COMPLETE, ra2.status());
    }

    @Test
    public void shouldValidateLargeValue()
    {
        ProtobufValidatorModelHandler handler = newHandler();
        ModelPipeline pipeline = handler.supplyPipeline(ModelVisitor.NONE);

        // a 10000-byte content field forces the re-encode past the 8192-byte scratch window, exercising the
        // internal SUSPENDED drain loop that discards the validation output
        byte[] contentBytes = "A".repeat(10000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] wire = new byte[contentBytes.length + 8];
        MutableDirectBuffer builder = new UnsafeBuffer(wire);
        int p = 0;
        builder.putByte(p++, (byte) 0x00);                  // message index 0
        builder.putByte(p++, (byte) 0x0a);                  // field 1 (content), wire type LEN
        builder.putByte(p++, (byte) 0x90);                  // length 10000 varint, byte 0
        builder.putByte(p++, (byte) 0x4e);                  // length 10000 varint, byte 1
        builder.putBytes(p, contentBytes);
        p += contentBytes.length;

        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(wire), 0, p, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(p, result.consumed());
        assertEquals(0, result.produced());
    }

    @Test
    public void shouldRejectInvalid()
    {
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        ProtobufValidatorModelHandler handler = newHandler();
        ModelPipeline pipeline = handler.supplyPipeline(ModelVisitor.NONE);

        // index byte, then content length prefix promises 8 bytes but only "OK" follows under FLAGS_COMPLETE
        byte[] invalid = {0x00, 0x0a, 0x08, 0x4f, 0x4b};
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[64]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(invalid), 0, invalid.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    private ProtobufValidatorModelHandler newHandler()
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();
        ProtobufModelConfig model = ProtobufModelConfig.builder()
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
        return new ProtobufValidatorModelHandler(model, context);
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
