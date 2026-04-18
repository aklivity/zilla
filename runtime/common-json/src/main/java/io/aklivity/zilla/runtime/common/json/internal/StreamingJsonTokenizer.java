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
package io.aklivity.zilla.runtime.common.json.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

public final class StreamingJsonTokenizer
{
    private enum ParseState
    {
        DOC_START,
        DOC_DONE,
        OBJ_AFTER_OPEN,
        OBJ_AFTER_COMMA,
        OBJ_AFTER_KEY,
        OBJ_AFTER_VALUE,
        ARR_AFTER_OPEN,
        ARR_AFTER_COMMA,
        ARR_AFTER_VALUE
    }

    private Deque<ParseState> stack = new ArrayDeque<>();
    private final StringBuilder scratch = new StringBuilder();

    private ParseState state = ParseState.DOC_START;
    private JsonParser.Event pendingEvent;
    private String pendingString;
    private long streamOffset;
    private int attemptConsumed;

    public boolean advance(
        InputStream in) throws IOException
    {
        if (state == ParseState.DOC_DONE)
        {
            return false;
        }

        in.mark(Integer.MAX_VALUE);
        final ParseState savedState = state;
        @SuppressWarnings("unchecked")
        final Deque<ParseState> savedStack = (Deque<ParseState>) ((ArrayDeque<ParseState>) stack).clone();
        attemptConsumed = 0;
        pendingEvent = null;
        pendingString = null;

        try
        {
            advanceOne(in);
            streamOffset += attemptConsumed;
            return true;
        }
        catch (EOFException ex)
        {
            in.reset();
            state = savedState;
            stack = savedStack;
            pendingEvent = null;
            pendingString = null;
            return false;
        }
    }

    public JsonParser.Event event()
    {
        return pendingEvent;
    }

    public void clearEvent()
    {
        pendingEvent = null;
    }

    public String stringValue()
    {
        return pendingString;
    }

    public long streamOffset()
    {
        return streamOffset;
    }

    private void advanceOne(
        InputStream in) throws IOException
    {
        switch (state)
        {
        case DOC_START:
            consumeValue(in);
            break;
        case OBJ_AFTER_OPEN:
            consumeKeyOrEnd(in);
            break;
        case OBJ_AFTER_KEY:
            consumeColonValue(in);
            break;
        case OBJ_AFTER_VALUE:
            consumeSeparatorOrEnd(in, true);
            break;
        case OBJ_AFTER_COMMA:
            consumeKey(in);
            break;
        case ARR_AFTER_OPEN:
            consumeValueOrEnd(in);
            break;
        case ARR_AFTER_VALUE:
            consumeSeparatorOrEnd(in, false);
            break;
        case ARR_AFTER_COMMA:
            consumeValue(in);
            break;
        default:
            throw new JsonParsingException("Unexpected parse state: " + state, null);
        }
    }

    private void consumeValue(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        parseValue(in, c);
    }

    private void consumeValueOrEnd(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (c == ']')
        {
            emitEnd(JsonParser.Event.END_ARRAY);
        }
        else
        {
            parseValue(in, c);
        }
    }

    private void consumeKey(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        parseKey(in, c);
    }

    private void consumeKeyOrEnd(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (c == '}')
        {
            emitEnd(JsonParser.Event.END_OBJECT);
        }
        else
        {
            parseKey(in, c);
        }
    }

    private void consumeColonValue(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (c != ':')
        {
            throw new JsonParsingException("Expected ':' but got: " + describe(c), null);
        }
        c = skipWhitespace(in);
        parseValue(in, c);
    }

