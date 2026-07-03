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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayDeque;
import java.util.Deque;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.json.JsonTokenizer;

public final class JsonTokenizerImpl implements JsonTokenizer
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
    // cap on the decoded chars retained for one value/key; appendScratch fails closed past it so a
    // consumer that declines fragments cannot grow scratch without bound
    private final int maxValueSize;
    private boolean terminalEof;
    // byte length of the current input window; a value whose own bytes reach this length without
    // completing fills the window and is delivered as fragments rather than reassembled across windows.
    // Set independently via window() — a caller that never calls it (e.g. one driving advance() by
    // repeatedly re-wrapping small ranges without an explicit per-slot window size) keeps the default
    // below, disabling the fragmentation threshold entirely.
    private int windowLength = Integer.MAX_VALUE;

    // the currently wrapped input: advance() may be called many times against the same wrapped
    // segment (once per token drained from it); cursor is the next unread position, persisting
    // across those calls until the next wrap() rebinds it to a new window.
    private MemorySegment segment;
    private int cursor;
    private int limit;

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
    private long documentEndOffset;
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

    // set as each object key / array element begins: true when it was reached after a separator comma (a
    // sibling precedes it in its container), false when it was the first member after the container open.
    // A prune reads this at the dropped member to decide whether the next surviving sibling's leading
    // separator must be trimmed.
    private boolean memberSeparated;

    // set as each token is consumed: true when that token was immediately preceded by a value-separator comma
    // (a member/element start with a sibling before it), false otherwise. Unlike memberSeparated, which stays
    // set across the whole member (its value and trailing close included), this is one-shot per token, so a
    // verbatim transcript can emit exactly one SEPARATOR step per source comma.
    private boolean separatorBefore;

    // set when a read hits the end of the current input window mid-token: the scan unwinds logically
    // (no exception) and advance() routes to onScalarStarved; reset at the top of each advance()
    private boolean starved;

    // scalar resume state (valid when resumeOp != NONE)
    private ResumeOp resumeOp = ResumeOp.NONE;
    private boolean resumeEscape;
    private int resumeUnicodePending;   // hex digits remaining for backslash-u escape
    private int resumeUnicodeValue;
    private int resumeLiteralIndex;     // chars matched so far for true/false/null

    public JsonTokenizerImpl()
    {
        this(false);
    }

    public JsonTokenizerImpl(
        boolean terminalEof)
    {
        this(terminalEof, Integer.MAX_VALUE);
    }

    // terminalEof distinguishes a one-shot stream (EOF is the final delimiter) from the chunked
    // wrap()/feed model (EOF marks a frame boundary with more bytes possibly still to come). It
    // only matters for numbers, which unlike strings and the true/false/null literals are not
    // self-terminating and need a following non-digit byte to know they have ended.
    // maxValueSize caps the decoded chars retained for one value/key; growth past it fails closed.
    public JsonTokenizerImpl(
        boolean terminalEof,
        int maxValueSize)
    {
        this.terminalEof = terminalEof;
        this.maxValueSize = maxValueSize;
        for (int i = 0; i < MAX_DEPTH; i++)
        {
            pathKeyChars[i] = new StringBuilder();
        }
    }

    @Override
    public void reset()
    {
        stack.clear();
        scratch.setLength(0);
        scratchConsumed = 0;
        numberState = NumberState.START;
        stringVerbatim = false;
        memberSeparated = false;
        separatorBefore = false;
        pathDepth = 0;
        state = ParseState.DOC_START;
        pendingEvent = null;
        pendingString = null;
        valuePending = false;
        streamOffset = 0;
        documentEndOffset = 0;
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

    // Binds the next input window to scan: advance() may be called many times against this same
    // window (once per token drained from it) before the caller wraps the next one. Independent of
    // windowLength (set separately via window()) — a caller that never calls window() gets the
    // Integer.MAX_VALUE default, i.e. no fragmentation threshold, regardless of how small this
    // particular wrapped range is.
    @Override
    public void wrap(
        MemorySegment segment,
        int offset,
        int limit)
    {
        this.segment = segment;
        this.cursor = offset;
        this.limit = limit;
    }

    // Set per input window: its byte length is the fragmentation bound — a value whose own bytes reach
    // it without completing is delivered as fragments instead of reassembled across windows. Independent
    // of wrap()'s (offset, limit) — a caller may wrap a small range without calling this at all.
    @Override
    public void window(
        int length)
    {
        this.windowLength = length;
    }

    // Tracks whether a segment scan is in progress so a value-string spanning frames is streamed as raw
    // bytes instead of rewound (which would require it whole in one frame) or retained whole in scratch.
    @Override
    public void segmenting(
        boolean segmenting)
    {
        this.segmenting = segmenting;
    }

    // Arms the next value-string to stream verbatim as raw fragments (no decoded retention).
    @Override
    public void scalarSegment(
        boolean scalarSegment)
    {
        this.scalarSegment = scalarSegment;
    }

    // Set per input window: when true this window's EOF is the terminal delimiter (one-shot or final
    // window), so a trailing scalar completes at EOF and an incomplete value is rejected; when false EOF
    // is a frame boundary with more bytes still to come.
    @Override
    public void terminal(
        boolean terminalEof)
    {
        this.terminalEof = terminalEof;
    }

    @Override
    public boolean advance()
    {
        starved = false;

        if (state == ParseState.DOC_DONE)
        {
            if (terminalEof)
            {
                enforceEndOfInput();
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
                resumeScan();
            }
            else
            {
                advanceOne();
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

    @Override
    public JsonParser.Event event()
    {
        return pendingEvent;
    }

    @Override
    public void clearEvent()
    {
        pendingEvent = null;
    }

    @Override
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
    @Override
    public CharSequence stringView()
    {
        return valuePending ? scratch : pendingString;
    }

    // The parser reports, before a resuming advance, how many chars of the current fragment the consumer
    // took, so resume keeps the unconsumed remainder rather than discarding the whole fragment.
    @Override
    public void markScratchConsumed(
        int consumed)
    {
        this.scratchConsumed = consumed;
    }

    @Override
    public long streamOffset()
    {
        return streamOffset;
    }

    @Override
    public long line()
    {
        return line;
    }

    @Override
    public long column()
    {
        return column;
    }

    // Stream-offset span of the most recent readable scalar token (a VALUE_STRING including its
    // surrounding quotes, or a VALUE_NUMBER lexeme). The EOF rewind for a readable scalar guarantees the
    // whole token is contiguous in one frame, so this span maps to a single contiguous slice the sink can
    // splice or fragment.
    @Override
    public long valueStreamStart()
    {
        return valueStreamStart;
    }

    @Override
    public long valueStreamEnd()
    {
        return valueStreamEnd;
    }

    @Override
    public boolean done()
    {
        return state == ParseState.DOC_DONE;
    }

    @Override
    public long documentEndOffset()
    {
        return documentEndOffset;
    }

    @Override
    public boolean inObjectContext()
    {
        return pathDepth > 0 && !pathInArray[pathDepth - 1];
    }

    @Override
    public boolean inArrayContext()
    {
        return pathDepth > 0 && pathInArray[pathDepth - 1];
    }

    // True when the member (object key or array element) at the current boundary was reached after a
    // separator comma — a sibling precedes it in its container — and false when it was the first member
    // after the container open. Read by a prune at a dropped member to decide leading-separator trimming.
    @Override
    public boolean memberSeparated()
    {
        return memberSeparated;
    }

    // True when the token just delivered was immediately preceded by a value-separator comma — one-shot per
    // token, unlike memberSeparated() which stays set across the whole member. A verbatim consumer emits one
    // SEPARATOR step per source comma directly from this, with no need to re-derive container structure.
    @Override
    public boolean separatorBefore()
    {
        return separatorBefore;
    }

    // True while a value-string is being delivered in fragments and more fragments follow the current
    // event; drives the parser's deferredBytes() for over-slot scalars.
    @Override
    public boolean fragmenting()
    {
        return fragmenting;
    }

    // True when the just-delivered value-string was streamed verbatim as raw bytes rather than decoded
    // into scratch; the parser then splices its raw token bytes instead of rendering canonically.
    @Override
    public boolean stringVerbatim()
    {
        return stringVerbatim;
    }

    @Override
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

    private void advanceOne()
    {
        // a comma precedes only the token consumed at a member/element-start state; mirror that here so the
        // signal is one-shot per token — a starved scalar resumes via resumeScan, which leaves this untouched,
        // so a token reached after a comma keeps the flag set across a window boundary
        separatorBefore = state == ParseState.OBJ_AFTER_COMMA || state == ParseState.ARR_AFTER_COMMA;
        switch (state)
        {
        case DOC_START:
            consumeValue();
            break;
        case OBJ_AFTER_OPEN:
            memberSeparated = false;
            consumeKeyOrEnd();
            break;
        case OBJ_AFTER_KEY:
            consumeColon();
            break;
        case OBJ_AFTER_COLON:
            consumeValue();
            break;
        case OBJ_AFTER_VALUE:
            consumeSeparatorOrEnd(true);
            break;
        case OBJ_AFTER_COMMA:
            memberSeparated = true;
            consumeKey();
            break;
        case ARR_AFTER_OPEN:
            memberSeparated = false;
            consumeValueOrEnd();
            break;
        case ARR_AFTER_VALUE:
            consumeSeparatorOrEnd(false);
            break;
        case ARR_AFTER_COMMA:
            memberSeparated = true;
            consumeValue();
            break;
        default:
            throw new JsonParsingException("Unexpected parse state: " + state, null);
        }
    }

    private void resumeScan()
    {
        switch (resumeOp)
        {
        case KEY_STRING:
            continueStringContent();
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
            continueStringContent();
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
            continueNumberContent();
            if (!starved)
            {
                finishNumberValue();
            }
            break;
        case VALUE_TRUE:
            continueLiteral("true");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_TRUE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case VALUE_FALSE:
            continueLiteral("false");
            if (!starved)
            {
                pendingEvent = JsonParser.Event.VALUE_FALSE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case VALUE_NULL:
            continueLiteral("null");
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
    // seen (otherwise the window is exhausted and onScalarStarved decides fragment-vs-reassemble), so the
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

    private void consumeValue()
    {
        int c = skipWhitespace();
        if (!starved)
        {
            parseValue(c);
        }
    }

    private void consumeValueOrEnd()
    {
        int c = skipWhitespace();
        if (!starved)
        {
            if (c == ']')
            {
                emitEnd(JsonParser.Event.END_ARRAY);
            }
            else
            {
                parseValue(c);
            }
        }
    }

    private void consumeKey()
    {
        int c = skipWhitespace();
        if (!starved)
        {
            parseKey(c);
        }
    }

    private void consumeKeyOrEnd()
    {
        int c = skipWhitespace();
        if (!starved)
        {
            if (c == '}')
            {
                emitEnd(JsonParser.Event.END_OBJECT);
            }
            else
            {
                parseKey(c);
            }
        }
    }

    private void consumeColon()
    {
        int c = skipWhitespace();
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
        boolean inObject)
    {
        int c = skipWhitespace();
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
        int c)
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
            continueStringContent();
            if (!starved)
            {
                finishStringValue();
            }
            break;
        case 't':
            resumeLiteralIndex = 1;
            resumeOp = ResumeOp.VALUE_TRUE;
            continueLiteral("true");
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
            continueLiteral("false");
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
            continueLiteral("null");
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
                appendScratch((char) c);
                validateNumberChar(c);
                resumeOp = ResumeOp.VALUE_NUMBER;
                continueNumberContent();
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
        int c)
    {
        if (c != '"')
        {
            throw new JsonParsingException("Expected '\"' for key but got: " + describe(c), null);
        }
        scratch.setLength(0);
        resumeEscape = false;
        resumeUnicodePending = 0;
        resumeOp = ResumeOp.KEY_STRING;
        continueStringContent();
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
            documentEndOffset = streamOffset;
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
        if (state == ParseState.DOC_DONE)
        {
            documentEndOffset = streamOffset;
        }
        markValueConsumed();
    }

    // After a complete top-level value on a one-shot stream, only insignificant whitespace may
    // remain; any further token is invalid per RFC 8259. Chunked frame sources skip this check.
    private void enforceEndOfInput()
    {
        int c = skipWhitespace();
        if (c != -1)
        {
            throw new JsonParsingException("Unexpected trailing content: " + describe(c), null);
        }
    }

    private int skipWhitespace()
    {
        int c;
        do
        {
            c = readByte();
        }
        while (c == ' ' || c == '\t' || c == '\n' || c == '\r');
        return c;
    }

    private void continueLiteral(
        String expected)
    {
        while (!starved && resumeLiteralIndex < expected.length())
        {
            int c = readByte();
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

    private void continueStringContent()
    {
        boolean complete = false;
        while (!complete && !starved)
        {
            // Track each complete-unit boundary so an exhausted window mid-char/escape can rewind here: the
            // chars decoded so far ship as a fragment and the partial unit's bytes stay unconsumed
            // (position() reports unitStartOffset) for the caller to re-present on the next window.
            if (!resumeEscape && resumeUnicodePending == 0)
            {
                unitStartOffset = streamOffset;
                unitLine = line;
                unitColumn = column;
            }
            int c = readByte();
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
                    int codePoint = decodeUtf8(c);
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
            if (scratch.length() >= maxValueSize)
            {
                throw resourceExhausted();
            }
            scratch.append(c);
        }
    }

    private void appendCodePointScratch(
        int codePoint)
    {
        if (!streamingValue())
        {
            if (scratch.length() >= maxValueSize)
            {
                throw resourceExhausted();
            }
            scratch.appendCodePoint(codePoint);
        }
    }

    // fail closed when a retained value/key exceeds the cap: a consumer that declines fragments to gather
    // a value whole must not be able to drive scratch past maxValueSize. Surfaced as REJECTED by the pump.
    private JsonParsingException resourceExhausted()
    {
        return new JsonParsingException("JSON value exceeds the maximum retained size of " + maxValueSize +
            " chars (resource exhausted)", null);
    }

    // A value-string being streamed as raw segment bytes is not retained in scratch; keys and numbers
    // still retain (keys for path matching, numbers for grammar validation).
    private boolean streamingValue()
    {
        return (segmenting || scalarSegment) && resumeOp == ResumeOp.VALUE_STRING;
    }

    private int decodeUtf8(
        int first)
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
            int cont = readByte();
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

    private void continueNumberContent()
    {
        boolean complete = false;
        while (!complete && !starved)
        {
            if (cursor >= limit)
            {
                // terminal window: exhaustion ends the number; otherwise the window is exhausted mid-number
                starved = !terminalEof;
                complete = terminalEof;
            }
            else
            {
                // peek: a plain index read, no mark/reset needed — only advance the cursor if this byte
                // actually belongs to the number, leaving it unconsumed as the next token's first byte
                int c = segment.get(ValueLayout.JAVA_BYTE, cursor) & 0xff;
                if (c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')
                {
                    cursor++;
                    streamOffset++;
                    advance(c);
                    validateNumberChar(c);
                    appendScratch((char) c);
                }
                else
                {
                    complete = true;
                }
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

    private int readByte()
    {
        int c;
        if (cursor >= limit)
        {
            c = -1;
            starved = true;
        }
        else
        {
            c = segment.get(ValueLayout.JAVA_BYTE, cursor) & 0xff;
            cursor++;
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
