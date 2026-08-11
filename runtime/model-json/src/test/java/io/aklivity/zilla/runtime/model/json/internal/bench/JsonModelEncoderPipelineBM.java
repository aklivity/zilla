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

import java.time.Clock;

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
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.json.internal.JsonModelHandlerImpl;

/**
 * Exercises {@code model-json}'s encode {@link ModelPipeline}, the schema-validating transform that
 * {@code JsonModelHandlerImpl.supplyEncoder} vends per stream: catalog framing prefix on the first
 * fragment, then the common-json transform into the destination. {@code encodeSmallDocument} and
 * {@code encodeLargeDocument} drive the same schema over a short and a long value respectively, so the
 * effect of document size on throughput and allocation (under {@code -prof gc}) is directly comparable.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class JsonModelEncoderPipelineBM
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

    private static final String SMALL_DOCUMENT = "{\"id\":\"123\",\"status\":\"OK\"}";

    private static final String LARGE_DOCUMENT =
        "{\"id\":\"123\",\"status\":\"OK\",\"payload\":\"" + "x".repeat(2048) + "\"}";

    private final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[32 * 1024]);

    private ModelPipeline pipeline;

    private UnsafeBufferEx smallBuffer;
    private UnsafeBufferEx largeBuffer;
    private int smallLength;
    private int largeLength;

    @Setup(Level.Trial)
    public void init()
    {
        JsonModelHandlerImpl handler = newHandler();
        pipeline = handler.supplyEncoder(ModelTransform.NONE);

        byte[] smallBytes = SMALL_DOCUMENT.getBytes(UTF_8);
        byte[] largeBytes = LARGE_DOCUMENT.getBytes(UTF_8);
        smallBuffer = new UnsafeBufferEx(smallBytes);
        largeBuffer = new UnsafeBufferEx(largeBytes);
        smallLength = smallBytes.length;
        largeLength = largeBytes.length;
    }

    @Benchmark
    public int encodeSmallDocument()
    {
        return run(smallBuffer, smallLength);
    }

    @Benchmark
    public int encodeLargeDocument()
    {
        return run(largeBuffer, largeLength);
    }

    private int run(
        UnsafeBufferEx buffer,
        int length)
    {
        ModelPipelineResult result = pipeline.transform(0L, 0L, FLAGS_COMPLETE,
            buffer, 0, length, dst, 0, dst.capacity());
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
                    .id(9)
                    .build()
                .build()
            .build();
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
        return new JsonModelHandlerImpl(model, context);
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(JsonModelEncoderPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
