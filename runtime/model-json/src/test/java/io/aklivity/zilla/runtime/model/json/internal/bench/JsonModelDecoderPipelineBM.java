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
package io.aklivity.zilla.runtime.model.json.internal.bench;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.json.internal.JsonModelHandlerImpl;

/**
 * Exercises {@code model-json}'s decode {@link ModelPipeline}, the schema-validating transform that
 * {@code JsonModelHandlerImpl.supplyDecoder} vends per stream. {@code decodeValidDocument} drives the
 * verbatim/SEGMENTED fast path ({@link ModelTransform#NONE}, no field-extraction bridge); {@code
 * decodeWithFieldExtraction} drives the same schema and document through a wired {@link ModelTransform}
 * so the extra {@code ModelFieldBridge} field-visit cost is directly comparable under {@code -prof gc}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonModelDecoderPipelineBM
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

    private static final String VALID_DOCUMENT = "{\"id\":\"123\",\"status\":\"OK\"}";

    private final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[16 * 1024]);

    private ModelPipeline plainPipeline;
    private ModelPipeline extractingPipeline;

    private UnsafeBufferEx validBuffer;
    private int validLength;

    @Setup(Level.Trial)
    public void init()
    {
        JsonModelHandlerImpl handler = newHandler();
        plainPipeline = handler.supplyDecoder(ModelTransform.NONE);
        extractingPipeline = handler.supplyDecoder(fieldCounter());

        byte[] validBytes = VALID_DOCUMENT.getBytes(UTF_8);
        validBuffer = new UnsafeBufferEx(validBytes);
        validLength = validBytes.length;
    }

    @Benchmark
    public int decodeValidDocument()
    {
        return run(plainPipeline);
    }

    @Benchmark
    public int decodeWithFieldExtraction()
    {
        return run(extractingPipeline);
    }

    private int run(
        ModelPipeline pipeline)
    {
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            validBuffer, 0, validLength, dst, 0, dst.capacity());
        return result.produced();
    }

    private JsonModelHandlerImpl newHandler()
    {
        EngineContext context = mock(EngineContext.class);
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
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
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new JsonModelHandlerImpl(model, context);
    }

    // captures field-visit count without allocating per call, isolating the bridge's traversal cost
    // from any downstream storage the caller might use
    private static ModelTransform fieldCounter()
    {
        return new ModelTransform()
        {
            private long fields;

            @Override
            public ModelStatus transform(
                ModelController control,
                ModelSource source,
                ModelEvent event,
                ModelSink sink)
            {
                if (event == ModelEvent.FIELD)
                {
                    fields++;
                }
                return sink.transform(control, source, event);
            }

            @Override
            public boolean identity()
            {
                return true;
            }
        };
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonModelDecoderPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
