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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

class JsonGeneratorDepthTest
{
    private static final int MAX_DEPTH = 64;
    private static final int CAPACITY = 1024;

    @Test
    void shouldTrackMemberOccupancyAtEveryLevelUpToMaximumDepth()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final UnsafeBufferEx output = new UnsafeBufferEx(new byte[CAPACITY]);
        generator.wrap(output, 0, CAPACITY);

        nest(generator, 0);

        assertEquals(expected(0), output.getStringWithoutLengthUtf8(0, generator.length()));
    }

    @Test
    void shouldRejectNestingBeyondMaximumDepth()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(new UnsafeBufferEx(new byte[CAPACITY]), 0, CAPACITY);
        for (int level = 0; level < MAX_DEPTH; level++)
        {
            generator.writeStartArray();
        }

        assertThrows(IllegalStateException.class, generator::writeStartArray);
    }

    @Test
    void shouldRejectVerbatimRunThatOpensBeyondMaximumDepth()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        generator.wrap(new UnsafeBufferEx(new byte[CAPACITY]), 0, CAPACITY);
        for (int level = 0; level < MAX_DEPTH - 4; level++)
        {
            generator.writeStartArray();
        }

        final byte[] bytes = "[[[[[[[[[[1 ".getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        while (parser.nextEvent(Mode.STRUCTURED) != JsonEvent.VALUE_NUMBER)
        {
        }
        final JsonVerbatim verbatim = parser.getVerbatim(bytes.length);

        assertThrows(IllegalStateException.class, () -> generator.writeVerbatim(verbatim));
    }

    // an array per level, holding a number before the nested array at even levels and one after it at odd levels,
    // so every level separates its second member from its first
    private static void nest(
        JsonGeneratorEx generator,
        int level)
    {
        generator.writeStartArray();
        if (level % 2 == 0)
        {
            generator.write(1);
        }
        if (level < MAX_DEPTH - 1)
        {
            nest(generator, level + 1);
        }
        else
        {
            generator.write(0);
        }
        if (level % 2 == 1)
        {
            generator.write(2);
        }
        generator.writeEnd();
    }

    private static String expected(
        int level)
    {
        return "[" + (level % 2 == 0 ? "1," : "") + (level < MAX_DEPTH - 1 ? expected(level + 1) : "0") +
            (level % 2 == 1 ? ",2" : "") + "]";
    }
}
