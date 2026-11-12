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

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;
import io.aklivity.zilla.runtime.common.lang.CharSequenceEx;
import io.aklivity.zilla.runtime.common.lang.SegmentTransferTarget;

// every token a parser reads is a view that transfers its chars, whichever kind of token it is
class JsonParserTransferTest
{
    @Test
    void shouldTransferAKeyAsItIsWritten()
    {
        assertEquals("te\\u0078t", transfers("{\"te\\u0078t\":1}", JsonEvent.KEY_NAME));
    }

    @Test
    void shouldTransferANumberAsItIsWritten()
    {
        assertEquals("-1.5e+3", transfers("[-1.5e+3]", JsonEvent.VALUE_NUMBER));
    }

    @Test
    void shouldTransferAStringValueAsItIsWritten()
    {
        assertEquals("a\\né\\\"", transfers("[\"a\\né\\\"\"]", JsonEvent.VALUE_STRING));
    }

    @Test
    void shouldTransferAStringReadInsideAnEscapedScopeWithTheEscapesOfTheStringThatHoldsIt()
    {
        final String json = "{\"a\":\"{\\\"b\\\":\\\"x\\\\ny\\\"}\"}";

        assertEquals("x\\\\ny", transfersInScope(json, "x\ny"));
    }

    @Test
    void shouldTransferNothingForAnEmptyString()
    {
        assertEquals("", transfers("[\"\"]", JsonEvent.VALUE_STRING));
    }

    private static String transfers(
        String json,
        JsonEvent expected)
    {
        final JsonParserEx parser = parser(json);
        String transferred = null;

        while (transferred == null && parser.hasNextEvent())
        {
            if (parser.nextEvent(Mode.STRUCTURED) == expected)
            {
                transferred = transfer(parser.getStringView());
            }
        }

        return transferred;
    }

    private static String transfersInScope(
        String json,
        String chars)
    {
        final JsonParserEx parser = parser(json);
        String transferred = null;

        while (transferred == null && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent(Mode.STRUCTURED);

            if (event == JsonEvent.KEY_NAME && parser.getStringView().toString().equals("a"))
            {
                parser.escaped();
            }
            else if (event == JsonEvent.VALUE_STRING)
            {
                assertEquals(chars, parser.getStringView().toString());
                assertEquals(JsonStringView.Kind.ESCAPED, parser.getStringView().kind());
                transferred = transfer(parser.getStringView());
            }
        }

        return transferred;
    }

    private static JsonParserEx parser(
        String json)
    {
        final byte[] bytes = (json + " ").getBytes(UTF_8);
        final JsonParserEx parser = new JsonParserImpl();

        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        return parser;
    }

    private static String transfer(
        CharSequenceEx view)
    {
        final byte[] bytes = new byte[64];
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final SegmentTransferTarget target = new SegmentTransferTarget().wrap(buffer.segment(), buffer.wrapAdjustment());

        return new String(bytes, 0, view.transferTo(0, view.length(), target), UTF_8);
    }
}
