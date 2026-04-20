/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class StreamingJsonLocationTest
{
    @Test
    void shouldReturnMinusOneForLineAndColumn()
    {
        JsonLocation location = parserFor("{}").getLocation();

        assertEquals(-1L, location.getLineNumber());
        assertEquals(-1L, location.getColumnNumber());
    }

    @Test
    void shouldReturnZeroStreamOffsetBeforeAdvance()
    {
        JsonLocation location = parserFor("{}").getLocation();

        assertEquals(0L, location.getStreamOffset());
    }

    @Test
    void shouldAdvanceStreamOffsetAsParserProgresses()
    {
        JsonParser parser = parserFor("[1,2]");

        parser.next();
        assertEquals(1L, parser.getLocation().getStreamOffset());
        parser.next();
        assertEquals(2L, parser.getLocation().getStreamOffset());
        parser.next();
        assertEquals(4L, parser.getLocation().getStreamOffset());
        parser.next();
        assertEquals(5L, parser.getLocation().getStreamOffset());
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
