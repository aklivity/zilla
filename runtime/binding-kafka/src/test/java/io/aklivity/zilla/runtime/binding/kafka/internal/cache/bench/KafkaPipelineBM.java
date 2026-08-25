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

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheModel;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaPipeline;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaSink;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicTransformsType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelFieldBridge;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

/**
 * Exercises {@link KafkaPipeline#transformKey} and {@link KafkaPipeline#transformValue}, the hot-path
 * methods a Kafka cache entry drives per fetched record. Each direction is measured twice: once with no
 * {@code KafkaTransform} stage composed in (a structured model surfaces fields, but nothing switches lane
 * or appends), and once with the stage an {@code extractKey} / {@code extractHeaders} topic config
 * produces actually promoting a field into the key or a header. The model itself is the same field-only
 * test double {@code KafkaPipelineTest} uses, so the difference between the paired benchmarks isolates
 * the pipeline's own lane-switch and terminal-dispatch cost rather than any particular model's parse cost.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class KafkaPipelineBM
{
    private static final String KEY_JSON = "{\"id\":\"key-1234\",\"tenant\":\"acme\"}";
    private static final String VALUE_JSON =
        "{\"region\":\"us-east-1\",\"status\":\"ok\",\"payload\":\"large-event-payload-body\"}";

    private final MutableDirectBufferEx scratch = new UnsafeBufferEx(new byte[512]);
    private final UnsafeBufferEx keyBuffer = new UnsafeBufferEx(KEY_JSON.getBytes(UTF_8));
    private final UnsafeBufferEx valueBuffer = new UnsafeBufferEx(VALUE_JSON.getBytes(UTF_8));
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
        passthroughKeyPipeline = KafkaPipeline.decoder(handler("$.id", "key-1234"), null, null, scratch);
        passthroughValuePipeline = KafkaPipeline.decoder(null,
            handler("$.region", "us-east-1", "$.status", "ok"), null, scratch);

        KafkaTopicTransformsType extractKey = new KafkaTopicTransformsType("$.id", List.of());
        extractKeyPipeline = KafkaPipeline.decoder(handler("$.id", "key-1234"), null, extractKey, scratch);

        KafkaTopicTransformsType extractHeaders = new KafkaTopicTransformsType(null,
            List.of(new KafkaTopicHeaderType("region", "$.region"), new KafkaTopicHeaderType("status", "$.status")));
        extractHeadersPipeline = KafkaPipeline.decoder(null,
            handler("$.region", "us-east-1", "$.status", "ok"), extractHeaders, scratch);
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

    // a model that copies the value through and surfaces the given path/value pairs as its fields,
    // standing in for a real model decoding a structured key or value
    private static ModelHandler handler(
        String... pathsAndValues)
    {
        return new ModelHandler()
        {
            @Override
            public ModelPipeline supplyCacheable(
                ModelEnvelope envelope,
                ModelTransform transform)
            {
                return supplyDecoder(envelope, transform);
            }

            @Override
            public ModelPipeline supplyDecoder(
                ModelEnvelope envelope,
                ModelTransform transform)
            {
                return new FieldsPipeline(transform, pathsAndValues);
            }

            @Override
            public ModelPipeline supplyEncoder(
                ModelEnvelope envelope,
                ModelTransform transform)
            {
                return supplyDecoder(envelope, transform);
            }
        };
    }

    private static final class FieldsPipeline implements ModelPipeline
    {
        private final ModelFieldBridge bridge;
        private final String[] pathsAndValues;
        private final MutableDirectBufferEx field = new UnsafeBufferEx(new byte[64]);
        private final ModelPipelineResult result = new ModelPipelineResult();

        private FieldsPipeline(
            ModelTransform transform,
            String[] pathsAndValues)
        {
            this.bridge = new ModelFieldBridge(transform);
            this.pathsAndValues = pathsAndValues;
        }

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int srcLength = srcLimit - srcIndex;

            bridge.start(authorization);
            for (int index = 0; index < pathsAndValues.length; index += 2)
            {
                final byte[] value = pathsAndValues[index + 1].getBytes(UTF_8);
                field.putBytes(0, value);
                bridge.field(pathsAndValues[index], field, 0, value.length);
            }
            bridge.end();

            dst.putBytes(dstIndex, src, srcIndex, srcLength);

            return result.set(ModelStatus.COMPLETE, srcLength, srcLength);
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public void reset()
        {
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(KafkaPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
