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

import static io.aklivity.zilla.runtime.common.avro.AvroSink.Delivery.SEGMENTABLE;
import static io.aklivity.zilla.runtime.common.avro.AvroSink.Delivery.STRUCTURED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

public class AvroRoundTripTest
{
    private void assertRoundTrip(
        String schemaText,
        byte[] original)
    {
        AvroSchema schema = Avro.schema(schemaText);
        // structured parse -> re-generate reproduces the single-element-block form
        assertArrayEquals(original, AvroValues.transcode(schema, original, STRUCTURED));
        // verbatim segment passthrough reproduces the input bytes exactly
        assertArrayEquals(original, AvroValues.transcode(schema, original, SEGMENTABLE));
    }

    @Test
    public void shouldRoundTripPrimitives()
    {
        assertRoundTrip("\"int\"", new byte[] { (byte) 0x80, 0x01 });
        assertRoundTrip("\"long\"", new byte[] { 0x0e });
        assertRoundTrip("\"boolean\"", new byte[] { 0x01 });
        assertRoundTrip("\"string\"", new byte[] { 0x06, 0x66, 0x6f, 0x6f });
        assertRoundTrip("\"bytes\"", new byte[] { 0x04, (byte) 0xff, 0x00 });
        assertRoundTrip("\"null\"", new byte[] {});
    }

    @Test
    public void shouldRoundTripFixed()
    {
        assertRoundTrip(
            "{\"type\":\"fixed\",\"name\":\"Hash\",\"size\":4}",
            new byte[] { 0x01, 0x02, 0x03, 0x04 });
    }

    @Test
    public void shouldRoundTripRecord()
    {
        assertRoundTrip("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""",
            new byte[] { 0x02, 0x04, 0x68, 0x69 });
    }

    @Test
    public void shouldRoundTripNestedRecord()
    {
        assertRoundTrip("""
            {"type":"record","name":"Outer","fields":[
            {"name":"inner","type":{"type":"record","name":"Inner","fields":[
            {"name":"v","type":"long"}]}},
            {"name":"tag","type":"string"}]}""",
            new byte[] { 0x0e, 0x02, 0x7a });
    }

    @Test
    public void shouldRoundTripEnum()
    {
        assertRoundTrip(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\",\"CLUBS\"]}",
            new byte[] { 0x04 });
    }

    @Test
    public void shouldRoundTripUnion()
    {
        assertRoundTrip("[\"null\",\"string\"]", new byte[] { 0x00 });
        assertRoundTrip("[\"null\",\"string\"]", new byte[] { 0x02, 0x02, 0x78 });
    }

    @Test
    public void shouldRoundTripArrayViaSingleElementBlocks()
    {
        assertRoundTrip(
            "{\"type\":\"array\",\"items\":\"int\"}",
            new byte[] { 0x02, 0x02, 0x02, 0x04, 0x00 });
    }

    @Test
    public void shouldRoundTripMapViaSingleElementBlocks()
    {
        assertRoundTrip(
            "{\"type\":\"map\",\"values\":\"long\"}",
            new byte[] { 0x02, 0x02, 0x61, 0x0e, 0x00 });
    }
}