    private void consumeSeparatorOrEnd(
        InputStream in,
        boolean inObject) throws IOException
    {
        int c = skipWhitespace(in);
        if (c == ',')
        {
            state = inObject ? ParseState.OBJ_AFTER_COMMA : ParseState.ARR_AFTER_COMMA;
            // comma is a separator, not an event; consume next token recursively
            int d = skipWhitespace(in);
            if (inObject)
            {
                parseKey(in, d);
            }
            else
            {
                parseValue(in, d);
            }
        }
        else if (inObject && c == '}' || !inObject && c == ']')
        {
            emitEnd(inObject ? JsonParser.Event.END_OBJECT : JsonParser.Event.END_ARRAY);
        }
        else
        {
            throw new JsonParsingException("Expected ',' or closing bracket but got: " + describe(c), null);
        }
    }

    private void parseValue(
        InputStream in,
        int c) throws IOException
    {
        switch (c)
        {
        case '{':
            pendingEvent = JsonParser.Event.START_OBJECT;
            pushAndEnter(ParseState.OBJ_AFTER_OPEN);
            break;
        case '[':
            pendingEvent = JsonParser.Event.START_ARRAY;
            pushAndEnter(ParseState.ARR_AFTER_OPEN);
            break;
        case '"':
            pendingEvent = JsonParser.Event.VALUE_STRING;
            pendingString = parseStringContent(in);
            afterValueConsumed();
            break;
        case 't':
            expectLiteral(in, "rue");
            pendingEvent = JsonParser.Event.VALUE_TRUE;
            afterValueConsumed();
            break;
        case 'f':
            expectLiteral(in, "alse");
            pendingEvent = JsonParser.Event.VALUE_FALSE;
            afterValueConsumed();
            break;
        case 'n':
            expectLiteral(in, "ull");
            pendingEvent = JsonParser.Event.VALUE_NULL;
            afterValueConsumed();
            break;
        default:
            if (c == '-' || c >= '0' && c <= '9')
            {
                pendingEvent = JsonParser.Event.VALUE_NUMBER;
                pendingString = parseNumberContent(in, c);
                afterValueConsumed();
            }
            else
            {
                throw new JsonParsingException("Unexpected character starting value: " + describe(c), null);
            }
        }
    }

    private void parseKey(
        InputStream in,
        int c) throws IOException
    {
        if (c != '"')
        {
            throw new JsonParsingException("Expected '\"' for key but got: " + describe(c), null);
        }
        pendingEvent = JsonParser.Event.KEY_NAME;
        pendingString = parseStringContent(in);
        state = ParseState.OBJ_AFTER_KEY;
    }

    private void pushAndEnter(
        ParseState nested)
    {
        stack.push(returnState());
        state = nested;
    }

    private ParseState returnState()
    {
        switch (state)
        {
        case DOC_START:
            return ParseState.DOC_DONE;
        case OBJ_AFTER_KEY:
            return ParseState.OBJ_AFTER_VALUE;
        case ARR_AFTER_OPEN:
        case ARR_AFTER_COMMA:
            return ParseState.ARR_AFTER_VALUE;
        default:
            throw new JsonParsingException("Unexpected return-point state: " + state, null);
        }
    }

    private void afterValueConsumed()
    {
        // scalar value; transition based on current state
        switch (state)
        {
        case DOC_START:
            state = ParseState.DOC_DONE;
            break;
        case OBJ_AFTER_KEY:
            state = ParseState.OBJ_AFTER_VALUE;
            break;
        case ARR_AFTER_OPEN:
        case ARR_AFTER_COMMA:
            state = ParseState.ARR_AFTER_VALUE;
            break;
        default:
            throw new JsonParsingException("Unexpected state after value: " + state, null);
        }
    }

    private void emitEnd(
        JsonParser.Event endEvent)
    {
        pendingEvent = endEvent;
        if (stack.isEmpty())
        {
            state = ParseState.DOC_DONE;
        }
        else
        {
            state = stack.pop();
        }
    }

    private int skipWhitespace(
        InputStream in) throws IOException
    {
        int c;
        do
        {
            c = readByte(in);
        }
        while (c == ' ' || c == '\t' || c == '\n' || c == '\r');
        return c;
    }

