/*
 * Copyright 2021-2026 Aklivity Inc
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

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class AvroParserPullTest
{
    @Test
    public void shouldPullEventsDirectly()
    {
        AvroParser parser = Avro.parser(Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}"""));

        // id=1 (0x02), name="hi" (0x04 'h' 'i')
        parser.wrap(new UnsafeBufferEx(new byte[] { 0x02, 0x04, 0x68, 0x69 }), 0, 4);

        List<AvroEvent> events = new ArrayList<>();
        while (parser.hasNext())
        {
            events.add(parser.nextEvent());
        }

        assertEquals(
            List.of(START_MESSAGE, START_RECORD, FIELD_NAME, INT, FIELD_NAME, STRING, END_RECORD, END_MESSAGE),
            events);
    }

    @Test
    public void shouldPullAcrossFrames()
    {
        AvroParser parser = Avro.parser(Avro.schema("\"int\""));

        // multi-byte varint 64 -> 0x80 0x01; the caller re-presents the unconsumed bytes plus each new byte.
        // last == false: more input will follow, so the partial INT underflows rather than truncating
        parser.wrap(new UnsafeBufferEx(new byte[] { (byte) 0x80 }), 0, 1, false);
        // START_MESSAGE is available, but the INT value is not yet
        assertEquals(true, parser.hasNext());
        assertEquals(START_MESSAGE, parser.nextEvent());
        assertEquals(false, parser.hasNext());

        // re-present the unconsumed 0x80 plus the newly arrived 0x01 as the final window
        parser.wrap(new UnsafeBufferEx(new byte[] { (byte) 0x80, 0x01 }), 0, 2);

        List<AvroEvent> events = new ArrayList<>();
        while (parser.hasNext())
        {
            events.add(parser.nextEvent());
        }
        assertEquals(List.of(INT, END_MESSAGE), events);
    }
}
