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
package io.aklivity.zilla.runtime.model.protobuf.internal.bench;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

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
import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.protobuf.internal.ProtobufModelHandlerImpl;

/**
 * Drives {@code ProtobufModelEncoderPipeline} (via {@code ProtobufModelHandlerImpl.supplyEncoder}) over
 * a fully-buffered JSON message, encoding it to protobuf wire bytes. Run with {@code -prof gc} to
 * measure allocation per op for the JSON-to-wire hot path.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class ProtobufModelEncoderPipelineBM
{
    private static final int FLAGS_COMPLETE = 0x03;

    private static final String SCHEMA = """
                                            syntax = "proto3";
                                            package io.aklivity.examples.clients.proto;
                                            message SimpleMessage {
                                                string content = 1;
                                                optional string date_time = 2;
                                            }
                                            """;

    private static final byte[] JSON = "{\"content\":\"OK\",\"date_time\":\"01012024\"}".getBytes(UTF_8);

    private final MutableDirectBufferEx outputBuffer = new UnsafeBufferEx(new byte[1024]);
    private final UnsafeBufferEx inputBuffer = new UnsafeBufferEx(JSON);

    private ModelPipeline pipeline;

    @Setup(Level.Trial)
    public void init()
    {
        pipeline = newHandler().supplyEncoder(ModelTransform.NONE);
    }

    @Benchmark
    public int encodeToWire()
    {
        pipeline.reset();
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            inputBuffer, 0, JSON.length, outputBuffer, 0, outputBuffer.capacity());
        return result.produced();
    }

    private static ProtobufModelHandlerImpl newHandler()
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .view("json")
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject("test-value")
                    .record("SimpleMessage")
                    .build()
                .build()
            .build();
        EngineContext context = mock(EngineContext.class);
        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        return new ProtobufModelHandlerImpl(model, context, List.of());
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(ProtobufModelEncoderPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
