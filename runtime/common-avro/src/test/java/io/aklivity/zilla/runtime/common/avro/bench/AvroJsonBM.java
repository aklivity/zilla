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
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.avro.json.AvroJson;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;

/**
 * Measures the {@link AvroJson} bridge as two {@link AvroPipeline} variants — <b>Avro → JSON</b> (an
 * {@code Avro} parse pipeline terminating in an {@link AvroJson#generator generator}-backed sink) and
 * <b>JSON → Avro</b> (an {@link AvroJson#stream JSON-driven} pipeline terminating in an Avro generator) —
 * over a flat and a nested datum. The Avro input is produced once by the reference Apache Avro library and
 * the JSON input once by the bridge itself; only the bridge pipelines are measured. Run with {@code -prof
 * gc} to confirm zero per-message allocation on the structural and numeric hot path
 * ({@code gc.alloc.rate.norm} in B/op).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(3)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(SECONDS)
public class AvroJsonBM
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

    private AvroGenerator flatJsonGenerator;
    private AvroGenerator nestedJsonGenerator;
    private AvroGenerator flatAvroGenerator;
    private AvroGenerator nestedAvroGenerator;

    private AvroPipeline flatToJson;
    private AvroPipeline nestedToJson;
    private AvroPipeline flatToAvro;
    private AvroPipeline nestedToAvro;

    private UnsafeBuffer flatAvro;
    private UnsafeBuffer nestedAvro;
    private UnsafeBuffer flatJson;
    private UnsafeBuffer nestedJson;
    private int flatAvroLength;
    private int nestedAvroLength;
    private int flatJsonLength;
    private int nestedJsonLength;

    @Setup(Level.Trial)
    public void init()
    {
        AvroSchema flatSchema = Avro.schema(FLAT);
        AvroSchema nestedSchema = Avro.schema(NESTED);

        flatJsonGenerator = AvroJson.generator(flatSchema, JsonEx.createGenerator());
        nestedJsonGenerator = AvroJson.generator(nestedSchema, JsonEx.createGenerator());
        flatAvroGenerator = Avro.generator(flatSchema, outputBuffer, 0);
        nestedAvroGenerator = Avro.generator(nestedSchema, outputBuffer, 0);

        flatToJson = Avro.stream(Avro.parser(flatSchema)).into(AvroSink.of(flatJsonGenerator));
        nestedToJson = Avro.stream(Avro.parser(nestedSchema)).into(AvroSink.of(nestedJsonGenerator));
        flatToAvro = AvroJson.stream(flatSchema, JsonEx.createParser()).into(AvroSink.of(flatAvroGenerator));
        nestedToAvro = AvroJson.stream(nestedSchema, JsonEx.createParser()).into(AvroSink.of(nestedAvroGenerator));

        byte[] flatBytes = referenceEncode(FLAT, flatValue());
        byte[] nestedBytes = referenceEncode(NESTED, nestedValue());
        flatAvro = new UnsafeBuffer(flatBytes);
        nestedAvro = new UnsafeBuffer(nestedBytes);
        flatAvroLength = flatBytes.length;
        nestedAvroLength = nestedBytes.length;

        byte[] flatJsonBytes = toJson(flatSchema, flatBytes);
        byte[] nestedJsonBytes = toJson(nestedSchema, nestedBytes);
        flatJson = new UnsafeBuffer(flatJsonBytes);
        nestedJson = new UnsafeBuffer(nestedJsonBytes);
        flatJsonLength = flatJsonBytes.length;
        nestedJsonLength = nestedJsonBytes.length;
    }

    @Benchmark
    public int flatAvroToJson()
    {
        return transcode(flatToJson, flatJsonGenerator, flatAvro, flatAvroLength);
    }

    @Benchmark
    public int nestedAvroToJson()
    {
        return transcode(nestedToJson, nestedJsonGenerator, nestedAvro, nestedAvroLength);
    }

    @Benchmark
    public int flatJsonToAvro()
    {
        return transcode(flatToAvro, flatAvroGenerator, flatJson, flatJsonLength);
    }

    @Benchmark
    public int nestedJsonToAvro()
    {
        return transcode(nestedToAvro, nestedAvroGenerator, nestedJson, nestedJsonLength);
    }

    private int transcode(
        AvroPipeline pipeline,
        AvroGenerator generator,
        UnsafeBuffer buffer,
        int length)
    {
        generator.wrap(outputBuffer, 0, outputBuffer.capacity());
        pipeline.reset();
        pipeline.feed(buffer, 0, length);
        return generator.length();
    }

    private byte[] toJson(
        AvroSchema schema,
        byte[] avro)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[16 * 1024]);
        JsonGeneratorEx json = JsonEx.createGenerator();
        AvroGenerator generator = AvroJson.generator(schema, json).wrap(out, 0, out.capacity());
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(avro), 0, avro.length);
        if (status != Status.COMPLETED)
        {
            throw new IllegalStateException("avro -> json did not complete: " + status);
        }
        json.flush();
        byte[] bytes = new byte[json.length()];
        out.getBytes(0, bytes);
        return bytes;
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
            .include(AvroJsonBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
