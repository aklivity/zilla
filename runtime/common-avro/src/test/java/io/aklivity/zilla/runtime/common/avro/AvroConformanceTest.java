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

import static io.aklivity.zilla.runtime.common.avro.AvroDecodePipeline.Status.COMPLETE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroValues.Entry;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Replay;

/**
 * Conformance test for the {@code common-avro} codec. The Apache Avro {@code interop.avsc} schema
 * (the cross-language interoperability schema, which enumerates the full Avro type system including
 * a recursive record) defines the coverage surface; the reference Apache Avro library (test scope
 * only) is the byte oracle at the raw single-object datum layer.
 * <p>
 * For each randomly generated value the reference encodes canonical bytes; the value then flows
 * through this codec ({@code decode -> events -> encode}) and the result is re-canonicalized by the
 * reference. Equality of the two canonical encodings proves this codec read the reference bytes
 * correctly and emitted valid Avro that the reference reads back to the same value, while remaining
 * agnostic to the legitimate block-framing difference (single-element blocks here vs. one block in
 * the reference).
 */
public class AvroConformanceTest
{
    private Schema referenceSchema;
    private AvroSchema schema;

    private final Recorder recorder = new Recorder();
    private final Replay replay = new Replay();

    @BeforeEach
    public void setup() throws IOException
    {
        String text;
        try (InputStream in = getClass().getResourceAsStream("interop.avsc"))
        {
            text = new String(in.readAllBytes(), UTF_8);
        }
        referenceSchema = new Schema.Parser().parse(text);
        schema = StreamingAvro.schema(text);
    }

    @Test
    public void shouldConformToInteropSchemaAcrossRandomValues()
    {
        Random random = new Random(42L);
        for (int i = 0; i < 500; i++)
        {
            Object value = randomValue(referenceSchema, random, 5);
            byte[] reference = referenceEncode(value);

            byte[] roundTripped = encode(decode(reference));

            // compare values through the same reference reader on both sides: this proves our
            // decode + encode is datum-faithful while remaining agnostic to the legitimate
            // block-framing difference (single-element blocks here vs. one block in the reference)
            assertEquals(referenceDecode(reference), referenceDecode(roundTripped), "iteration " + i);
        }
    }

    private List<Entry> decode(
        byte[] binary)
    {
        AvroDecodePipeline decoder = schema.decoder(recorder);
        recorder.reset();
        decoder.reset();
        UnsafeBuffer buffer = new UnsafeBuffer(binary);
        assertEquals(COMPLETE, decoder.feed(buffer, 0, binary.length));
        return List.copyOf(recorder.entries());
    }

    private byte[] encode(
        List<Entry> entries)
    {
        UnsafeBuffer out = new UnsafeBuffer(new byte[64 * 1024]);
        AvroEncodePipeline encoder = schema.encoder(out, 0);
        encoder.reset();
        for (Entry entry : entries)
        {
            replay.wrap(entry);
            encoder.feed(entry.event, replay);
        }
        byte[] bytes = new byte[encoder.length()];
        out.getBytes(0, bytes);
        return bytes;
    }

    private byte[] referenceEncode(
        Object value)
    {
        byte[] bytes;
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            GenericDatumWriter<Object> writer = new GenericDatumWriter<>(referenceSchema);
            writer.write(value, encoder);
            encoder.flush();
            bytes = out.toByteArray();
        }
        catch (IOException ex)
        {
            throw new AssertionError(ex);
        }
        return bytes;
    }

    private Object referenceDecode(
        byte[] binary)
    {
        Object value;
        try
        {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(binary, null);
            GenericDatumReader<Object> reader = new GenericDatumReader<>(referenceSchema);
            value = reader.read(null, decoder);
        }
        catch (IOException ex)
        {
            throw new AssertionError(ex);
        }
        return value;
    }

    private Object randomValue(
        Schema type,
        Random random,
        int depth)
    {
        Object value;
        switch (type.getType())
        {
        case RECORD:
            GenericData.Record record = new GenericData.Record(type);
            for (Schema.Field field : type.getFields())
            {
                record.put(field.name(), randomValue(field.schema(), random, depth));
            }
            value = record;
            break;
        case ENUM:
            List<String> symbols = type.getEnumSymbols();
            value = new GenericData.EnumSymbol(type, symbols.get(random.nextInt(symbols.size())));
            break;
        case ARRAY:
            int items = depth <= 0 ? 0 : random.nextInt(4);
            GenericData.Array<Object> array = new GenericData.Array<>(items, type);
            for (int i = 0; i < items; i++)
            {
                array.add(randomValue(type.getElementType(), random, depth - 1));
            }
            value = array;
            break;
        case MAP:
            int entries = depth <= 0 ? 0 : random.nextInt(4);
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < entries; i++)
            {
                map.put("k" + i, randomValue(type.getValueType(), random, depth - 1));
            }
            value = map;
            break;
        case UNION:
            List<Schema> branches = type.getTypes();
            value = randomValue(branches.get(random.nextInt(branches.size())), random, depth - 1);
            break;
        case FIXED:
            byte[] fixed = new byte[type.getFixedSize()];
            random.nextBytes(fixed);
            value = new GenericData.Fixed(type, fixed);
            break;
        case BYTES:
            byte[] bytes = new byte[random.nextInt(8)];
            random.nextBytes(bytes);
            value = ByteBuffer.wrap(bytes);
            break;
        case STRING:
            value = randomString(random);
            break;
        case INT:
            value = random.nextInt();
            break;
        case LONG:
            value = random.nextLong();
            break;
        case FLOAT:
            value = random.nextFloat();
            break;
        case DOUBLE:
            value = random.nextDouble();
            break;
        case BOOLEAN:
            value = random.nextBoolean();
            break;
        default:
            value = null;
            break;
        }
        return value;
    }

    private String randomString(
        Random random)
    {
        int length = random.nextInt(8);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        return builder.toString();
    }
}
