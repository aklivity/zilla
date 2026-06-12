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

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroBoundedParserTest
{
    @Test
    public void shouldRejectDatumExceedingWorkLimit()
    {
        AvroSchema schema = Avro.schema("\"bytes\"");
        AvroPipeline pipeline = Avro.parser(schema, Map.of(Avro.WORK_MAX_BYTES, 8)).stream().into(new Recorder());
        pipeline.reset();

        // bytes length prefix claims 100 (0xC8 0x01) but the value never completes, so the parser
        // keeps buffering across frames until it crosses the 8-byte work limit
        byte[] binary = { (byte) 0xC8, 0x01, 0, 0, 0, 0, 0, 0, 0, 0 };
        UnsafeBuffer one = new UnsafeBuffer(new byte[1]);
        Status status = Status.ADVANCED;
        for (int i = 0; i < binary.length && status != REJECTED; i++)
        {
            one.putByte(0, binary[i]);
            status = pipeline.feed(one, 0, 1);
        }
        assertEquals(REJECTED, status);
    }

    @Test
    public void shouldUseDefaultWorkLimitWhenConfigAbsent()
    {
        AvroSchema schema = Avro.schema("\"int\"");
        AvroPipeline pipeline = Avro.parser(schema, Map.of()).stream().into(new Recorder());
        pipeline.reset();
        Status status = pipeline.feed(new UnsafeBuffer(new byte[] { 0x02 }), 0, 1);
        assertEquals(COMPLETED, status);
    }
}
