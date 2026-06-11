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
package io.aklivity.zilla.runtime.common.avro.bench;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
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

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroController;
import io.aklivity.zilla.runtime.common.avro.AvroEvent;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.AvroSink.Delivery;
import io.aklivity.zilla.runtime.common.avro.AvroSource;

/**
 * Compares the {@code common-avro} streaming pipeline, decoder through encoder, across decode-only
 * (events to a no-op sink), structured transcode (decode -> validate -> re-encode, block framing
 * normalized) and segmented transcode (the datum copied verbatim). Each transcode benchmark is paired
 * so throughput and (under {@code -prof gc}) per-op allocation can be compared directly; the
 * decode-only case is the control. Input datums are produced once by the reference Apache Avro library
 * in {@link #init()}; only the {@code common-avro} pipeline is measured.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class AvroPipelineBM
{
    private static final String FLAT = """
        {"type":"record","name":"Event","fields":[
        {"name":"id","type":"long"},
        {"name":"name","type":"string"},
        {"name":"active","type":"boolean"},
        {"name":"score","type":"double"},
        {"name":"tags","type":{"type":"array","items":"string"}}]}""";

    private static final String NESTED = """
        {"type":"record","name":"Outer","fields":[
        {"name":"meta","type":{"type":"record","name":"Meta","fields":[
        {"name":"id","type":"int"},{"name":"source","type":"string"}]}},
        {"name":"items","type":{"type":"array","items":{"type":"record","name":"Item","fields":[
        {"name":"key","type":"string"},{"name":"value","type":"long"}]}}}]}""";

    private final MutableDirectBuffer outputBuffer = new UnsafeBuffer(new byte[16 * 1024]);

    private AvroGenerator flatEncoder;
    private AvroGenerator nestedEncoder;

    private AvroPipeline flatDecode;
    private AvroPipeline flatStructured;
    private AvroPipeline flatSegmented;
    private AvroPipeline nestedDecode;
    private AvroPipeline nestedStructured;
    private AvroPipeline nestedSegmented;

    private UnsafeBuffer flatBuffer;
    private UnsafeBuffer nestedBuffer;
    private int flatLength;
    private int nestedLength;

    @Setup(Level.Trial)
    public void init()
    {
        AvroSchema flatSchema = Avro.schema(FLAT);
        AvroSchema nestedSchema = Avro.schema(NESTED);
        AvroSink noop = new NoopSink();

        flatEncoder = flatSchema.generator(outputBuffer, 0);
        nestedEncoder = nestedSchema.generator(outputBuffer, 0);

        flatDecode = flatSchema.parser().stream().into(noop);
        flatStructured = flatSchema.parser().stream()
            .transform(flatSchema.validator()).into(AvroSink.of(flatEncoder));
        flatSegmented = flatSchema.parser().stream().into(AvroSink.of(flatEncoder, Delivery.SEGMENTABLE));

        nestedDecode = nestedSchema.parser().stream().into(noop);
        nestedStructured = nestedSchema.parser().stream()
            .transform(nestedSchema.validator()).into(AvroSink.of(nestedEncoder));
        nestedSegmented = nestedSchema.parser().stream().into(AvroSink.of(nestedEncoder, Delivery.SEGMENTABLE));

        byte[] flatBytes = referenceEncode(FLAT, flatValue());
        byte[] nestedBytes = referenceEncode(NESTED, nestedValue());

        flatBuffer = new UnsafeBuffer(flatBytes);
        nestedBuffer = new UnsafeBuffer(nestedBytes);
        flatLength = flatBytes.length;
        nestedLength = nestedBytes.length;
    }

    @Benchmark
    public int flatDecode()
    {
        return decode(flatDecode, flatBuffer, flatLength);
    }

    @Benchmark
    public int flatStructured()
    {
        return transcode(flatStructured, flatEncoder, flatBuffer, flatLength);
    }

    @Benchmark
    public int flatSegmented()
    {
        return transcode(flatSegmented, flatEncoder, flatBuffer, flatLength);
    }

    @Benchmark
    public int nestedDecode()
    {
        return decode(nestedDecode, nestedBuffer, nestedLength);
    }

    @Benchmark
    public int nestedStructured()
    {
        return transcode(nestedStructured, nestedEncoder, nestedBuffer, nestedLength);
    }

    @Benchmark
    public int nestedSegmented()
    {
        return transcode(nestedSegmented, nestedEncoder, nestedBuffer, nestedLength);
    }

    private int decode(
        AvroPipeline pipeline,
        UnsafeBuffer buffer,
        int length)
    {
        pipeline.reset();
        pipeline.feed(buffer, 0, length);
        return length;
    }

    private int transcode(
        AvroPipeline pipeline,
        AvroGenerator encoder,
        UnsafeBuffer buffer,
        int length)
    {
        encoder.wrap(outputBuffer, 0);
        pipeline.reset();
        pipeline.feed(buffer, 0, length);
        return encoder.length();
    }

    private static Object flatValue()
    {
        Schema schema = new Schema.Parser().parse(FLAT);
        GenericData.Record record = new GenericData.Record(schema);
        record.put("id", 300L);
        record.put("name", "zilla");
        record.put("active", true);
        record.put("score", 0.5d);
        record.put("tags", new GenericData.Array<>(schema.getField("tags").schema(), List.of("a", "b", "c")));
        return record;
    }

    private static Object nestedValue()
    {
        Schema schema = new Schema.Parser().parse(NESTED);
        Schema metaSchema = schema.getField("meta").schema();
        Schema itemsSchema = schema.getField("items").schema();
        Schema itemSchema = itemsSchema.getElementType();

        GenericData.Record meta = new GenericData.Record(metaSchema);
        meta.put("id", 7);
        meta.put("source", "sensor");

        GenericData.Array<Object> items = new GenericData.Array<>(3, itemsSchema);
        items.add(item(itemSchema, "a", 1L));
        items.add(item(itemSchema, "b", 2L));
        items.add(item(itemSchema, "c", 3L));

        GenericData.Record record = new GenericData.Record(schema);
        record.put("meta", meta);
        record.put("items", items);
        return record;
    }

    private static GenericData.Record item(
        Schema schema,
        String key,
        long value)
    {
        GenericData.Record record = new GenericData.Record(schema);
        record.put("key", key);
        record.put("value", value);
        return record;
    }

    private static byte[] referenceEncode(
        String schemaText,
        Object value)
    {
        byte[] bytes;
        try
        {
            Schema schema = new Schema.Parser().parse(schemaText);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            GenericDatumWriter<Object> writer = new GenericDatumWriter<>(schema);
            writer.write(value, encoder);
            encoder.flush();
            bytes = out.toByteArray();
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return bytes;
    }

    public static void main(
        String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(AvroPipelineBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    private static final class NoopSink implements AvroSink
    {
        @Override
        public Status feed(
            AvroController control,
            AvroSource source,
            AvroEvent event)
        {
            return Status.PENDING;
        }
    }
}
