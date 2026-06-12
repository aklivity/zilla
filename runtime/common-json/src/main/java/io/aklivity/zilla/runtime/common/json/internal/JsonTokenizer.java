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
import java.util.Optional;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

public final class JsonTokenizer
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
    private final boolean pathFiltering;
    private final int tokenMaxBytes;
    private final boolean terminalEof;

    // path tracking — pre-allocated, no per-event allocation
    private final boolean[] pathInArray = new boolean[MAX_DEPTH];
    // pathKey holds the key String only when path filtering is configured (the filter compares keys
    // eagerly); pathKeyChars mirrors it allocation-free for currentPath() in the deferred (no-filter)
    // mode, where keys are not materialized into Strings.
    private final String[] pathKey = new String[MAX_DEPTH];
    private final StringBuilder[] pathKeyChars = new StringBuilder[MAX_DEPTH];
    private final boolean[] pathKeySet = new boolean[MAX_DEPTH];
    private final int[] pathIndex = new int[MAX_DEPTH];
    private int pathDepth;

    private ParseState state = ParseState.DOC_START;
    private JsonParser.Event pendingEvent;
    private String pendingString;
    private boolean valuePending;
    private long streamOffset;
    private long valueStreamStart;
    private long valueStreamEnd;
    private boolean valueReadable = true;
    // set while a segment scan is in progress: a value-string is then streamed across frames as raw
    // bytes (no rewind to require it whole-in-frame, no decoded retention) rather than buffered whole.
    private boolean segmenting;

    // scalar resume state (valid when resumeOp != NONE)
    private ResumeOp resumeOp = ResumeOp.NONE;
    private boolean resumeEscape;
    private int resumeUnicodePending;   // hex digits remaining for backslash-u escape
    private int resumeUnicodeValue;
    private int resumeLiteralIndex;     // chars matched so far for true/false/null

    public JsonTokenizer()
    {
        this(null, null, Integer.MAX_VALUE);
    }

    public JsonTokenizer(
        List<String> pathIncludes,
        List<String> pathExcludes,
        int tokenMaxBytes)
    {
        this(pathIncludes, pathExcludes, tokenMaxBytes, false);
    }

    // terminalEof distinguishes a one-shot stream (EOF is the final delimiter) from the chunked
    // wrap()/feed model (EOF marks a frame boundary with more bytes possibly still to come). It
    // only matters for numbers, which unlike strings and the true/false/null literals are not
    // self-terminating and need a following non-digit byte to know they have ended.
    public JsonTokenizer(
        List<String> pathIncludes,
        List<String> pathExcludes,
        int tokenMaxBytes,
        boolean terminalEof)
    {
        this.pathIncludes = compilePaths(pathIncludes);
        this.pathExcludes = compilePaths(pathExcludes);
        this.pathFiltering = this.pathIncludes != null || this.pathExcludes != null;
        this.tokenMaxBytes = tokenMaxBytes;
        this.terminalEof = terminalEof;
        for (int i = 0; i < MAX_DEPTH; i++)
        {
            pathKeyChars[i] = new StringBuilder();
        }
    }

    public void reset()
    {
        stack.clear();
        scratch.setLength(0);
        pathDepth = 0;
        state = ParseState.DOC_START;
        pendingEvent = null;
        pendingString = null;
        valuePending = false;
        streamOffset = 0;
        valueReadable = true;
        segmenting = false;
        resumeOp = ResumeOp.NONE;
        resumeEscape = false;
        resumeUnicodePending = 0;
        resumeUnicodeValue = 0;
        resumeLiteralIndex = 0;
    }

    // Tracks whether a segment scan is in progress so a value-string spanning frames is streamed as raw
    // bytes instead of rewound (which would require it whole in one frame) or retained whole in scratch.
    void segmenting(
        boolean segmenting)
    {
        this.segmenting = segmenting;
    }

    public static final List<String> INCLUDE_ALL = null;

    private static List<String[]> compilePaths(
        List<String> paths)
    {
        return Optional.ofNullable(paths)
            .map(JsonTokenizer::compilePathList)
            .orElse(null);
    }

    private static List<String[]> compilePathList(
        List<String> paths)
    {
        return paths.isEmpty()
            ? List.of()
            : paths.stream().map(JsonTokenizer::compilePath).toList();
    }

    private static String[] compilePath(
        String path)
    {
        if (path.isEmpty())
        {
            return new String[0];
        }
        // RFC 6901: leading '/', segments separated by '/', '~1' decodes to '/', '~0' to '~'
        final String[] parts = path.substring(1).split("/", -1);
        for (int i = 0; i < parts.length; i++)
        {
            parts[i] = parts[i].replace("~1", "/").replace("~0", "~");
        }
        return parts;
    }

    public boolean advance(
        InputStream in) throws IOException
    {
        if (state == ParseState.DOC_DONE)
        {
            if (terminalEof)
            {
                enforceEndOfInput(in);
            }
            return false;
        }

        pendingEvent = null;
        pendingString = null;
        valuePending = false;

        while (pendingEvent == null && state != ParseState.DOC_DONE)
        {
            // Snapshot at the start of each iteration. On EOF mid-readable-scalar-scan we rewind
            // to drop any partial scratch and let the caller retry once more bytes are appended.
            // For other parse steps (KEY_NAME, structural separators, non-readable values) the
            // existing internal-resume state carries across frames without needing rewind.
            final long iterStart = streamOffset;
            in.mark(tokenMaxBytes == Integer.MAX_VALUE ? Integer.MAX_VALUE : tokenMaxBytes);

            try
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
            catch (EOFException ex)
            {
                if (pendingEvent != null)
                {
                    return true;
                }
                // While segmenting, a value-string spanning the frame is not rewound — its resume state
                // is kept so the next frame continues it and the raw bytes stream as segments.
                final boolean midReadableScalarScan =
                    (resumeOp == ResumeOp.VALUE_STRING || resumeOp == ResumeOp.VALUE_NUMBER) &&
                    valueReadable && !segmenting;
                if (midReadableScalarScan)
                {
                    in.reset();
                    streamOffset = iterStart;
                    scratch.setLength(0);
                    resumeOp = ResumeOp.NONE;
                    resumeEscape = false;
                    resumeUnicodePending = 0;
                    resumeUnicodeValue = 0;
                }
                return false;
            }
        }
        return true;
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
        if (valuePending)
        {
            pendingString = takeScratch();
            valuePending = false;
        }
        return pendingString;
    }

    // Non-allocating key view, valid while positioned on a deferred KEY_NAME (the unescaped key chars
    // are still in scratch because nobody has materialized them yet). Lets a downstream stage copy or
    // compare the key without a String; returns the materialized value if it was already taken.
    public CharSequence key()
    {
        return valuePending ? scratch : pendingString;
    }

    public long streamOffset()
    {
        return streamOffset;
    }

    // Stream-offset span of the most recent VALUE_STRING token, including its surrounding quotes. The
    // EOF rewind for a readable scalar guarantees the whole token is contiguous in one frame, so this
    // span maps to a single contiguous slice the sink can splice or fragment.
    public long valueStreamStart()
    {
        return valueStreamStart;
    }

    public long valueStreamEnd()
    {
        return valueStreamEnd;
    }

    public boolean done()
    {
        return state == ParseState.DOC_DONE;
    }

    public boolean inObjectContext()
    {
        return pathDepth > 0 && !pathInArray[pathDepth - 1];
    }

    public boolean inArrayContext()
    {
        return pathDepth > 0 && pathInArray[pathDepth - 1];
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
            else if (pathKeySet[i])
            {
                path.append(pathKeyChars[i].toString().replace("~", "~0").replace("/", "~1"));
            }
        }
        return path.toString();
    }

    private boolean currentPathReadable()
    {
        // null pathIncludes means include all; empty list means include none;
        // non-empty list restricts to matching paths
        final boolean included = pathIncludes == null || pathMatchesAny(pathIncludes);
        // excludes have final veto
        return included && (pathExcludes == null || !pathMatchesAny(pathExcludes));
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
        pathKeySet[pathDepth] = false;
        pathIndex[pathDepth] = 0;
        pathDepth++;
    }

    private void popPath()
    {
        pathDepth--;
        pathKey[pathDepth] = null;
        pathKeySet[pathDepth] = false;
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
            captureKey();
            resumeOp = ResumeOp.NONE;
            state = ParseState.OBJ_AFTER_KEY;
            break;
        case VALUE_STRING:
            continueStringContent(in);
            pendingEvent = JsonParser.Event.VALUE_STRING;
            captureValue(valueReadable);
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
            break;
        case VALUE_NUMBER:
            continueNumberContent(in);
            pendingEvent = JsonParser.Event.VALUE_NUMBER;
            captureValue(valueReadable);
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

    private void captureValue(
        boolean readable)
    {
        valuePending = readable;
        pendingString = null;
    }

    private String takeScratch()
    {
        String path = scratch.toString();
        scratch.setLength(0);
        return path;
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
        valueReadable = currentPathReadable();
        switch (c)
        {
        case '{':
            pendingEvent = JsonParser.Event.START_OBJECT;
            pushAndEnter(ParseState.OBJ_AFTER_OPEN);
            pushPath(false);
            break;
        case '[':
            pendingEvent = JsonParser.Event.START_ARRAY;
            pushAndEnter(ParseState.ARR_AFTER_OPEN);
            pushPath(true);
            break;
        case '"':
            valueStreamStart = streamOffset - 1;
            scratch.setLength(0);
            resumeEscape = false;
            resumeUnicodePending = 0;
            resumeOp = ResumeOp.VALUE_STRING;
            continueStringContent(in);
            valueStreamEnd = streamOffset;
            pendingEvent = JsonParser.Event.VALUE_STRING;
            captureValue(valueReadable);
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
                if (valueReadable)
                {
                    scratch.append((char) c);
                }
                resumeOp = ResumeOp.VALUE_NUMBER;
                continueNumberContent(in);
                pendingEvent = JsonParser.Event.VALUE_NUMBER;
                captureValue(valueReadable);
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
        // keys must always be readable so path matching can compare them
        valueReadable = true;
        scratch.setLength(0);
        resumeEscape = false;
        resumeUnicodePending = 0;
        resumeOp = ResumeOp.KEY_STRING;
        continueStringContent(in);
        pendingEvent = JsonParser.Event.KEY_NAME;
        captureKey();
        resumeOp = ResumeOp.NONE;
        state = ParseState.OBJ_AFTER_KEY;
    }

    // Keys are normally deferred (left in scratch, materialized lazily by stringValue()), so a key
    // that is never read by a downstream stage allocates no String. When the tokenizer is itself
    // configured with a path filter it must compare keys eagerly via pathKey[], so in that mode the
    // key is materialized up front and the per-depth pathKey slot is set.
    private void captureKey()
    {
        if (pathFiltering)
        {
            pendingString = takeScratch();
            valuePending = false;
            if (pathDepth > 0 && !pathInArray[pathDepth - 1])
            {
                pathKey[pathDepth - 1] = pendingString;
            }
        }
        else
        {
            valuePending = true;
            pendingString = null;
            if (pathDepth > 0 && !pathInArray[pathDepth - 1])
            {
                final StringBuilder keyChars = pathKeyChars[pathDepth - 1];
                keyChars.setLength(0);
                keyChars.append(scratch);
                pathKeySet[pathDepth - 1] = true;
            }
        }
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
        markValueConsumed();
    }

    private void emitEnd(
        JsonParser.Event endEvent)
    {
        pendingEvent = endEvent;
        popPath();
        if (stack.isEmpty())
        {
            state = ParseState.DOC_DONE;
        }
        else
        {
            state = stack.pop();
        }
        markValueConsumed();
    }

    // After a complete top-level value on a one-shot stream, only insignificant whitespace may
    // remain; any further token is invalid per RFC 8259. Chunked frame sources skip this check.
    private void enforceEndOfInput(
        InputStream in) throws IOException
    {
        try
        {
            int c = skipWhitespace(in);
            throw new JsonParsingException("Unexpected trailing content: " + describe(c), null);
        }
        catch (EOFException ex)
        {
            // clean end of input
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
                    appendScratch((char) resumeUnicodeValue);
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
                    appendScratch((char) c);
                    break;
                case 'b':
                    appendScratch('\b');
                    break;
                case 'f':
                    appendScratch('\f');
                    break;
                case 'n':
                    appendScratch('\n');
                    break;
                case 'r':
                    appendScratch('\r');
                    break;
                case 't':
                    appendScratch('\t');
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
                appendScratch((char) c);
            }
            else
            {
                int codePoint = decodeUtf8(in, c);
                appendCodePointScratch(codePoint);
            }
        }
    }

    private void appendScratch(
        char c)
    {
        if (valueReadable && !streamingValue())
        {
            scratch.append(c);
        }
    }

    private void appendCodePointScratch(
        int codePoint)
    {
        if (valueReadable && !streamingValue())
        {
            scratch.appendCodePoint(codePoint);
        }
    }

    // A value-string being streamed as raw segment bytes is not retained in scratch; keys and numbers
    // still retain (keys for path matching, numbers for grammar validation).
    private boolean streamingValue()
    {
        return segmenting && resumeOp == ResumeOp.VALUE_STRING;
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
                if (terminalEof)
                {
                    validateNumber();
                    return;
                }
                throw new EOFException();
            }
            if (c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')
            {
                streamOffset++;
                appendScratch((char) c);
            }
            else
            {
                in.reset();
                validateNumber();
                return;
            }
        }
    }

    // RFC 8259 number grammar: -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?. Only enforced for
    // readable values, where scratch holds the complete lexeme; filtered-out values are discarded.
    private void validateNumber()
    {
        if (valueReadable)
        {
            final int length = scratch.length();
            int index = 0;
            boolean valid = length > 0;
            if (valid && scratch.charAt(index) == '-')
            {
                index++;
            }
            final int intStart = index;
            if (index < length && scratch.charAt(index) == '0')
            {
                index++;
            }
            else
            {
                while (index < length && isDigit(scratch.charAt(index)))
                {
                    index++;
                }
            }
            valid &= index > intStart;
            if (valid && index < length && scratch.charAt(index) == '.')
            {
                index++;
                final int fracStart = index;
                while (index < length && isDigit(scratch.charAt(index)))
                {
                    index++;
                }
                valid &= index > fracStart;
            }
            if (valid && index < length && (scratch.charAt(index) == 'e' || scratch.charAt(index) == 'E'))
            {
                index++;
                if (index < length && (scratch.charAt(index) == '+' || scratch.charAt(index) == '-'))
                {
                    index++;
                }
                final int expStart = index;
                while (index < length && isDigit(scratch.charAt(index)))
                {
                    index++;
                }
                valid &= index > expStart;
            }
            valid &= index == length;
            if (!valid)
            {
                throw new JsonParsingException("Invalid JSON number: " + scratch, null);
            }
        }
    }

    private static boolean isDigit(
        char c)
    {
        return c >= '0' && c <= '9';
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
        if (valueReadable &&
            (resumeOp == ResumeOp.VALUE_STRING || resumeOp == ResumeOp.VALUE_NUMBER) &&
            scratch.length() >= tokenMaxBytes)
        {
            throw new JsonParsingException(
                "value at " + currentPath() + " exceeds max " + tokenMaxBytes + " bytes",
                null);
        }
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
