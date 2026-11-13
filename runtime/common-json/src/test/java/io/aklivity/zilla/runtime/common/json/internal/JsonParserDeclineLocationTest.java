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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

class JsonParserDeclineLocationTest
{
    private static final int[] WINDOWS = {24, 40, 64, 100, 128};

    @Test
    void shouldReportTheLocationOfASingleWindowWhenPartOfAFragmentIsLeft()
    {
        assertLocations("{\"long\":\"" + "abcdefghij".repeat(40) + "\"}");
    }

    @Test
    void shouldReportTheLocationOfASingleWindowWhenPartOfAnEscapedFragmentIsLeft()
    {
        assertLocations("{\"long\":\"" + "ab\\n\\t\\u00e9".repeat(40) + "\"}");
    }

    @Test
    void shouldReportTheLocationOfASingleWindowWhenPartOfAMultibyteFragmentIsLeft()
    {
        assertLocations("{\"long\":\"" + "é€😀z".repeat(40) + "\"}");
    }

    @Test
    void shouldReportTheLocationOfASingleWindowAcrossLinesAroundAFragment()
    {
        assertLocations("{\n  \"a\": [1,\n 2],\n  \"long\": \"" + "abcdefghij".repeat(40) + "\",\n  \"b\": {\"c\": 3}\n}");
    }

    private static void assertLocations(
        String document)
    {
        final byte[] bytes = (document + " ").getBytes(UTF_8);
        final List<String> expected = locations(bytes, bytes.length);

        for (int window : WINDOWS)
        {
            assertEquals(expected, locations(bytes, window), "window " + window);
        }
    }

    private static List<String> locations(
        byte[] bytes,
        int window)
    {
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = new JsonParserImpl();
        final List<String> locations = new ArrayList<>();

        int progress = 0;
        int limit = 0;
        while (limit < bytes.length)
        {
            limit = Math.min(limit + window, bytes.length);
            parser.wrap(input, progress, limit, limit == bytes.length);

            while (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                if (event == JsonEvent.VALUE_STRING || event == JsonEvent.KEY_NAME)
                {
                    final int length = parser.getStringView().length();
                    parser.consumed(parser.deferredBytes() && length > 1 ? length / 2 : length);
                }
                else if (event == JsonEvent.START_OBJECT || event == JsonEvent.END_OBJECT ||
                    event == JsonEvent.START_ARRAY || event == JsonEvent.END_ARRAY)
                {
                    locations.add(event + "@" + parser.getLocation().getLineNumber() + ":" +
                        parser.getLocation().getColumnNumber());
                }
            }

            progress = limit - parser.remaining();
        }

        return locations;
    }
}
