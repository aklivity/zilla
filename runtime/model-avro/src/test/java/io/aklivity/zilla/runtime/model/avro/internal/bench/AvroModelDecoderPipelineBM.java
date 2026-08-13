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
 * Drives {@code AvroModelDecoderPipeline} (vended by {@code AvroModelHandlerImpl.supplyDecoder}) the same
 * way {@code AvroModelDecoderPipelineTest} does — a {@code type: test} catalog resolving a fixed schema id,
 * decoding a single complete Avro-binary fragment per invocation. {@code decodeToJson} re-encodes into the
 * JSON view; {@code decodeIdentity} re-encodes into canonical Avro binary (no view configured). Run with
 * {@code -prof gc} to see per-op allocation (@code gc.alloc.rate.norm} in B/op).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class AvroModelDecoderPipelineBM
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

    // id="id0" (len 3) then status="positive" (len 8)
    private static final byte[] AVRO = {0x06, 0x69, 0x64, 0x30, 0x10, 0x70, 0x6f, 0x73, 0x69, 0x74, 0x69, 0x76, 0x65};

    private ModelPipeline toJson;
    private ModelPipeline identity;
    private UnsafeBufferEx src;
    private MutableDirectBufferEx dst;

    @Setup(Level.Trial)
    public void init()
    {
        toJson = newHandler("json").supplyDecoder(ModelTransform.NONE);
        identity = newHandler(null).supplyDecoder(ModelTransform.NONE);
        src = new UnsafeBufferEx(AVRO);
        dst = new UnsafeBufferEx(new byte[256]);
    }

    @Benchmark
    public int decodeToJson()
    {
        return decode(toJson);
    }

    @Benchmark
    public int decodeIdentity()
    {
        return decode(identity);
    }

    private int decode(
        ModelPipeline pipeline)
    {
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            src, 0, AVRO.length, dst, 0, dst.capacity());
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
        AvroModelConfig model = AvroModelConfig.builder()
            .view(view)
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
            .include(AvroModelDecoderPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
