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

import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_MESSAGE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIELD_NAME;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_MESSAGE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class AvroParserPullTest
{
    @Test
    public void shouldPullEventsDirectly()
    {
        AvroParser decoder = Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""").parser();

        // id=1 (0x02), name="hi" (0x04 'h' 'i')
        decoder.wrap(new UnsafeBuffer(new byte[] { 0x02, 0x04, 0x68, 0x69 }), 0, 4);

        List<AvroEvent> events = new ArrayList<>();
        while (decoder.hasNextEvent())
        {
            events.add(decoder.nextEvent());
        }

        assertEquals(
            List.of(START_MESSAGE, START_RECORD, FIELD_NAME, INT, FIELD_NAME, STRING, END_RECORD, END_MESSAGE),
            events);
    }

    @Test
    public void shouldPullAcrossFrames()
    {
        AvroParser decoder = Avro.schema("\"int\"").parser();
        UnsafeBuffer one = new UnsafeBuffer(new byte[1]);

        // multi-byte varint 64 -> 0x80 0x01, fed one byte at a time
        one.putByte(0, (byte) 0x80);
        decoder.wrap(one, 0, 1);
        // START_MESSAGE is available, but the INT value is not yet
        assertEquals(true, decoder.hasNextEvent());
        assertEquals(START_MESSAGE, decoder.nextEvent());
        assertEquals(false, decoder.hasNextEvent());

        one.putByte(0, (byte) 0x01);
        decoder.wrap(one, 0, 1);

        List<AvroEvent> events = new ArrayList<>();
        while (decoder.hasNextEvent())
        {
            events.add(decoder.nextEvent());
        }
        assertEquals(List.of(INT, END_MESSAGE), events);
    }
}
