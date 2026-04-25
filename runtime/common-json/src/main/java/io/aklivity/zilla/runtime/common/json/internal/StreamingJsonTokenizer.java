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
package io.aklivity.zilla.runtime.common.json.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import jakarta.json.JsonPointer;
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
        OBJ_AFTER_COLON,
        OBJ_AFTER_VALUE,
        ARR_AFTER_OPEN,
        ARR_AFTER_COMMA,
        ARR_AFTER_VALUE
    }

    private enum ResumeOp
    {
        NONE,
        KEY_STRING,
        VALUE_STRING,
        VALUE_NUMBER,
        VALUE_TRUE,
        VALUE_FALSE,
        VALUE_NULL
    }

    private static final int MAX_DEPTH = 64;
    private static final String WILDCARD_INDEX = "-";

    private final Deque<ParseState> stack = new ArrayDeque<>();
    private final StringBuilder scratch = new StringBuilder();
    private final List<String[]> pathIncludes;
    private final List<String[]> pathExcludes;
    private final int tokenMaxBytes;

    // path tracking — pre-allocated, no per-event allocation
    private final boolean[] pathInArray = new boolean[MAX_DEPTH];
    private final String[] pathKey = new String[MAX_DEPTH];
    private final int[] pathIndex = new int[MAX_DEPTH];
    private int pathDepth;

    private ParseState state = ParseState.DOC_START;
    private JsonParser.Event pendingEvent;
    private String pendingString;
    private long streamOffset;
    private boolean valueReadable = true;

    // scalar resume state (valid when resumeOp != NONE)
    private ResumeOp resumeOp = ResumeOp.NONE;
    private boolean resumeEscape;
    private int resumeUnicodePending;   // hex digits remaining for backslash-u escape
    private int resumeUnicodeValue;
    private int resumeLiteralIndex;     // chars matched so far for true/false/null

    public StreamingJsonTokenizer()
    {
        this(List.of(), List.of(), Integer.MAX_VALUE);
    }

    public StreamingJsonTokenizer(
        List<JsonPointer> pathIncludes,
        List<JsonPointer> pathExcludes,
        int tokenMaxBytes)
    {
        this.pathIncludes = compilePaths(pathIncludes);
        this.pathExcludes = compilePaths(pathExcludes);
        this.tokenMaxBytes = tokenMaxBytes;
    }

    private static List<String[]> compilePaths(
        List<JsonPointer> paths)
    {
        if (paths.isEmpty())
        {
            return List.of();
        }
        final List<String[]> compiled = new java.util.ArrayList<>(paths.size());
        for (JsonPointer p : paths)
        {
            final String s = p.toString();
            if (s.isEmpty())
            {
                compiled.add(new String[0]);
            }
            else
            {
                // RFC 6901: leading '/', segments separated by '/', '~1' decodes to '/', '~0' to '~'
                final String[] parts = s.substring(1).split("/", -1);
                for (int i = 0; i < parts.length; i++)
                {
                    parts[i] = parts[i].replace("~1", "/").replace("~0", "~");
                }
                compiled.add(parts);
            }
        }
        return compiled;
    }

    public boolean advance(
        InputStream in) throws IOException
    {
        if (state == ParseState.DOC_DONE)
        {
            return false;
        }

        pendingEvent = null;
        pendingString = null;

        try
        {
            while (pendingEvent == null && state != ParseState.DOC_DONE)
            {
                if (resumeOp != ResumeOp.NONE)
                {
                    resumeScan(in);
                }
                else
                {
                    advanceOne(in);
                }
            }
            return true;
        }
        catch (EOFException ex)
        {
            return pendingEvent != null;
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

    public boolean valueReadable()
    {
        return valueReadable;
    }

    public String currentPath()
    {
        if (pathDepth == 0)
        {
            return "";
        }
        final StringBuilder path = new StringBuilder();
        for (int i = 0; i < pathDepth; i++)
        {
            path.append('/');
            if (pathInArray[i])
            {
                path.append(pathIndex[i]);
            }
            else if (pathKey[i] != null)
            {
                path.append(pathKey[i].replace("~", "~0").replace("/", "~1"));
            }
        }
        return path.toString();
    }

    private boolean currentPathReadable()
    {
        // include is everything by default; if pathIncludes is specified, restrict to those
        final boolean included = pathIncludes.isEmpty() || pathMatchesAny(pathIncludes);
        // excludes have final veto
        return included && !pathMatchesAny(pathExcludes);
    }

    private boolean pathMatchesAny(
        List<String[]> paths)
    {
        outer:
        for (String[] target : paths)
        {
            if (target.length != pathDepth)
            {
                continue;
            }
            for (int i = 0; i < pathDepth; i++)
            {
                final String segment = pathInArray[i]
                    ? Integer.toString(pathIndex[i])
                    : pathKey[i];
                final String expected = target[i];
                if (!WILDCARD_INDEX.equals(expected) && !expected.equals(segment))
                {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private void pushPath(
        boolean inArray)
    {
        if (pathDepth == MAX_DEPTH)
        {
            throw new JsonParsingException("JSON depth exceeds " + MAX_DEPTH, null);
        }
        pathInArray[pathDepth] = inArray;
        pathKey[pathDepth] = null;
        pathIndex[pathDepth] = 0;
        pathDepth++;
    }

    private void popPath()
    {
        pathDepth--;
        pathKey[pathDepth] = null;
    }

    private void markValueConsumed()
    {
        if (pathDepth > 0 && pathInArray[pathDepth - 1])
        {
            pathIndex[pathDepth - 1]++;
        }
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
            consumeColon(in);
            break;
        case OBJ_AFTER_COLON:
            consumeValue(in);
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

    private void resumeScan(
        InputStream in) throws IOException
    {
        switch (resumeOp)
        {
        case KEY_STRING:
            continueStringContent(in);
            pendingEvent = JsonParser.Event.KEY_NAME;
            pendingString = takeScratch();
            resumeOp = ResumeOp.NONE;
            state = ParseState.OBJ_AFTER_KEY;
            break;
        case VALUE_STRING:
            continueStringContent(in);
            pendingEvent = JsonParser.Event.VALUE_STRING;
            pendingString = takeScratch();
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case VALUE_NUMBER:
            continueNumberContent(in);
            pendingEvent = JsonParser.Event.VALUE_NUMBER;
            pendingString = takeScratch();
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case VALUE_TRUE:
            continueLiteral(in, "true");
            pendingEvent = JsonParser.Event.VALUE_TRUE;
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case VALUE_FALSE:
            continueLiteral(in, "false");
            pendingEvent = JsonParser.Event.VALUE_FALSE;
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case VALUE_NULL:
            continueLiteral(in, "null");
            pendingEvent = JsonParser.Event.VALUE_NULL;
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        default:
            throw new IllegalStateException("Unexpected resumeOp: " + resumeOp);
        }
    }

    private String takeScratch()
    {
        String s = scratch.toString();
        scratch.setLength(0);
        return s;
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

    private void consumeColon(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (c != ':')
        {
            throw new JsonParsingException("Expected ':' but got: " + describe(c), null);
        }
        state = ParseState.OBJ_AFTER_COLON;
    }

    private void consumeSeparatorOrEnd(
        InputStream in,
        boolean inObject) throws IOException
    {
        int c = skipWhitespace(in);
        if (c == ',')
        {
            state = inObject ? ParseState.OBJ_AFTER_COMMA : ParseState.ARR_AFTER_COMMA;
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
            scratch.setLength(0);
            resumeEscape = false;
            resumeUnicodePending = 0;
            resumeOp = ResumeOp.VALUE_STRING;
            continueStringContent(in);
            pendingEvent = JsonParser.Event.VALUE_STRING;
            pendingString = takeScratch();
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case 't':
            resumeLiteralIndex = 1;
            resumeOp = ResumeOp.VALUE_TRUE;
            continueLiteral(in, "true");
            pendingEvent = JsonParser.Event.VALUE_TRUE;
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case 'f':
            resumeLiteralIndex = 1;
            resumeOp = ResumeOp.VALUE_FALSE;
            continueLiteral(in, "false");
            pendingEvent = JsonParser.Event.VALUE_FALSE;
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case 'n':
            resumeLiteralIndex = 1;
            resumeOp = ResumeOp.VALUE_NULL;
            continueLiteral(in, "null");
            pendingEvent = JsonParser.Event.VALUE_NULL;
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        default:
            if (c == '-' || c >= '0' && c <= '9')
            {
                scratch.setLength(0);
                scratch.append((char) c);
                resumeOp = ResumeOp.VALUE_NUMBER;
                continueNumberContent(in);
                pendingEvent = JsonParser.Event.VALUE_NUMBER;
                pendingString = takeScratch();
                resumeOp = ResumeOp.NONE;
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
        scratch.setLength(0);
        resumeEscape = false;
        resumeUnicodePending = 0;
        resumeOp = ResumeOp.KEY_STRING;
        continueStringContent(in);
        pendingEvent = JsonParser.Event.KEY_NAME;
        pendingString = takeScratch();
        resumeOp = ResumeOp.NONE;
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
        case OBJ_AFTER_COLON:
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
        switch (state)
        {
        case DOC_START:
            state = ParseState.DOC_DONE;
            break;
        case OBJ_AFTER_COLON:
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

    private void continueLiteral(
        InputStream in,
        String expected) throws IOException
    {
        while (resumeLiteralIndex < expected.length())
        {
            int c = readByte(in);
            if (c != expected.charAt(resumeLiteralIndex))
            {
                throw new JsonParsingException("Unexpected character in literal: " + describe(c), null);
            }
            resumeLiteralIndex++;
        }
    }

    private void continueStringContent(
        InputStream in) throws IOException
    {
        while (true)
        {
            int c = readByte(in);
            if (resumeUnicodePending > 0)
            {
                resumeUnicodeValue = (resumeUnicodeValue << 4) | hexDigit(c);
                resumeUnicodePending--;
                if (resumeUnicodePending == 0)
                {
                    scratch.append((char) resumeUnicodeValue);
                }
                continue;
            }
            if (resumeEscape)
            {
                resumeEscape = false;
                switch (c)
                {
                case '"':
                case '\\':
                case '/':
                    scratch.append((char) c);
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
                    resumeUnicodePending = 4;
                    resumeUnicodeValue = 0;
                    break;
                default:
                    throw new JsonParsingException("Invalid escape: \\" + describe(c), null);
                }
                continue;
            }
            if (c == '"')
            {
                return;
            }
            else if (c == '\\')
            {
                resumeEscape = true;
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

    private void continueNumberContent(
        InputStream in) throws IOException
    {
        while (true)
        {
            in.mark(1);
            int c = in.read();
            if (c == -1)
            {
                throw new EOFException();
            }
            if (c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')
            {
                streamOffset++;
                scratch.append((char) c);
            }
            else
            {
                in.reset();
                return;
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
        streamOffset++;
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
