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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonEncodeModelPipelineTest.java
import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
=======
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelEncoderPipelineTest.java
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonModelEncoderPipelineTest
{
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String OBJECT_SCHEMA = """
        {
            "type": "object",
            "properties":
            {
                "id": { "type": "string" },
                "status": { "type": "string" }
            },
            "required": [ "id", "status" ]
        }""";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
<<<<<<< HEAD:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonEncodeModelPipelineTest.java
    public void shouldTransformWholeValue()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        // the test catalog adds no framing prefix, so the output is the validated value itself
        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(in.length, result.consumed());
        byte[] out = new byte[result.produced()];
        dst.getBytes(0, out);
        assertEquals("{\"id\":\"123\",\"status\":\"OK\"}", new String(out, UTF_8));
    }

    @Test
    public void shouldRejectInvalidValue()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        // missing required "status"
        byte[] in = "{\"id\":\"123\"}".getBytes(UTF_8);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[256]);
        ModelPipelineResult result = pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
=======
>>>>>>> origin/develop:runtime/model-json/src/test/java/io/aklivity/zilla/runtime/model/json/internal/JsonModelEncoderPipelineTest.java
    public void shouldReportEncodePadding()
    {
        JsonModelHandlerImpl handler = newHandler();
        ModelPipeline pipeline = handler.supplyEncoder(ModelVisitor.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        assertTrue(pipeline.padding(new UnsafeBufferEx(in), 0, in.length) >= 0);
    }

    private JsonModelHandlerImpl newHandler()
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
                    .id(9)
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        return new JsonModelHandlerImpl(model, context);
    }
}
