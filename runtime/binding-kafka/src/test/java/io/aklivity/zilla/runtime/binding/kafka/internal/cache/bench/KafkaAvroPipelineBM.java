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
import io.aklivity.zilla.config.model.avro.AvroModelConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheModel;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaPipeline;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaSink;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicTransformsType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;
import io.aklivity.zilla.runtime.model.avro.internal.AvroModelConfiguration;
import io.aklivity.zilla.runtime.model.avro.internal.AvroModelHandlerImpl;

/**
 * Same measurement as {@link KafkaPipelineBM}, but the key and value lanes are driven by the real
 * {@code model-avro} {@link AvroModelHandlerImpl} instead of a synthetic field-surfacing double, so the
 * B/op figures reflect actual Avro binary decode plus {@link KafkaPipeline}'s own lane-switch cost rather
 * than isolating the latter alone.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class KafkaAvroPipelineBM
{
    private static final String KEY_SCHEMA = """
        {
            "fields":
            [
                { "name": "id", "type": "string" },
                { "name": "tenant", "type": "string" }
            ],
            "name": "Key",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final String VALUE_SCHEMA = """
        {
            "fields":
            [
                { "name": "region", "type": "string" },
                { "name": "status", "type": "string" },
                { "name": "payload", "type": "string" }
            ],
            "name": "Value",
            "namespace": "io.aklivity.example",
            "type": "record"
        }""";

    private static final byte[] KEY_AVRO = avroRecord("key-1234", "acme");
    private static final byte[] VALUE_AVRO = avroRecord("us-east-1", "ok", "large-event-payload-body");

    private final MutableDirectBufferEx scratch = new UnsafeBufferEx(new byte[512]);
    private final UnsafeBufferEx keyBuffer = new UnsafeBufferEx(KEY_AVRO);
    private final UnsafeBufferEx valueBuffer = new UnsafeBufferEx(VALUE_AVRO);
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

    private final AvroModelConfiguration config = new AvroModelConfiguration(new Configuration());

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

    private ModelHandler newKeyHandler()
    {
        return newHandler(KEY_SCHEMA, 1, "test-key");
    }

    private ModelHandler newValueHandler()
    {
        return newHandler(VALUE_SCHEMA, 2, "test-value");
    }

    // mirrors AvroModelDecoderPipelineTest.newHandler(): an inline test catalog serving the given schema
    // by id, with no network round-trip, so the handler decodes and validates for real. The "json" view
    // is what lets extraction read fields by JSON pointer, matching AvroModelDecoderPipelineTest's
    // shouldExtractField / shouldExtractScalarFields setup rather than its no-view identity-check setup.
    private ModelHandler newHandler(
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
        AvroModelConfig model = AvroModelConfig.builder()
            .view("json")
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
        return new AvroModelHandlerImpl(config, model, context);
    }

    // Avro strings are length-prefixed with a zigzag varint; every fixture value here is under 64 bytes,
    // so the zigzag-encoded length always fits the varint's single-byte range.
    private static byte[] avroString(
        String value)
    {
        byte[] utf8 = value.getBytes(UTF_8);
        byte[] encoded = new byte[1 + utf8.length];
        encoded[0] = (byte) (utf8.length << 1);
        System.arraycopy(utf8, 0, encoded, 1, utf8.length);
        return encoded;
    }

    private static byte[] avroRecord(
        String... values)
    {
        byte[][] fields = new byte[values.length][];
        int length = 0;
        for (int i = 0; i < values.length; i++)
        {
            fields[i] = avroString(values[i]);
            length += fields[i].length;
        }

        byte[] record = new byte[length];
        int offset = 0;
        for (byte[] field : fields)
        {
            System.arraycopy(field, 0, record, offset, field.length);
            offset += field.length;
        }
        return record;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(KafkaAvroPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
