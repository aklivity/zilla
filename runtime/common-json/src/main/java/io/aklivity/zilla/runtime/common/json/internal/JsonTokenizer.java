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
    // upper bound on the bytes of a single decoded unit (a UTF-8 char is at most 4 bytes, a
    // backslash-u escape is 6); the fragment-boundary mark rewinds across at most one such unit
    private static final int MAX_UNIT_BYTES = 8;

    private final Deque<ParseState> stack = new ArrayDeque<>();
    private final StringBuilder scratch = new StringBuilder();
    private final int tokenMaxBytes;
    private boolean terminalEof;

    // path tracking — pre-allocated, no per-event allocation
    private final boolean[] pathInArray = new boolean[MAX_DEPTH];
    // pathKeyChars mirrors each object key allocation-free; path matching and currentPath() compare
    // against it as a CharSequence so keys are never materialized into Strings just to track the path.
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
    // raw stream offset where the current value-string fragment began; getSegment() of a fragmented
    // value returns [fragmentStart, valueStreamEnd] so each fragment's verbatim bytes splice back to
    // the whole token (fragment 1 includes the opening quote, the final fragment the closing quote)
    private long fragmentStart;
    // set while a segment scan is in progress: a value-string is then streamed across frames as raw
    // bytes (no rewind to require it whole-in-frame, no decoded retention) rather than buffered whole.
    private boolean segmenting;
    // set while a value-string larger than tokenMaxBytes is being delivered as a sequence of
    // fragments: each fragment carries the decoded chars that fit the slot, deferredBytes() stays true
    // until the closing quote. A partial char/escape at a frame boundary is left unconsumed (rewound
    // to unitStartOffset) for the caller's decode slot to re-present, so no partial state crosses wrap.
    private boolean fragmenting;
    private boolean stringComplete;
    private long unitStartOffset;

    // scalar resume state (valid when resumeOp != NONE)
    private ResumeOp resumeOp = ResumeOp.NONE;
    private boolean resumeEscape;
    private int resumeUnicodePending;   // hex digits remaining for backslash-u escape
    private int resumeUnicodeValue;
    private int resumeLiteralIndex;     // chars matched so far for true/false/null

    public JsonTokenizer()
    {
        this(Integer.MAX_VALUE);
    }

    public JsonTokenizer(
        int tokenMaxBytes)
    {
        this(tokenMaxBytes, false);
    }

    // terminalEof distinguishes a one-shot stream (EOF is the final delimiter) from the chunked
    // wrap()/feed model (EOF marks a frame boundary with more bytes possibly still to come). It
    // only matters for numbers, which unlike strings and the true/false/null literals are not
    // self-terminating and need a following non-digit byte to know they have ended.
    public JsonTokenizer(
        int tokenMaxBytes,
        boolean terminalEof)
    {
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
        fragmentStart = 0;
        segmenting = false;
        fragmenting = false;
        stringComplete = false;
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

    // Set per input window: when true this window's EOF is the terminal delimiter (one-shot or final
    // window), so a trailing scalar completes at EOF and an incomplete value is rejected; when false EOF
    // is a frame boundary with more bytes still to come.
    void terminal(
        boolean terminalEof)
    {
        this.terminalEof = terminalEof;
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
                final boolean midScalar =
                    (resumeOp == ResumeOp.VALUE_STRING || resumeOp == ResumeOp.VALUE_NUMBER) && !segmenting;
                boolean delivered = false;
                if (midScalar && fragmenting && resumeOp == ResumeOp.VALUE_STRING)
                {
                    // mid-fragmentation: ship the chars accumulated so far and leave the partial unit's
                    // bytes unconsumed (rewound to its start) for the decode slot to re-present.
                    in.reset();
                    streamOffset = unitStartOffset;
                    resumeEscape = false;
                    resumeUnicodePending = 0;
                    resumeUnicodeValue = 0;
                    if (scratch.length() > 0)
                    {
                        valueStreamStart = fragmentStart;
                        valueStreamEnd = streamOffset;
                        pendingEvent = JsonParser.Event.VALUE_STRING;
                        pendingString = takeScratch();
                        valuePending = false;
                        delivered = true;
                    }
                }
                else if (midScalar)
                {
                    in.reset();
                    streamOffset = iterStart;
                    scratch.setLength(0);
                    resumeOp = ResumeOp.NONE;
                    resumeEscape = false;
                    resumeUnicodePending = 0;
                    resumeUnicodeValue = 0;
                }
                return delivered;
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

    // Non-allocating view of the current string token (a deferred KEY_NAME or a readable VALUE_STRING),
    // valid while the unescaped chars are still in scratch because nobody has materialized them yet. Lets
    // a downstream stage copy or compare the chars without a String; returns the materialized value if it
    // was already taken.
    public CharSequence stringView()
    {
        return valuePending ? scratch : pendingString;
    }

    public long streamOffset()
    {
        return streamOffset;
    }

    // Stream-offset span of the most recent readable scalar token (a VALUE_STRING including its
    // surrounding quotes, or a VALUE_NUMBER lexeme). The EOF rewind for a readable scalar guarantees the
    // whole token is contiguous in one frame, so this span maps to a single contiguous slice the sink can
    // splice or fragment.
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

    // True while a value-string is being delivered in fragments and more fragments follow the current
    // event; drives the parser's deferredBytes() for over-slot scalars.
    public boolean fragmenting()
    {
        return fragmenting;
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
            else if (pathKeySet[i])
            {
                path.append(pathKeyChars[i].toString().replace("~", "~0").replace("/", "~1"));
            }
        }
        return path.toString();
    }

    private void pushPath(
        boolean inArray)
    {
        if (pathDepth == MAX_DEPTH)
        {
            throw new JsonParsingException("JSON depth exceeds " + MAX_DEPTH, null);
        }
        pathInArray[pathDepth] = inArray;
        pathKeySet[pathDepth] = false;
        pathIndex[pathDepth] = 0;
        pathDepth++;
    }

    private void popPath()
    {
        pathDepth--;
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
            fragmentStart = streamOffset;
            continueStringContent(in);
            finishStringValue();
            break;
        case VALUE_NUMBER:
            continueNumberContent(in);
            pendingEvent = JsonParser.Event.VALUE_NUMBER;
            captureValue();
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

    private void captureValue()
    {
        valuePending = true;
        pendingString = null;
    }

    // Completes a VALUE_STRING scan: a finished string (closing quote seen) is captured lazily and the
    // parse state advances; a slot-bound fragment is materialized eagerly (freeing scratch for the next
    // fragment) with the value left in progress so the next advance() continues it.
    private void finishStringValue()
    {
        valueStreamStart = fragmentStart;
        valueStreamEnd = streamOffset;
        pendingEvent = JsonParser.Event.VALUE_STRING;
        if (stringComplete)
        {
            captureValue();
            fragmenting = false;
            resumeOp = ResumeOp.NONE;
            afterValueConsumed();
        }
        else
        {
            pendingString = takeScratch();
            valuePending = false;
            fragmenting = true;
        }
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
            fragmentStart = streamOffset - 1;
            scratch.setLength(0);
            resumeEscape = false;
            resumeUnicodePending = 0;
            resumeOp = ResumeOp.VALUE_STRING;
            continueStringContent(in);
            finishStringValue();
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
                valueStreamStart = streamOffset - 1;
                scratch.setLength(0);
                scratch.append((char) c);
                resumeOp = ResumeOp.VALUE_NUMBER;
                continueNumberContent(in);
                valueStreamEnd = streamOffset;
                pendingEvent = JsonParser.Event.VALUE_NUMBER;
                captureValue();
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
        captureKey();
        resumeOp = ResumeOp.NONE;
        state = ParseState.OBJ_AFTER_KEY;
    }

    // Keys are always deferred (left in scratch, materialized lazily by stringValue()), so a key that
    // is never read by a downstream stage allocates no String. The chars are mirrored into the
    // per-depth pathKeyChars slot so path matching and currentPath() can compare them as a
    // CharSequence without materializing a String — even when path filtering is configured.
    private void captureKey()
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
        stringComplete = false;
        while (true)
        {
            // While fragmenting, mark each unit boundary so an EOF mid-char/escape rewinds here: the
            // chars decoded so far ship as a fragment and the partial unit's bytes stay unconsumed for
            // the decode slot to re-present on the next frame.
            if (fragmenting && !resumeEscape && resumeUnicodePending == 0)
            {
                in.mark(MAX_UNIT_BYTES);
                unitStartOffset = streamOffset;
            }
            int c = readByte(in);
            if (resumeUnicodePending > 0)
            {
                resumeUnicodeValue = (resumeUnicodeValue << 4) | hexDigit(c);
                resumeUnicodePending--;
                if (resumeUnicodePending == 0)
                {
                    appendScratch((char) resumeUnicodeValue);
                }
            }
            else if (resumeEscape)
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
            }
            else if (c == '"')
            {
                stringComplete = true;
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
                appendCodePointScratch(decodeUtf8(in, c));
            }
            // suspend with a full fragment once a complete unit pushes scratch to the slot bound
            if (!resumeEscape && resumeUnicodePending == 0 &&
                tokenMaxBytes != Integer.MAX_VALUE && scratch.length() >= tokenMaxBytes)
            {
                return;
            }
        }
    }

    private void appendScratch(
        char c)
    {
        if (!streamingValue())
        {
            scratch.append(c);
        }
    }

    private void appendCodePointScratch(
        int codePoint)
    {
        if (!streamingValue())
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

    // RFC 8259 number grammar: -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?. scratch holds the
    // complete lexeme (numbers are always retained in scratch for validation and lazy materialization).
    private void validateNumber()
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
