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
import io.aklivity.zilla.runtime.model.json.config.JsonModelConfig;

public class JsonValidatorModelPipelineTest
{
    private static final String OBJECT_SCHEMA = "{" +
        "\"type\": \"object\"," +
        "\"properties\": {" +
            "\"id\": { \"type\": \"string\" }," +
            "\"status\": { \"type\": \"string\" }" +
        "}," +
        "\"required\": [ \"id\", \"status\" ]" +
        "}";

    // the validate-only pipeline never writes to the caller's destination
    private static final MutableDirectBuffer NO_DST = new UnsafeBuffer(new byte[0]);

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldValidateWholeValue()
    {
        JsonValidatorModelHandler handler = newHandler();
        ModelPipeline pipeline = handler.supplyPipeline(ModelVisitor.NONE);

        byte[] in = "{\"id\":\"123\",\"status\":\"OK\"}".getBytes(UTF_8);
        ModelPipelineResult result = validate(pipeline, in);

        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(in.length, result.consumed());
        assertEquals(0, result.produced());
    }

    @Test
    public void shouldRejectInvalidValue()
    {
        JsonValidatorModelHandler handler = newHandler();
        ModelPipeline pipeline = handler.supplyPipeline(ModelVisitor.NONE);

        // missing required "status"
        byte[] in = "{\"id\":\"123\"}".getBytes(UTF_8);
        ModelPipelineResult result = validate(pipeline, in);

        assertEquals(ModelStatus.REJECTED, result.status());
    }

    @Test
    public void shouldIsolateInterleavedStreams()
    {
        // two per-stream pipelines vended by one per-worker handler share the scratch buffer but must not
        // share parse state; interleaving two distinct values exercises that isolation
        JsonValidatorModelHandler handler = newHandler();
        ModelPipeline a = handler.supplyPipeline(ModelVisitor.NONE);
        ModelPipeline b = handler.supplyPipeline(ModelVisitor.NONE);

        byte[] valueA = "{\"id\":\"aaa\",\"status\":\"A\"}".getBytes(UTF_8);
        byte[] valueB = "{\"id\":\"bbb\",\"status\":\"B\"}".getBytes(UTF_8);

        ModelPipelineResult resultA = validate(a, valueA);
        ModelPipelineResult resultB = validate(b, valueB);

        assertEquals(ModelStatus.COMPLETE, resultA.status());
        assertEquals(valueA.length, resultA.consumed());
        assertEquals(ModelStatus.COMPLETE, resultB.status());
        assertEquals(valueB.length, resultB.consumed());
    }

    private static ModelPipelineResult validate(
        ModelPipeline pipeline,
        byte[] in)
    {
        return pipeline.transform(0L, 0L, ModelPipeline.FLAGS_COMPLETE,
            new UnsafeBuffer(in), 0, in.length, NO_DST, 0, 0);
    }

    private JsonValidatorModelHandler newHandler()
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
        return new JsonValidatorModelHandler(model, context);
    }
}
