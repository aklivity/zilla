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

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class AvroMalformedTest
{
    private AvroPipeline.Status parse(
        String schemaText,
        byte[] binary)
    {
        return AvroValues.record(Avro.schema(schemaText), binary).status;
    }

    @Test
    public void shouldRejectEnumOrdinalOutOfRange()
    {
        assertEquals(REJECTED, parse(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\"]}",
            new byte[] { 0x0a }));
    }

    @Test
    public void shouldRejectUnionBranchOutOfRange()
    {
        assertEquals(REJECTED, parse("[\"null\",\"string\"]", new byte[] { 0x12 }));
    }

    @Test
    public void shouldRejectNegativeStringLength()
    {
        assertEquals(REJECTED, parse("\"string\"", new byte[] { 0x01 }));
    }

    @Test
    public void shouldRejectOverlongVarint()
    {
        assertEquals(REJECTED, parse("\"int\"",
            new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 }));
    }
}
