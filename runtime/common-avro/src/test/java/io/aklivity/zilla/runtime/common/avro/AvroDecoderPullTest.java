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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class AvroDecoderPullTest
{
    @Test
    public void shouldPullRecordEventsDirectly()
    {
        AvroDecoder decoder = Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""").decoder();

        // id=1 (0x02), name="hi" (0x04 'h' 'i')
        decoder.wrap(new UnsafeBuffer(new byte[] { 0x02, 0x04, 0x68, 0x69 }), 0, 4);

        List<AvroEvent> events = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        int id = 0;
        String name = null;
        while (decoder.hasNextEvent())
        {
            AvroEvent event = decoder.nextEvent();
            events.add(event);
            switch (event)
            {
            case FIELD_NAME:
                fields.add(decoder.getField());
                break;
            case INT:
                id = decoder.getInt();
                break;
            case STRING:
                name = decoder.getSegment() == null ? null : new String(toBytes(decoder), UTF_8);
                break;
            default:
                break;
            }
        }

        assertEquals(List.of(START_MESSAGE, START_RECORD, FIELD_NAME, INT, FIELD_NAME, STRING, END_RECORD, END_MESSAGE),
            events);
        assertEquals(List.of("id", "name"), fields);
        assertEquals(1, id);
        assertEquals("hi", name);
    }

    @Test
    public void shouldReportLocationWhilePulling()
    {
        AvroDecoder decoder = Avro.schema("\"int\"").decoder();
        decoder.wrap(new UnsafeBuffer(new byte[] { (byte) 0x80, 0x01 }), 0, 2);

        long position = -1L;
        int depth = -1;
        while (decoder.hasNextEvent())
        {
            if (decoder.nextEvent() == INT)
            {
                position = decoder.getLocation().position();
                depth = decoder.getLocation().depth();
                assertEquals(64, decoder.getInt());
            }
        }
        assertEquals(0L, position);
        assertEquals(1, depth);
    }

    private static byte[] toBytes(
        AvroDecoder decoder)
    {
        byte[] dst = new byte[decoder.getSegment().capacity()];
        decoder.getSegment().getBytes(0, dst);
        return dst;
    }
}
