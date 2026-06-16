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
package io.aklivity.zilla.runtime.common.avro.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.avro.AvroSink;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public class AvroJsonTest
{
    @Test
    public void shouldEncodeAndRoundTripPrimitives()
    {
        assertJson("\"int\"", new byte[] { (byte) 0x80, 0x01 }, "64");
        assertJson("\"long\"", new byte[] { 0x0e }, "7");
        assertJson("\"boolean\"", new byte[] { 0x01 }, "true");
        assertJson("\"boolean\"", new byte[] { 0x00 }, "false");
        assertJson("\"string\"", new byte[] { 0x06, 0x66, 0x6f, 0x6f }, "\"foo\"");
        assertJson("\"null\"", new byte[] {}, "null");
    }

    @Test
    public void shouldEncodeAndRoundTripFloatingPoint()
    {
        assertRoundTrip("\"float\"", new byte[] { 0x00, 0x00, (byte) 0xc0, 0x3f });
        assertRoundTrip("\"double\"", new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x40 });
    }

    @Test
    public void shouldEncodeAndRoundTripBytesAsBase64()
    {
        assertJson("\"bytes\"", new byte[] { 0x04, (byte) 0xff, 0x00 }, "\"/wA=\"");
    }

    @Test
    public void shouldEncodeAndRoundTripFixedAsBase64()
    {
        assertJson(
            "{\"type\":\"fixed\",\"name\":\"Hash\",\"size\":4}",
            new byte[] { 0x01, 0x02, 0x03, 0x04 },
            "\"AQIDBA==\"");
    }

    @Test
    public void shouldEncodeAndRoundTripEnumAsString()
    {
        assertJson(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\",\"CLUBS\"]}",
            new byte[] { 0x04 },
            "\"CLUBS\"");
    }

    @Test
    public void shouldEncodeAndRoundTripRecord()
    {
        assertJson("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""",
            new byte[] { 0x02, 0x04, 0x68, 0x69 },
            "{\"id\":1,\"name\":\"hi\"}");
    }

    @Test
    public void shouldEncodeAndRoundTripNestedRecord()
    {
        assertJson("""
            {"type":"record","name":"Outer","fields":[
            {"name":"inner","type":{"type":"record","name":"Inner","fields":[
            {"name":"v","type":"long"}]}},
            {"name":"tag","type":"string"}]}""",
            new byte[] { 0x0e, 0x02, 0x7a },
            "{\"inner\":{\"v\":7},\"tag\":\"z\"}");
    }

    @Test
    public void shouldEncodeAndRoundTripUnion()
    {
        assertJson("[\"null\",\"string\"]", new byte[] { 0x00 }, "null");
        assertJson("[\"null\",\"string\"]", new byte[] { 0x02, 0x02, 0x78 }, "{\"string\":\"x\"}");
    }

    @Test
    public void shouldEncodeAndRoundTripUnionWithRecordBranch()
    {
        assertJson("""
            ["null",{"type":"record","name":"R","fields":[{"name":"v","type":"int"}]}]""",
            new byte[] { 0x02, 0x02 },
            "{\"R\":{\"v\":1}}");
    }

    @Test
    public void shouldEncodeAndRoundTripArray()
    {
        assertJson(
            "{\"type\":\"array\",\"items\":\"int\"}",
            new byte[] { 0x02, 0x02, 0x02, 0x04, 0x00 },
            "[1,2]");
    }

    @Test
    public void shouldEncodeAndRoundTripEmptyArray()
    {
        assertJson(
            "{\"type\":\"array\",\"items\":\"int\"}",
            new byte[] { 0x00 },
            "[]");
    }

    @Test
    public void shouldEncodeAndRoundTripMap()
    {
        assertJson(
            "{\"type\":\"map\",\"values\":\"long\"}",
            new byte[] { 0x02, 0x02, 0x61, 0x0e, 0x00 },
            "{\"a\":7}");
    }

    @Test
    public void shouldRoundTripUnionBranchNames()
    {
        String schema = """
            ["null","boolean","int","long","float","double","bytes","string",
            {"type":"fixed","name":"F","size":2},
            {"type":"enum","name":"E","symbols":["A","B"]},
            {"type":"array","items":"int"},
            {"type":"map","values":"int"},
            {"type":"record","name":"R","fields":[{"name":"v","type":"int"}]}]""";

        assertJsonRoundTrip(schema, "null");
        assertJsonRoundTrip(schema, "{\"boolean\":true}");
        assertJsonRoundTrip(schema, "{\"int\":5}");
        assertJsonRoundTrip(schema, "{\"long\":99}");
        assertJsonRoundTrip(schema, "{\"float\":1.5}");
        assertJsonRoundTrip(schema, "{\"double\":2.25}");
        assertJsonRoundTrip(schema, "{\"bytes\":\"/wA=\"}");
        assertJsonRoundTrip(schema, "{\"string\":\"hi\"}");
        assertJsonRoundTrip(schema, "{\"F\":\"AAE=\"}");
        assertJsonRoundTrip(schema, "{\"E\":\"B\"}");
        assertJsonRoundTrip(schema, "{\"array\":[1,2]}");
        assertJsonRoundTrip(schema, "{\"map\":{\"a\":1}}");
        assertJsonRoundTrip(schema, "{\"R\":{\"v\":7}}");
    }

    @Test
    public void shouldRejectNumberForString()
    {
        assertRejected("\"int\"", "\"notanumber\"");
    }

    @Test
    public void shouldRejectUnexpectedFieldName()
    {
        assertRejected(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}",
            "{\"other\":1}");
    }

    @Test
    public void shouldRejectUnknownUnionBranch()
    {
        assertRejected("[\"null\",\"string\"]", "{\"int\":1}");
    }

    @Test
    public void shouldRejectUnknownEnumSymbol()
    {
        assertRejected(
            "{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"A\",\"B\"]}",
            "\"Z\"");
    }

    @Test
    public void shouldRejectFixedOfWrongSize()
    {
        assertRejected(
            "{\"type\":\"fixed\",\"name\":\"F\",\"size\":4}",
            "\"AAE=\"");
    }

    @Test
    public void shouldRejectInvalidBase64()
    {
        assertRejected("\"bytes\"", "\"!!notbase64!!\"");
    }

    @Test
    public void shouldRejectNonUnionValue()
    {
        assertRejected("[\"null\",\"string\"]", "5");
    }

    @Test
    public void shouldRejectUnionWithoutNullBranch()
    {
        assertRejected("[\"int\",\"string\"]", "null");
    }

    private void assertRejected(
        String schemaText,
        String json)
    {
        AvroSchema schema = Avro.schema(schemaText);
        byte[] jsonBytes = json.getBytes(UTF_8);
        JsonParserEx parser = JsonEx.createParser();
        MutableDirectBuffer out = new UnsafeBuffer(new byte[64]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        AvroPipeline pipeline = AvroJson.stream(schema, parser).into(AvroSink.of(generator));
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(jsonBytes), 0, jsonBytes.length);
        assertEquals(Status.REJECTED, status);
    }

    private void assertJsonRoundTrip(
        String schemaText,
        String json)
    {
        AvroSchema schema = Avro.schema(schemaText);
        assertEquals(json, avroToJson(schema, jsonToAvro(schema, json)));
    }

    @Test
    public void shouldStreamAvroToJsonAcrossDrains()
    {
        String schemaText = """
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"long"},
            {"name":"name","type":"string"},
            {"name":"tags","type":{"type":"array","items":"string"}}]}""";
        // id=7, name="hello", tags=["a","b","c"] via single-element blocks
        byte[] binary =
        {
            0x0e,
            0x0a, 0x68, 0x65, 0x6c, 0x6c, 0x6f,
            0x02, 0x02, 0x61, 0x02, 0x02, 0x62, 0x02, 0x02, 0x63, 0x00
        };
        AvroSchema schema = Avro.schema(schemaText);
        String whole = avroToJson(schema, binary);
        // a bound small enough to force several drains mid-datum, but large enough for any single value
        assertEquals(whole, avroToJsonChunked(schema, binary, 48));
    }

    @Test
    public void shouldStreamJsonToAvroAcrossDrains()
    {
        // a string longer than the output bound forces a mid-value suspend, so the bridge parser must honor
        // consumed() pushback (re-expose the segment remainder) for the stateless sink to resume correctly
        AvroSchema schema = Avro.schema("\"string\"");
        String json = "\"abcdefghijklmnopqrstuvwxyz0123456789\"";
        assertArrayEquals(jsonToAvro(schema, json), jsonToAvroChunked(schema, json, 12));
    }

    private static byte[] jsonToAvroChunked(
        AvroSchema schema,
        String json,
        int limit)
    {
        byte[] jsonBytes = json.getBytes(UTF_8);
        MutableDirectBuffer out = new UnsafeBuffer(new byte[Math.max(256, jsonBytes.length * 4)]);
        JsonParserEx parser = JsonEx.createParser();
        AvroGenerator generator = Avro.generator(schema, out, 0);
        AvroPipeline pipeline = AvroJson.stream(schema, parser).into(AvroSink.of(generator));
        generator.wrap(out, 0, limit);
        pipeline.reset();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        UnsafeBuffer input = new UnsafeBuffer(jsonBytes);
        Status status = pipeline.feed(input, 0, jsonBytes.length);
        while (status == Status.SUSPENDED)
        {
            drainAvro(out, generator, result);
            generator.wrap(out, 0, limit);
            status = pipeline.feed(input, 0, jsonBytes.length);
        }
        if (status != Status.COMPLETED)
        {
            throw new AssertionError("json -> avro did not complete: " + status);
        }
        drainAvro(out, generator, result);
        return result.toByteArray();
    }

    private static void drainAvro(
        MutableDirectBuffer out,
        AvroGenerator generator,
        ByteArrayOutputStream result)
    {
        int length = generator.length();
        byte[] chunk = new byte[length];
        out.getBytes(0, chunk);
        result.write(chunk, 0, length);
    }

    @Test
    public void shouldReuseParserAcrossDatums()
    {
        AvroSchema schema = Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""");
        JsonParserEx parser = JsonEx.createParser();
        MutableDirectBuffer out = new UnsafeBuffer(new byte[128]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        AvroPipeline pipeline = AvroJson.stream(schema, parser).into(AvroSink.of(generator));

        assertArrayEquals(new byte[] { 0x02, 0x04, 0x68, 0x69 },
            feedOnce(pipeline, generator, out, "{\"id\":1,\"name\":\"hi\"}"));
        assertArrayEquals(new byte[] { 0x06, 0x02, 0x7a },
            feedOnce(pipeline, generator, out, "{\"id\":3,\"name\":\"z\"}"));
    }

    private static byte[] feedOnce(
        AvroPipeline pipeline,
        AvroGenerator generator,
        MutableDirectBuffer out,
        String json)
    {
        byte[] jsonBytes = json.getBytes(UTF_8);
        generator.wrap(out, 0, out.capacity());
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(jsonBytes), 0, jsonBytes.length);
        if (status != Status.COMPLETED)
        {
            throw new AssertionError("json -> avro did not complete: " + status);
        }
        byte[] avro = new byte[generator.length()];
        out.getBytes(0, avro);
        return avro;
    }

    @Test
    public void shouldWriteThroughGeneratorApi()
    {
        AvroSchema schema = Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"a","type":"string"},
            {"name":"b","type":"bytes"},
            {"name":"c","type":{"type":"fixed","name":"F","size":2}}]}""");
        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        JsonGeneratorEx json = JsonEx.createGenerator();
        AvroGenerator generator = AvroJson.generator(schema, json).wrap(out, 0, out.capacity());
        generator.writeStartRecord();
        generator.writeString(new UnsafeBuffer("hi".getBytes(UTF_8)), 0, 2);
        generator.writeBytes(new UnsafeBuffer(new byte[] { (byte) 0xff, 0x00 }), 0, 2);
        generator.writeFixed(new UnsafeBuffer(new byte[] { 0x01, 0x02 }), 0, 2);
        generator.writeEnd();
        json.flush();
        byte[] bytes = new byte[json.length()];
        out.getBytes(0, bytes);
        assertEquals("{\"a\":\"hi\",\"b\":\"/wA=\",\"c\":\"AQI=\"}", new String(bytes, UTF_8));

        assertThrows(UnsupportedOperationException.class,
            () -> generator.writeRaw(new UnsafeBuffer(new byte[] { 0x00 }), 0, 1));
    }

    private static String avroToJsonChunked(
        AvroSchema schema,
        byte[] binary,
        int limit)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[limit]);
        JsonGeneratorEx json = JsonEx.createGenerator();
        AvroGenerator generator = AvroJson.generator(schema, json);
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        generator.wrap(out, 0, limit);
        pipeline.reset();
        UnsafeBuffer input = new UnsafeBuffer(binary);
        Status status = pipeline.feed(input, 0, binary.length);
        while (status == Status.SUSPENDED)
        {
            drain(out, json, result);
            generator.wrap(out, 0, limit);
            status = pipeline.feed(input, 0, binary.length);
        }
        if (status != Status.COMPLETED)
        {
            throw new AssertionError("avro -> json did not complete: " + status);
        }
        drain(out, json, result);
        return result.toString(UTF_8);
    }

    private static void drain(
        MutableDirectBuffer out,
        JsonGeneratorEx json,
        ByteArrayOutputStream result)
    {
        json.flush();
        int length = json.length();
        byte[] chunk = new byte[length];
        out.getBytes(0, chunk);
        result.write(chunk, 0, length);
    }

    private void assertJson(
        String schemaText,
        byte[] binary,
        String expectedJson)
    {
        AvroSchema schema = Avro.schema(schemaText);
        assertEquals(expectedJson, avroToJson(schema, binary));
        assertArrayEquals(binary, jsonToAvro(schema, expectedJson));
    }

    private void assertRoundTrip(
        String schemaText,
        byte[] binary)
    {
        AvroSchema schema = Avro.schema(schemaText);
        assertArrayEquals(binary, jsonToAvro(schema, avroToJson(schema, binary)));
    }

    private static String avroToJson(
        AvroSchema schema,
        byte[] binary)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[Math.max(256, binary.length * 8)]);
        JsonGeneratorEx json = JsonEx.createGenerator();
        AvroGenerator generator = AvroJson.generator(schema, json).wrap(out, 0, out.capacity());
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(binary), 0, binary.length);
        if (status != Status.COMPLETED)
        {
            throw new AssertionError("avro -> json did not complete: " + status);
        }
        json.flush();
        byte[] bytes = new byte[json.length()];
        out.getBytes(0, bytes);
        return new String(bytes, UTF_8);
    }

    private static byte[] jsonToAvro(
        AvroSchema schema,
        String json)
    {
        byte[] jsonBytes = json.getBytes(UTF_8);
        MutableDirectBuffer out = new UnsafeBuffer(new byte[Math.max(256, jsonBytes.length * 4)]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        JsonParserEx parser = JsonEx.createParser();
        AvroPipeline pipeline = AvroJson.stream(schema, parser).into(AvroSink.of(generator));
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(jsonBytes), 0, jsonBytes.length);
        if (status != Status.COMPLETED)
        {
            throw new AssertionError("json -> avro did not complete: " + status);
        }
        byte[] avro = new byte[generator.length()];
        out.getBytes(0, avro);
        return avro;
    }
}
