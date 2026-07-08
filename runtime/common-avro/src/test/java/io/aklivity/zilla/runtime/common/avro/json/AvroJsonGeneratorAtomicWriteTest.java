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
package io.aklivity.zilla.runtime.common.avro.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.Avro;
import io.aklivity.zilla.runtime.common.avro.AvroGenerator;
import io.aklivity.zilla.runtime.common.avro.AvroSchema;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;

// Drives AvroJsonGeneratorImpl directly (bypassing AvroSinkImpl's conservative HEADROOM pre-check
// entirely) so the window size handed to the wrapped JsonGeneratorEx is exact, proving the adapter itself
// never desyncs from the underlying JSON generator: an atomic write (brace/bracket/boolean/null) that does
// not fit writes nothing and reports false, rather than the caller (AvroJsonGeneratorImpl) advancing its
// schema-walk state (push()/complete()) as though it had succeeded, which would produce malformed JSON with
// no signal to the caller. Regression coverage for #2040.
class AvroJsonGeneratorAtomicWriteTest
{
    @Test
    void shouldNotWriteBooleanThatExceedsOutputLimit()
    {
        AvroSchema schema = Avro.schema("\"boolean\"");
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        // "false" needs 5 bytes at the top level (no leading separator); 3 is deliberately short
        AvroGenerator generator = AvroJson.generator(schema, json, false).wrap(out, 0, 3);

        assertFalse(generator.writeBoolean(false));
        assertEquals(0, generator.length());
    }

    @Test
    void shouldNotWriteNullThatExceedsOutputLimit()
    {
        AvroSchema schema = Avro.schema("\"null\"");
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        // "null" needs 4 bytes at the top level; 2 is deliberately short
        AvroGenerator generator = AvroJson.generator(schema, json, false).wrap(out, 0, 2);

        assertFalse(generator.writeNull());
        assertEquals(0, generator.length());
    }

    @Test
    void shouldNotWriteRecordStartThatExceedsOutputLimit()
    {
        AvroSchema schema = Avro.schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"a\",\"type\":\"boolean\"}]}");
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        AvroGenerator generator = AvroJson.generator(schema, json, false).wrap(out, 0, 0);

        assertFalse(generator.writeStartRecord());
        assertEquals(0, generator.length());
    }

    @Test
    void shouldRecoverOnceRoomIsAvailable()
    {
        AvroSchema schema = Avro.schema("\"boolean\"");
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx tiny = new UnsafeBufferEx(new byte[64]);
        AvroGenerator generator = AvroJson.generator(schema, json, false).wrap(tiny, 0, 3);

        assertFalse(generator.writeBoolean(false));

        MutableDirectBufferEx roomy = new UnsafeBufferEx(new byte[64]);
        generator.wrap(roomy, 0, roomy.capacity());

        assertTrue(generator.writeBoolean(false));
        assertEquals(5, generator.length());
    }
}
