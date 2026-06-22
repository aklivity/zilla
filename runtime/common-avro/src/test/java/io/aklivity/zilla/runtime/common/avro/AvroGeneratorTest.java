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

import static io.aklivity.zilla.runtime.common.avro.AvroValues.NO_CONTROL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

public class AvroGeneratorTest
{
    private final UnsafeBufferEx out = new UnsafeBufferEx(new byte[64]);
    private final AvroSource zero = new ZeroSource(new byte[0]);

    private AvroSink sink(
        String schemaText)
    {
        AvroSchema schema = Avro.schema(schemaText);
        return AvroSink.of(Avro.generator(schema, out, 0));
    }

    @Test
    public void shouldRejectMismatchedScalarEvent()
    {
        AvroSink sink = sink("\"int\"");
        assertThrows(AvroValidationException.class, () -> sink.transform(NO_CONTROL, zero, AvroEvent.BOOLEAN));
    }

    @Test
    public void shouldRejectEventAfterValueComplete()
    {
        AvroSink sink = sink("\"int\"");
        sink.transform(NO_CONTROL, zero, AvroEvent.INT);
        assertThrows(AvroValidationException.class, () -> sink.transform(NO_CONTROL, zero, AvroEvent.INT));
    }

    @Test
    public void shouldRejectUnexpectedRecordStart()
    {
        AvroSink sink = sink(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}");
        assertThrows(AvroValidationException.class, () -> sink.transform(NO_CONTROL, zero, AvroEvent.INT));
    }

    @Test
    public void shouldStreamBytesValueReportingConsumed()
    {
        AvroSink sink = sink("\"bytes\"");
        AvroSource bytes = new ZeroSource(new byte[] { 0x01, 0x02, 0x03 });
        // writeValue streams the segment and reports control.consumed(...) — here the no-op default
        assertEquals(AvroPipeline.Status.COMPLETED, sink.transform(NO_CONTROL, bytes, AvroEvent.BYTES));
    }

    private static final class ZeroSource implements AvroSource
    {
        private final UnsafeBufferEx empty;

        private ZeroSource(
            byte[] segment)
        {
            this.empty = new UnsafeBufferEx(segment);
        }
        private final AvroLocation location = new AvroLocation()
        {
            @Override
            public int depth()
            {
                return 0;
            }

            @Override
            public long getStreamOffset()
            {
                return 0L;
            }
        };

        @Override
        public boolean getBoolean()
        {
            return false;
        }

        @Override
        public int getInt()
        {
            return 0;
        }

        @Override
        public long getLong()
        {
            return 0L;
        }

        @Override
        public float getFloat()
        {
            return 0f;
        }

        @Override
        public double getDouble()
        {
            return 0d;
        }

        @Override
        public String getString()
        {
            return "";
        }

        @Override
        public String getField()
        {
            return "";
        }

        @Override
        public String getKey()
        {
            return "";
        }

        @Override
        public DirectBufferEx getSegment()
        {
            return empty;
        }

        @Override
        public int deferredBytes()
        {
            return 0;
        }

        @Override
        public AvroType type()
        {
            return null;
        }

        @Override
        public AvroLocation getLocation()
        {
            return location;
        }
    }
}
