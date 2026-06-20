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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonParserNumberRetentionTest
{
    // a number lexeme longer than the feed window, with no terminator: the window ends mid-number so
    // the parser delivers a VALUE_NUMBER with bytes still deferred rather than the whole value
    private static final byte[] LONG_NUMBER =
        "12345678901234567890123456789012345678901234567890".getBytes(UTF_8);

    @Test
    void shouldAssertGetBigDecimalWhileValueDeferred()
    {
        JsonParserEx parser = JsonEx.createParser();
        parser.wrap(new UnsafeBuffer(LONG_NUMBER), 0, LONG_NUMBER.length, false);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertTrue(parser.deferredBytes());

        // getBigDecimal() promises the whole value; calling it while bytes are still deferred is a
        // contract violation, caught by assertion rather than silently returning a partial number
        assertThrows(AssertionError.class, parser::getBigDecimal);
    }

    @Test
    void shouldReturnWholeBigDecimalWhenComplete()
    {
        JsonParserEx parser = JsonEx.createParser();
        // last == true: terminal EOF completes the trailing number, so the whole value is present
        parser.wrap(new UnsafeBuffer(LONG_NUMBER), 0, LONG_NUMBER.length, true);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertFalse(parser.deferredBytes());
        assertEquals(new BigDecimal(new String(LONG_NUMBER, UTF_8)), parser.getBigDecimal());
    }
}
