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
package io.aklivity.zilla.runtime.common.lang;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StringsTest
{
    private void assertUtf8Length(
        String value)
    {
        int expected = value.getBytes(UTF_8).length;
        int actual = Strings.utf8Length(value);
        assertEquals(expected, actual, () -> "utf8Length mismatch for \"" + value + "\"");
    }

    @Test
    public void shouldComputeUtf8LengthForEmptyString()
    {
        assertUtf8Length("");
    }

    @Test
    public void shouldComputeUtf8LengthForAsciiString()
    {
        assertUtf8Length("events");
    }

    @Test
    public void shouldComputeUtf8LengthForTwoByteCharacters()
    {
        assertUtf8Length("café");
    }

    @Test
    public void shouldComputeUtf8LengthForThreeByteCharacters()
    {
        assertUtf8Length("日本");
    }

    @Test
    public void shouldComputeUtf8LengthForSurrogatePair()
    {
        assertUtf8Length("🎉");
    }

    @Test
    public void shouldComputeUtf8LengthForMixedContent()
    {
        assertUtf8Length("🎉-日本-café");
    }

    @Test
    public void shouldComputeUtf8LengthForLoneHighSurrogate()
    {
        assertUtf8Length("a\uD83Db");
    }

    @Test
    public void shouldComputeUtf8LengthForLoneLowSurrogate()
    {
        assertUtf8Length("a\uDC00b");
    }
}
