/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.cache.bench;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

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
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheModel;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaPipeline;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaSink;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicTransformsType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.protobuf.internal.ProtobufModelHandlerImpl;

/**
 * Same measurement as {@link KafkaPipelineBM}, but the key and value lanes are driven by the real
 * {@code model-protobuf} {@link ProtobufModelHandlerImpl} instead of a synthetic field-surfacing double,
 * so the B/op figures reflect actual protobuf wire-format decode plus {@link KafkaPipeline}'s own
 * lane-switch cost rather than isolating the latter alone.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class KafkaProtobufPipelineBM
{
    private static final String KEY_SCHEMA = """
        syntax = "proto3";
        package io.aklivity.example;
        message SimpleKey {
            string id = 1;
            string tenant = 2;
        }
        """;

    private static final String VALUE_SCHEMA = """
        syntax = "proto3";
        package io.aklivity.example;
        message SimpleValue {
            string region = 1;
            string status = 2;
            string payload = 3;
        }
        """;

    private static final byte[] KEY_WIRE = protobufRecord("key-1234", "acme");
    private static final byte[] VALUE_WIRE = protobufRecord("us-east-1", "ok", "large-event-payload-body");

    private final MutableDirectBufferEx scratch = new UnsafeBufferEx(new byte[512]);
    private final UnsafeBufferEx keyBuffer = new UnsafeBufferEx(KEY_WIRE);
    private final UnsafeBufferEx valueBuffer = new UnsafeBufferEx(VALUE_WIRE);
    private final int keyLength = keyBuffer.capacity();
    private final int valueLength = valueBuffer.capacity();

    // accumulates every callback's contribution so neither the terminal sink nor the produced-byte
    // callback can be eliminated as dead code; folded into each benchmark's returned value
    private long checksum;

    private final KafkaCacheModel.Output next = (buffer, index, length) -> checksum += length;
    private final KafkaSink sink = (control, source, event) ->
    {
        checksum += event.ordinal();
        return ModelStatus.OK;
    };

    private KafkaPipeline passthroughKeyPipeline;
    private KafkaPipeline passthroughValuePipeline;
    private KafkaPipeline extractKeyPipeline;
    private KafkaPipeline extractHeadersPipeline;

    @Setup(Level.Trial)
    public void init()
    {
        passthroughKeyPipeline = KafkaPipeline.decoder(newKeyHandler(), null, null, scratch);
        passthroughValuePipeline = KafkaPipeline.decoder(null, newValueHandler(), null, scratch);

        KafkaTopicTransformsType extractKey = new KafkaTopicTransformsType("$.id", List.of());
        extractKeyPipeline = KafkaPipeline.decoder(newKeyHandler(), null, extractKey, scratch);

        KafkaTopicTransformsType extractHeaders = new KafkaTopicTransformsType(null,
            List.of(new KafkaTopicHeaderType("region", "$.region"), new KafkaTopicHeaderType("status", "$.status")));
        extractHeadersPipeline = KafkaPipeline.decoder(null, newValueHandler(), extractHeaders, scratch);
    }

    @Benchmark
    public long transformKeyPassthrough()
    {
        return run(passthroughKeyPipeline, true, keyBuffer, keyLength);
    }

    @Benchmark
    public long transformValuePassthrough()
    {
        return run(passthroughValuePipeline, false, valueBuffer, valueLength);
    }

    @Benchmark
    public long transformKeyWithExtractKey()
    {
        return run(extractKeyPipeline, true, keyBuffer, keyLength);
    }

    @Benchmark
    public long transformValueWithExtractHeaders()
    {
        return run(extractHeadersPipeline, false, valueBuffer, valueLength);
    }

    private long run(
        KafkaPipeline pipeline,
        boolean key,
        DirectBufferEx data,
        int length)
    {
        pipeline.reset();
        final int transformed = key
            ? pipeline.transformKey(0L, 0L, 0L, data, 0, length, next, sink)
            : pipeline.transformValue(0L, 0L, 0L, data, 0, length, next, sink);
        return checksum + transformed;
    }

    private static ModelHandler newKeyHandler()
    {
        return newHandler(KEY_SCHEMA, 1, "test-key");
    }

    private static ModelHandler newValueHandler()
    {
        return newHandler(VALUE_SCHEMA, 2, "test-value");
    }

    // mirrors ProtobufModelDecoderPipelineTest.newHandler(): an inline test catalog serving the given
    // schema by id, with no network round-trip, so the handler decodes and validates for real
    private static ModelHandler newHandler(
        String schema,
        int schemaId,
        String subject)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(schemaId)
                .schema(schema)
                .build()
            .build();
        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .strategy("topic")
                    .version("latest")
                    .subject(subject)
                    .build()
                .build()
            .build();
        EngineContext context = new KafkaModelWorker(new TestCatalogHandler(catalog.options));
        return new ProtobufModelHandlerImpl(model, context, List.of());
    }

    // protobuf LEN-type fields (wire type 2) tag as (fieldNumber << 3) | 2; every fixture value here is
    // under 128 bytes so the varint length always fits a single byte. The leading zero byte selects
    // message index 0, matching the single-message .proto schemas above.
    private static byte[] protobufRecord(
        String... values)
    {
        byte[][] fields = new byte[values.length][];
        int length = 1;
        for (int i = 0; i < values.length; i++)
        {
            fields[i] = values[i].getBytes(UTF_8);
            length += 2 + fields[i].length;
        }

        byte[] record = new byte[length];
        int offset = 0;
        record[offset++] = 0x00;
        for (int i = 0; i < fields.length; i++)
        {
            record[offset++] = (byte) (((i + 1) << 3) | 2);
            record[offset++] = (byte) fields[i].length;
            System.arraycopy(fields[i], 0, record, offset, fields[i].length);
            offset += fields[i].length;
        }
        return record;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(KafkaProtobufPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
