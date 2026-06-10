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

import static io.aklivity.zilla.runtime.common.avro.AvroDecodePipeline.Status.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroMalformedTest
{
    private final Recorder recorder = new Recorder();

    private AvroDecodePipeline.Status decode(
        String schemaText,
        byte[] binary)
    {
        AvroSchema schema = StreamingAvro.schema(schemaText);
        AvroDecodePipeline decoder = schema.decoder(recorder);
        recorder.reset();
        decoder.reset();
        UnsafeBuffer buffer = new UnsafeBuffer(binary);
        return decoder.feed(buffer, 0, binary.length);
    }

    @Test
    public void shouldRejectEnumOrdinalOutOfRange()
    {
        // enum has 2 symbols; index 5 (0x0a) is out of range
        AvroDecodePipeline.Status status = decode(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\"]}",
            new byte[] { 0x0a });
        assertEquals(REJECTED, status);
    }

    @Test
    public void shouldRejectUnionBranchOutOfRange()
    {
        // union has 2 branches; index 9 (0x12) is out of range
        AvroDecodePipeline.Status status = decode("[\"null\",\"string\"]", new byte[] { 0x12 });
        assertEquals(REJECTED, status);
    }

    @Test
    public void shouldRejectNegativeStringLength()
    {
        // zig-zag of -1 is encoded as 0x01; a negative length is malformed
        AvroDecodePipeline.Status status = decode("\"string\"", new byte[] { 0x01 });
        assertEquals(REJECTED, status);
    }

    @Test
    public void shouldRejectOverlongVarint()
    {
        // more than 5 continuation bytes for an int is malformed
        AvroDecodePipeline.Status status = decode("\"int\"",
            new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 });
        assertEquals(REJECTED, status);
    }
}