    private void expectLiteral(
        InputStream in,
        String expected) throws IOException
    {
        for (int i = 0; i < expected.length(); i++)
        {
            int c = readByte(in);
            if (c != expected.charAt(i))
            {
                throw new JsonParsingException("Unexpected character in literal: " + describe(c), null);
            }
        }
    }

    private String parseStringContent(
        InputStream in) throws IOException
    {
        scratch.setLength(0);
        while (true)
        {
            int c = readByte(in);
            if (c == '"')
            {
                return scratch.toString();
            }
            else if (c == '\\')
            {
                int esc = readByte(in);
                switch (esc)
                {
                case '"':
                case '\\':
                case '/':
                    scratch.append((char) esc);
                    break;
                case 'b':
                    scratch.append('\b');
                    break;
                case 'f':
                    scratch.append('\f');
                    break;
                case 'n':
                    scratch.append('\n');
                    break;
                case 'r':
                    scratch.append('\r');
                    break;
                case 't':
                    scratch.append('\t');
                    break;
                case 'u':
                    int code = 0;
                    for (int i = 0; i < 4; i++)
                    {
                        int h = readByte(in);
                        code = (code << 4) | hexDigit(h);
                    }
                    scratch.append((char) code);
                    break;
                default:
                    throw new JsonParsingException("Invalid escape: \\" + describe(esc), null);
                }
            }
            else if (c < 0x20)
            {
                throw new JsonParsingException("Unescaped control character in string: " + describe(c), null);
            }
            else if (c < 0x80)
            {
                scratch.append((char) c);
            }
            else
            {
                // UTF-8 multi-byte; preserve as code points
                int codePoint = decodeUtf8(in, c);
                scratch.appendCodePoint(codePoint);
            }
        }
    }

    private int decodeUtf8(
        InputStream in,
        int first) throws IOException
    {
        int remaining;
        int code;
        if ((first & 0xe0) == 0xc0)
        {
            code = first & 0x1f;
            remaining = 1;
        }
        else if ((first & 0xf0) == 0xe0)
        {
            code = first & 0x0f;
            remaining = 2;
        }
        else if ((first & 0xf8) == 0xf0)
        {
            code = first & 0x07;
            remaining = 3;
        }
        else
        {
            throw new JsonParsingException("Invalid UTF-8 lead byte: " + describe(first), null);
        }
        for (int i = 0; i < remaining; i++)
        {
            int cont = readByte(in);
            if ((cont & 0xc0) != 0x80)
            {
                throw new JsonParsingException("Invalid UTF-8 continuation: " + describe(cont), null);
            }
            code = (code << 6) | (cont & 0x3f);
        }
        return code;
    }

    private int hexDigit(
        int c)
    {
        if (c >= '0' && c <= '9')
        {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f')
        {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F')
        {
            return c - 'A' + 10;
        }
        throw new JsonParsingException("Invalid hex digit: " + describe(c), null);
    }

    private String parseNumberContent(
        InputStream in,
        int first) throws IOException
    {
        scratch.setLength(0);
        scratch.append((char) first);
        while (true)
        {
            int c = in.read();
            if (c == -1)
            {
                throw new EOFException();
            }
            if (c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')
            {
                attemptConsumed++;
                scratch.append((char) c);
            }
            else
            {
                // rewind to just after the last digit using the advance-entry mark
                in.reset();
                long remaining = attemptConsumed;
                while (remaining > 0)
                {
                    final long skipped = in.skip(remaining);
                    if (skipped <= 0)
                    {
                        throw new IOException("Unable to skip " + remaining + " bytes after reset");
                    }
                    remaining -= skipped;
                }
                return scratch.toString();
            }
        }
    }

    private int readByte(
        InputStream in) throws IOException
    {
        int c = in.read();
        if (c == -1)
        {
            throw new EOFException();
        }
        attemptConsumed++;
        return c;
    }

    private static String describe(
        int c)
    {
        if (c < 0)
        {
            return "<EOF>";
        }
        if (c >= ' ' && c < 0x7f)
        {
            return "'" + (char) c + "'";
        }
        return String.format("0x%02x", c);
    }
}
