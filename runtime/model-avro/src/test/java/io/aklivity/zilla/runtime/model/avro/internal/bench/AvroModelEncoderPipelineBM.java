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
package io.aklivity.zilla.runtime.model.avro.internal.bench;

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
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.config.model.avro.AvroModelConfigBuilder;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.avro.internal.AvroModelConfiguration;
import io.aklivity.zilla.runtime.model.avro.internal.AvroModelHandlerImpl;

/**
 * Drives {@code AvroModelEncoderPipeline} (vended by {@code AvroModelHandlerImpl.supplyEncoder}) the same
 * way {@code AvroModelEncoderPipelineTest} does — a {@code type: test} catalog resolving a fixed schema id,
 * encoding a single complete fragment per invocation. {@code encodeFromJson} converts a JSON-view payload
 * into Avro binary; {@code encodeIdentity} validates and reproduces an Avro-binary payload (no view
 * configured). Run with {@code -prof gc} to see per-op allocation ({@code gc.alloc.rate.norm} in B/op).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class AvroModelEncoderPipelineBM
{
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String SCHEMA = """
        {
            "fields":
            [
                { "name": "id", "type": "string" },
                { "name": "status", "type": "string" }
            ],
            "name": "Event",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final byte[] JSON = "{\"id\":\"id0\",\"status\":\"positive\"}".getBytes(UTF_8);
    // id="id0" (len 3) then status="positive" (len 8); the TestCatalog adds no framing prefix
    private static final byte[] AVRO = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};

    private ModelPipeline fromJson;
    private ModelPipeline identity;
    private UnsafeBufferEx jsonSrc;
    private UnsafeBufferEx avroSrc;
    private MutableDirectBufferEx dst;

    @Setup(Level.Trial)
    public void init()
    {
        fromJson = newHandler("json").supplyEncoder(ModelTransform.NONE);
        identity = newHandler(null).supplyEncoder(ModelTransform.NONE);
        jsonSrc = new UnsafeBufferEx(JSON);
        avroSrc = new UnsafeBufferEx(AVRO);
        dst = new UnsafeBufferEx(new byte[256]);
    }

    @Benchmark
    public int encodeFromJson()
    {
        ModelPipelineResult result = fromJson.transform(0L, 0L, FLAGS_COMPLETE,
            jsonSrc, 0, JSON.length, dst, 0, dst.capacity());
        return result.produced();
    }

    @Benchmark
    public int encodeIdentity()
    {
        ModelPipelineResult result = identity.transform(0L, 0L, FLAGS_COMPLETE,
            avroSrc, 0, AVRO.length, dst, 0, dst.capacity());
        return result.produced();
    }

    private static AvroModelHandlerImpl newHandler(
        String view)
    {
        AvroModelConfiguration config = new AvroModelConfiguration(new Configuration());
        EngineContext context = mock(EngineContext.class);
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(SCHEMA)
                .build()
            .build();
        AvroModelConfigBuilder<AvroModelConfig> builder = AvroModelConfig.builder();
        if (view != null)
        {
            builder.view(view);
        }
        AvroModelConfig model = builder
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
        return new AvroModelHandlerImpl(config, model, context);
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(AvroModelEncoderPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
