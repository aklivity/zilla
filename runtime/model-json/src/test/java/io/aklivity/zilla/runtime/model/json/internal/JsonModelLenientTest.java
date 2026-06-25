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
package io.aklivity.zilla.runtime.model.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.ValidateConfig;
import io.aklivity.zilla.runtime.engine.config.ValidateMode;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonModelLenientTest
{
    private static final int FLAGS_COMPLETE = 0x03;

    // id must be a string; a numeric id is a schema-constraint violation on structurally well-formed JSON
    private static final String OBJECT_SCHEMA = "{" +
        "\"type\": \"object\"," +
        "\"properties\": { \"id\": { \"type\": \"string\" } }," +
        "\"required\": [ \"id\" ]" +
        "}";

    private EngineContext context;
    private MessageConsumer eventWriter;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        eventWriter = mock(MessageConsumer.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(eventWriter);
    }

    // decode vs encode independence: {decode: lenient, encode: strict} relaxes only the read side.
    @Test
    public void shouldRelaxDecodeOnlyWhenEncodeStrict()
    {
        JsonModelHandlerImpl handler = newHandler(
            new ValidateConfig(ValidateMode.LENIENT, ValidateMode.STRICT));

        byte[] in = "{\"id\":123}".getBytes(UTF_8);

        ModelPipeline decoder = handler.supplyDecoder(ModelVisitor.NONE);
        MutableDirectBuffer decodeDst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult decoded = decoder.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, decodeDst, 0, decodeDst.capacity());
        assertEquals(ModelStatus.COMPLETE, decoded.status());

        ModelPipeline encoder = handler.supplyEncoder(ModelVisitor.NONE);
        MutableDirectBuffer encodeDst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult encoded = encoder.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, encodeDst, 0, encodeDst.capacity());
        assertEquals(ModelStatus.REJECTED, encoded.status());
    }

    // A conforming document completes cleanly and reports nothing.
    @Test
    public void shouldCompleteConformingUnderLenient()
    {
        ModelPipeline pipeline = newHandler(lenient()).supplyDecoder(ModelVisitor.NONE);

        byte[] in = "{\"id\":\"abc\"}".getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals("{\"id\":\"abc\"}", text(dst, result.produced()));
        verify(eventWriter, never()).accept(anyInt(), any(DirectBuffer.class), anyInt(), anyInt());
    }

    private static ValidateConfig strict()
    {
        return new ValidateConfig(ValidateMode.STRICT, ValidateMode.STRICT);
    }

    private static ValidateConfig lenient()
    {
        return new ValidateConfig(ValidateMode.LENIENT, ValidateMode.LENIENT);
    }

    private JsonModelHandlerImpl newHandler(
        ValidateConfig validate)
    {
        TestCatalogConfig catalog = CatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(OBJECT_SCHEMA)
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
            .validate(validate)
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new JsonModelHandlerImpl(model, context);
    }

    private static String text(
        MutableDirectBuffer dst,
        int produced)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(0, chunk);
        return new String(chunk, UTF_8);
    }
}
