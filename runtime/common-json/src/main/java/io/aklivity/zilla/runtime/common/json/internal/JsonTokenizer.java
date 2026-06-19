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

    // states of the RFC 8259 number grammar, advanced one char at a time by validateNumberChar
    private enum NumberState
    {
        START,
        SIGN,
        ZERO,
        INT,
        DOT,
        FRAC,
        EXP,
        EXP_SIGN,
        EXP_INT
    }

    private static final int MAX_DEPTH = 64;

    private final Deque<ParseState> stack = new ArrayDeque<>();
    private final StringBuilder scratch = new StringBuilder();
    // chars of the current scratch the consumer has taken; on resume only this prefix is dropped so the
    // unconsumed remainder accumulates, letting a consumer that declines fragments (consumed(0)) receive
    // the value whole. Set by the parser from its consumed cursor before each resuming advance.
    private int scratchConsumed;
    private boolean terminalEof;
    // byte length of the current input window; a value whose own bytes reach this length without
    // completing fills the window and is delivered as fragments rather than reassembled across windows.
    private int windowLength = Integer.MAX_VALUE;

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
    // 1-based line/column of the next byte to read; snapshots restore them when the scan rewinds the
    // stream offset to a value start (value*) or a complete-unit boundary (unit*) at a window edge
    private long line = 1;
    private long column = 1;
    private long valueLine = 1;
    private long valueColumn = 1;
    private long unitLine = 1;
    private long unitColumn = 1;
    // raw stream offset where the current value (string or number) fragment began; getSegment() of a
    // fragmented value returns [fragmentStart, valueStreamEnd] so each fragment's verbatim bytes splice
    // back to the whole token (fragment 1 includes the opening quote, the final fragment the closing one)
    private long fragmentStart;
    // set while a segment scan is in progress: a value-string is then streamed across frames as raw
    // bytes (no rewind to require it whole-in-frame, no decoded retention) rather than buffered whole.
    private boolean segmenting;
    // set while a kept scalar leaf value-string is delivered verbatim as raw fragments: suppresses decoded
    // retention and lets the value-string fragment across windows at unit boundaries
    private boolean scalarSegment;
    // set while a value-string that fills the input window is being delivered as a sequence of
    // fragments: each fragment carries the decoded chars scanned so far, deferredBytes() stays true
    // until the closing quote. A partial char/escape at a window boundary is left unconsumed (rewound
    // to unitStartOffset) for the caller to re-present, so no partial state crosses a wrap.
    private boolean fragmenting;
    private long unitStartOffset;
    // RFC 8259 number grammar validated incrementally as each char is scanned, so a number spanning
    // windows needs no retained whole lexeme to validate; the state persists across fragments.
    private NumberState numberState;

    // set at each VALUE_STRING delivery: true when the value-string was streamed verbatim as raw bytes
    // (a segment scan or an armed scalar leaf), so the caller splices its raw token bytes; false when it
    // was decoded into scratch, so the caller renders it canonically from the decoded chars.
    private boolean stringVerbatim;

    // set when a read hits the end of the current input window mid-token: the scan unwinds logically
    // (no exception) and advance() routes to onScalarStarved; reset at the top of each advance()
    private boolean starved;

    // scalar resume state (valid when resumeOp != NONE)
    private ResumeOp resumeOp = ResumeOp.NONE;
    private boolean resumeEscape;
    private int resumeUnicodePending;   // hex digits remaining for backslash-u escape
    private int resumeUnicodeValue;
    private int resumeLiteralIndex;     // chars matched so far for true/false/null

    public JsonTokenizer()
    {
        this(false);
    }

    // terminalEof distinguishes a one-shot stream (EOF is the final delimiter) from the chunked
    // wrap()/feed model (EOF marks a frame boundary with more bytes possibly still to come). It
    // only matters for numbers, which unlike strings and the true/false/null literals are not
    // self-terminating and need a following non-digit byte to know they have ended.
    public JsonTokenizer(
        boolean terminalEof)
    {
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
        scratchConsumed = 0;
        numberState = NumberState.START;
        stringVerbatim = false;
        pathDepth = 0;
        state = ParseState.DOC_START;
        pendingEvent = null;
        pendingString = null;
        valuePending = false;
        streamOffset = 0;
        line = 1;
        column = 1;
        valueLine = 1;
        valueColumn = 1;
        unitLine = 1;
        unitColumn = 1;
        fragmentStart = 0;
        segmenting = false;
        scalarSegment = false;
        fragmenting = false;
        starved = false;
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

    // Arms the next value-string to stream verbatim as raw fragments (no decoded retention).
    void scalarSegment(
        boolean scalarSegment)
    {
        this.scalarSegment = scalarSegment;
    }

    // Set per input window: when true this window's EOF is the terminal delimiter (one-shot or final
    // window), so a trailing scalar completes at EOF and an incomplete value is rejected; when false EOF
    // is a frame boundary with more bytes still to come.
    void terminal(
        boolean terminalEof)
    {
        this.terminalEof = terminalEof;
    }

    // Set per input window: its byte length is the fragmentation bound — a value whose own bytes reach
    // it without completing is delivered as fragments instead of reassembled across windows.
    void window(
        int length)
    {
        this.windowLength = length;
    }

    public boolean advance(
        InputStream in) throws IOException
    {
        starved = false;

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

        boolean produced = true;
        while (!starved && pendingEvent == null && state != ParseState.DOC_DONE)
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

        if (starved && pendingEvent == null)
        {
            produced = onScalarStarved();
        }
        return produced;
    }

    // The window was exhausted mid-scalar. A value-string whose own bytes fill the window (or one
    // already fragmenting) ships its decoded-so-far as a fragment, leaving the partial unit for the
    // caller to re-present via position(); a smaller value (or a number, or the terminal window) is
    // rewound to its start so the caller carries it whole into a fuller next window. Keys and
    // segmented value-strings keep their resume state and simply wait for more input. Returns true
    // iff a fragment event was produced.
    private boolean onScalarStarved()
    {
        final boolean midScalar =
            (resumeOp == ResumeOp.VALUE_STRING || resumeOp == ResumeOp.VALUE_NUMBER) && !segmenting;
        boolean delivered = false;
        if (midScalar)
        {
            final long valueBytes = streamOffset - valueStreamStart;
            // a kept scalar leaf fragments verbatim on any starve; a decoded value only once it fills the window
            final boolean fragment =
                !terminalEof && (scalarSegment || fragmenting || valueBytes >= windowLength);
            if (fragment && resumeOp == ResumeOp.VALUE_STRING)
            {
                // rewind to the last complete code-point/escape boundary, leaving the partial unit for the caller
                streamOffset = unitStartOffset;
                line = unitLine;
                column = unitColumn;
                resumeEscape = false;
                resumeUnicodePending = 0;
                resumeUnicodeValue = 0;
                fragmenting = true;
                // ship a fragment only when this round scanned new bytes; with consumed(0) accumulation the
                // retained remainder keeps scratch non-empty, so a non-empty scratch alone is not progress and
                // re-shipping it would spin the pump — require streamOffset to have advanced past fragmentStart
                if (streamOffset > fragmentStart)
                {
                    valueStreamStart = fragmentStart;
                    valueStreamEnd = streamOffset;
                    stringVerbatim = scalarSegment || segmenting;
                    pendingEvent = JsonParser.Event.VALUE_STRING;
                    // capture lazily: a verbatim/segmented consumer reads only getSegment() and never
                    // materializes the decoded chars, so leave them in scratch (cleared when the next
                    // fragment resumes) and let stringValue() take them on demand
                    captureValue();
                    delivered = true;
                }
            }
            else if (fragment)
            {
                // number: every digit is a complete unit so nothing is rewound; ship only when this round
                // scanned new bytes (consumed(0) accumulation keeps the rest in scratch, re-presented whole)
                fragmenting = true;
                if (streamOffset > fragmentStart)
                {
                    valueStreamStart = fragmentStart;
                    valueStreamEnd = streamOffset;
                    pendingEvent = JsonParser.Event.VALUE_NUMBER;
                    captureValue();
                    delivered = true;
                }
            }
            else
            {
                // value fits a window (or terminal): rewind to its start and reassemble whole next window
                streamOffset = valueStreamStart;
                line = valueLine;
                column = valueColumn;
                scratch.setLength(0);
                resumeOp = ResumeOp.NONE;
                resumeEscape = false;
                resumeUnicodePending = 0;
                resumeUnicodeValue = 0;
            }
        }
        return delivered;
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

    // The parser reports, before a resuming advance, how many chars of the current fragment the consumer
    // took, so resume keeps the unconsumed remainder rather than discarding the whole fragment.
    void markScratchConsumed(
        int consumed)
    {
        this.scratchConsumed = consumed;
    }

    public long streamOffset()
    {
        return streamOffset;
    }

    public long line()
    {
        return line;
    }

    public long column()
    {
        return column;
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

    // True when the just-delivered value-string was streamed verbatim as raw bytes rather than decoded
    // into scratch; the parser then splices its raw token bytes instead of rendering canonically.
    public boolean stringVerbatim()
    {
        return stringVerbatim;
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
            if (!starved)
            {
                pendingEvent = JsonParser.Event.KEY_NAME;
                captureKey();
                resumeOp = ResumeOp.NONE;
                state = ParseState.OBJ_AFTER_KEY;
            }
            break;
        case VALUE_STRING:
            // keep the part of the prior fragment the consumer did not take (a decliner keeps all of it
            // via consumed(0)) so the next fragment accumulates onto the remainder and the value is
            // re-presented whole; a consumer that took the whole fragment drops it all, as before
            scratch.delete(0, Math.min(scratchConsumed, scratch.length()));
            scratchConsumed = 0;
            fragmentStart = streamOffset;
            continueStringContent(in);
            if (!starved)
            {
                finishStringValue();
            }
            break;
        case VALUE_NUMBER:
            // keep the unconsumed remainder (a decliner keeps all of it via consumed(0)) so the next
            // fragment accumulates onto it and the number is re-presented whole; full consumption drops it
            scratch.delete(0, Math.min(scratchConsumed, scratch.length()));
            scratchConsumed = 0;
            fragmentStart = streamOffset;
            continueNumberContent(in);
            if (!starved)
            {
                finishNumberValue();
            }
            break;
        case VALUE_TRUE:
            continueLiteral(in, "true");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_TRUE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case VALUE_FALSE:
            continueLiteral(in, "false");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_FALSE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case VALUE_NULL:
            continueLiteral(in, "null");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_NULL;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
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

    // Completes a VALUE_STRING scan: continueStringContent only returns here once the closing quote is
    // seen (otherwise it throws EOFException and onScalarStarved decides fragment-vs-reassemble), so the
    // string is whole — the final fragment of a fragmented value, or a value that fit one window. It is
    // captured lazily and the parse state advances.
    private void finishStringValue()
    {
        valueStreamStart = fragmentStart;
        valueStreamEnd = streamOffset;
        stringVerbatim = scalarSegment || segmenting;
        pendingEvent = JsonParser.Event.VALUE_STRING;
        captureValue();
        fragmenting = false;
        scalarSegment = false;
        resumeOp = ResumeOp.NONE;
        afterValueConsumed();
    }

    // Completes a VALUE_NUMBER scan: continueNumberContent only returns here once the number terminator
    // (or the terminal window's EOF) is seen, so the number is whole. The grammar was validated char by
    // char as the lexeme was scanned; here only the accepting-state check remains. The whole lexeme, when
    // a consumer needs it, lives in scratch via consumed(0) accumulation — no separate retained buffer.
    private void finishNumberValue()
    {
        validateNumberComplete();
        valueStreamStart = fragmentStart;
        valueStreamEnd = streamOffset;
        pendingEvent = JsonParser.Event.VALUE_NUMBER;
        captureValue();
        fragmenting = false;
        resumeOp = ResumeOp.NONE;
        afterValueConsumed();
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
        if (!starved)
        {
            parseValue(in, c);
        }
    }

    private void consumeValueOrEnd(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (!starved)
        {
            if (c == ']')
            {
                emitEnd(JsonParser.Event.END_ARRAY);
            }
            else
            {
                parseValue(in, c);
            }
        }
    }

    private void consumeKey(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (!starved)
        {
            parseKey(in, c);
        }
    }

    private void consumeKeyOrEnd(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (!starved)
        {
            if (c == '}')
            {
                emitEnd(JsonParser.Event.END_OBJECT);
            }
            else
            {
                parseKey(in, c);
            }
        }
    }

    private void consumeColon(
        InputStream in) throws IOException
    {
        int c = skipWhitespace(in);
        if (!starved)
        {
            if (c != ':')
            {
                throw new JsonParsingException("Expected ':' but got: " + describe(c), null);
            }
            state = ParseState.OBJ_AFTER_COLON;
        }
    }

    private void consumeSeparatorOrEnd(
        InputStream in,
        boolean inObject) throws IOException
    {
        int c = skipWhitespace(in);
        if (!starved)
        {
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
    }

    private void parseValue(
        InputStream in,
        int c) throws IOException
    {
        // a scalar-segment arm applies only to a value-string; clear it for any other value
        if (c != '"')
        {
            scalarSegment = false;
        }
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
            valueLine = line;
            valueColumn = column - 1;
            scratch.setLength(0);
            resumeEscape = false;
            resumeUnicodePending = 0;
            resumeOp = ResumeOp.VALUE_STRING;
            continueStringContent(in);
            if (!starved)
            {
                finishStringValue();
            }
            break;
        case 't':
            resumeLiteralIndex = 1;
            resumeOp = ResumeOp.VALUE_TRUE;
            continueLiteral(in, "true");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_TRUE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case 'f':
            resumeLiteralIndex = 1;
            resumeOp = ResumeOp.VALUE_FALSE;
            continueLiteral(in, "false");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_FALSE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case 'n':
            resumeLiteralIndex = 1;
            resumeOp = ResumeOp.VALUE_NULL;
            continueLiteral(in, "null");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_NULL;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        default:
            if (c == '-' || c >= '0' && c <= '9')
            {
                valueStreamStart = streamOffset - 1;
                fragmentStart = streamOffset - 1;
                valueLine = line;
                valueColumn = column - 1;
                scratch.setLength(0);
                scratchConsumed = 0;
                numberState = NumberState.START;
                scratch.append((char) c);
                validateNumberChar(c);
                resumeOp = ResumeOp.VALUE_NUMBER;
                continueNumberContent(in);
                if (!starved)
                {
                    finishNumberValue();
                }
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
        if (!starved)
        {
            pendingEvent = JsonParser.Event.KEY_NAME;
            captureKey();
            resumeOp = ResumeOp.NONE;
            state = ParseState.OBJ_AFTER_KEY;
        }
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
        int c = skipWhitespace(in);
        if (c != -1)
        {
            throw new JsonParsingException("Unexpected trailing content: " + describe(c), null);
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
        while (!starved && resumeLiteralIndex < expected.length())
        {
            int c = readByte(in);
            if (!starved)
            {
                if (c != expected.charAt(resumeLiteralIndex))
                {
                    throw new JsonParsingException("Unexpected character in literal: " + describe(c), null);
                }
                resumeLiteralIndex++;
            }
        }
    }

    private void continueStringContent(
        InputStream in) throws IOException
    {
        boolean complete = false;
        while (!complete && !starved)
        {
            // Track each complete-unit boundary so an EOF mid-char/escape can rewind here: the chars
            // decoded so far ship as a fragment and the partial unit's bytes stay unconsumed
            // (position() reports unitStartOffset) for the caller to re-present on the next window.
            if (!resumeEscape && resumeUnicodePending == 0)
            {
                unitStartOffset = streamOffset;
                unitLine = line;
                unitColumn = column;
            }
            int c = readByte(in);
            if (!starved)
            {
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
                    complete = true;
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
                    if (!starved)
                    {
                        appendCodePointScratch(codePoint);
                    }
                }
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
        return (segmenting || scalarSegment) && resumeOp == ResumeOp.VALUE_STRING;
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
        for (int i = 0; !starved && i < remaining; i++)
        {
            int cont = readByte(in);
            if (!starved)
            {
                if ((cont & 0xc0) != 0x80)
                {
                    throw new JsonParsingException("Invalid UTF-8 continuation: " + describe(cont), null);
                }
                code = (code << 6) | (cont & 0x3f);
            }
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
        boolean complete = false;
        while (!complete && !starved)
        {
            in.mark(1);
            int c = in.read();
            if (c == -1)
            {
                // terminal window: EOF ends the number; otherwise the window is exhausted mid-number
                starved = !terminalEof;
                complete = terminalEof;
            }
            else if (c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')
            {
                streamOffset++;
                advance(c);
                validateNumberChar(c);
                appendScratch((char) c);
            }
            else
            {
                in.reset();
                complete = true;
            }
        }
    }

    // RFC 8259 number grammar -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)? validated as a state machine,
    // one char at a time as it is scanned. The state persists across windows, so a number spanning windows
    // is validated without retaining its whole lexeme. continueNumberContent only feeds the number-class
    // chars [0-9.+-eE], so this rejects any that are illegal in their position.
    private void validateNumberChar(
        int c)
    {
        switch (numberState)
        {
        case START:
            numberState = c == '-' ? NumberState.SIGN : c == '0' ? NumberState.ZERO
                : isDigit((char) c) ? NumberState.INT : rejectNumber(c);
            break;
        case SIGN:
            numberState = c == '0' ? NumberState.ZERO : isDigit((char) c) ? NumberState.INT : rejectNumber(c);
            break;
        case ZERO:
            numberState = c == '.' ? NumberState.DOT : c == 'e' || c == 'E' ? NumberState.EXP : rejectNumber(c);
            break;
        case INT:
            numberState = isDigit((char) c) ? NumberState.INT : c == '.' ? NumberState.DOT
                : c == 'e' || c == 'E' ? NumberState.EXP : rejectNumber(c);
            break;
        case DOT:
            numberState = isDigit((char) c) ? NumberState.FRAC : rejectNumber(c);
            break;
        case FRAC:
            numberState = isDigit((char) c) ? NumberState.FRAC : c == 'e' || c == 'E' ? NumberState.EXP : rejectNumber(c);
            break;
        case EXP:
            numberState = c == '+' || c == '-' ? NumberState.EXP_SIGN
                : isDigit((char) c) ? NumberState.EXP_INT : rejectNumber(c);
            break;
        case EXP_SIGN:
            numberState = isDigit((char) c) ? NumberState.EXP_INT : rejectNumber(c);
            break;
        case EXP_INT:
            numberState = isDigit((char) c) ? NumberState.EXP_INT : rejectNumber(c);
            break;
        default:
            rejectNumber(c);
            break;
        }
    }

    // A complete number must end in an accepting state: an integer (with or without leading zero), a
    // fractional part, or an exponent's digits — not a dangling sign, dot, or exponent marker.
    private void validateNumberComplete()
    {
        boolean accepting = numberState == NumberState.ZERO || numberState == NumberState.INT ||
            numberState == NumberState.FRAC || numberState == NumberState.EXP_INT;
        if (!accepting)
        {
            throw new JsonParsingException("Invalid JSON number: " + scratch, null);
        }
    }

    private NumberState rejectNumber(
        int c)
    {
        throw new JsonParsingException("Invalid JSON number char: " + describe(c), null);
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
            starved = true;
        }
        else
        {
            streamOffset++;
            advance(c);
        }
        return c;
    }

    // Advances line/column for one consumed byte: a newline opens the next line; every other byte that
    // begins a character (i.e. not a UTF-8 continuation byte) advances the column by one.
    private void advance(
        int c)
    {
        if (c == '\n')
        {
            line++;
            column = 1;
        }
        else if ((c & 0xC0) != 0x80)
        {
            column++;
        }
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
