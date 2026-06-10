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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroValues.Replay;

public class AvroEncoderTest
{
    private final UnsafeBuffer out = new UnsafeBuffer(new byte[64]);
    private final Replay replay = new Replay();

    private AvroEncodePipeline encoder(
        String schemaText)
    {
        AvroEncodePipeline encoder = StreamingAvro.schema(schemaText).encoder(out, 0);
        encoder.reset();
        return encoder;
    }

    @Test
    public void shouldRejectMismatchedScalarEvent()
    {
        AvroEncodePipeline encoder = encoder("\"int\"");
        assertThrows(AvroValidationException.class, () -> encoder.feed(AvroEvent.BOOLEAN, replay));
    }

    @Test
    public void shouldRejectEventAfterValueComplete()
    {
        AvroEncodePipeline encoder = encoder("\"int\"");
        encoder.feed(AvroEvent.INT, replay);
        assertThrows(AvroValidationException.class, () -> encoder.feed(AvroEvent.INT, replay));
    }

    @Test
    public void shouldRejectUnexpectedRecordStart()
    {
        AvroEncodePipeline encoder = encoder(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"id\",\"type\":\"int\"}]}");
        assertThrows(AvroValidationException.class, () -> encoder.feed(AvroEvent.INT, replay));
    }
}
