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
package io.aklivity.zilla.runtime.common.avro;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Regenerates the golden interoperability fixtures under
 * {@code src/test/resources/.../conformance/<scenario>/}, produced by the reference Apache Avro
 * implementation so {@link AvroConformanceTest} can prove this codec interoperates with them.
 * Disabled by default — remove {@link Disabled} and run to rewrite the fixtures. Each scenario
 * directory holds an Object Container File ({@code record.avro}) together with its exploded,
 * human-inspectable parts: {@code avro.schema} and {@code avro.codec} (the container metadata) and
 * {@code datum.bin} (the raw single-object Avro binary, byte-identical to the null-codec container's
 * data block). {@code value.json} renders the value for review. {@link AvroConformanceTest} reads
 * only {@code avro.schema}, {@code avro.codec}, and {@code datum.bin}.
 */
@Disabled("remove @Disabled to regenerate golden interoperability fixtures under src/test/resources")
public class AvroInteroperabilityTest
{
    private static final Path ROOT =
        Path.of("src/test/resources/io/aklivity/zilla/runtime/common/avro/conformance");

    @Test
    public void generate() throws IOException
    {
        writePrimitives();
        writeArray();
        writeMap();
        writeEnum();
        writeFixed();
        writeUnion();
        writeRecursive();
    }

    private void writePrimitives() throws IOException
    {
        Schema schema = new Schema.Parser().parse("""
            {"type":"record","name":"Primitives","fields":[
            {"name":"intField","type":"int"},
            {"name":"longField","type":"long"},
            {"name":"stringField","type":"string"},
            {"name":"boolField","type":"boolean"},
            {"name":"floatField","type":"float"},
            {"name":"doubleField","type":"double"},
            {"name":"bytesField","type":"bytes"},
            {"name":"nullField","type":"null"}]}""");
        GenericData.Record record = new GenericData.Record(schema);
        record.put("intField", 42);
        record.put("longField", 9_999_999_999L);
        record.put("stringField", "hello");
        record.put("boolField", true);
        record.put("floatField", 1.5f);
        record.put("doubleField", 2.25d);
        record.put("bytesField", java.nio.ByteBuffer.wrap(new byte[] { 0x01, (byte) 0xff, 0x10 }));
        record.put("nullField", null);
        write("primitives", schema, record);
    }

    private void writeArray() throws IOException
    {
        Schema schema = new Schema.Parser().parse("{\"type\":\"array\",\"items\":\"double\"}");
        GenericData.Array<Object> array = new GenericData.Array<>(schema, List.of(1.0d, 2.5d, -3.75d));
        write("array", schema, array);
    }

    private void writeMap() throws IOException
    {
        Schema schema = new Schema.Parser().parse("{\"type\":\"map\",\"values\":\"long\"}");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1L);
        map.put("b", 1000L);
        write("map", schema, map);
    }

    private void writeEnum() throws IOException
    {
        Schema schema = new Schema.Parser().parse(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\",\"CLUBS\"]}");
        write("enum", schema, new GenericData.EnumSymbol(schema, "HEARTS"));
    }

    private void writeFixed() throws IOException
    {
        Schema schema = new Schema.Parser().parse("{\"type\":\"fixed\",\"name\":\"MD5\",\"size\":4}");
        write("fixed", schema, new GenericData.Fixed(schema, new byte[] { (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef }));
    }

    private void writeUnion() throws IOException
    {
        Schema schema = new Schema.Parser().parse("[\"null\",\"string\",\"long\"]");
        write("union", schema, "present");
    }

    private void writeRecursive() throws IOException
    {
        Schema schema = new Schema.Parser().parse("""
            {"type":"record","name":"Node","fields":[
            {"name":"label","type":"string"},
            {"name":"children","type":{"type":"array","items":"Node"}}]}""");
        write("recursive", schema, node(schema, "root", node(schema, "a"), node(schema, "b", node(schema, "b1"))));
    }

    private GenericData.Record node(
        Schema schema,
        String label,
        GenericData.Record... children)
    {
        GenericData.Record record = new GenericData.Record(schema);
        record.put("label", label);
        GenericData.Array<Object> array = new GenericData.Array<>(children.length, schema.getField("children").schema());
        for (GenericData.Record child : children)
        {
            array.add(child);
        }
        record.put("children", array);
        return record;
    }

    private void write(
        String name,
        Schema schema,
        Object value) throws IOException
    {
        Path dir = ROOT.resolve(name);
        Files.createDirectories(dir);

        Files.writeString(dir.resolve("avro.schema"), schema.toString(true) + "\n");
        Files.writeString(dir.resolve("avro.codec"), "null\n");
        Files.write(dir.resolve("datum.bin"), binaryEncode(schema, value));
        Files.writeString(dir.resolve("value.json"), jsonEncode(schema, value) + "\n");

        try (OutputStream out = Files.newOutputStream(dir.resolve("record.avro"));
             DataFileWriter<Object> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema)))
        {
            writer.create(schema, out);
            writer.append(value);
        }
    }

    private byte[] binaryEncode(
        Schema schema,
        Object value) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<>(schema).write(value, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private String jsonEncode(
        Schema schema,
        Object value) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonEncoder encoder = EncoderFactory.get().jsonEncoder(schema, out, true);
        new GenericDatumWriter<>(schema).write(value, encoder);
        encoder.flush();
        return out.toString(UTF_8);
    }
}
