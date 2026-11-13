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

import jakarta.json.JsonException;
import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonTokenizer;
import io.aklivity.zilla.runtime.common.json.view.JsonEscapeLens;

/**
 * Tokenizes JSON from one input window at a time, reading in place from the buffer it is wrapped on.
 * <p>
 * Every field is either <em>carried</em>, so it must survive from one window to the next, or <em>window-local</em>,
 * so it is rewritten by {@code wrap}, by the start of each {@code advance}, or by the start of the token it
 * describes before it is read again. At a window boundary a key, a number, or a string that fits a window is
 * rewound to its start, so none of its scan state is carried; the scan state that is carried belongs to a string
 * delivered in fragments, to {@code true}, {@code false} and {@code null}, and to any token scanned while
 * {@code segmenting} is set.
 * <p>
 * Carried:
 * <ul>
 * <li>{@code path}, {@code pathDepth}, {@code scopeDepth}: the open containers and the depth the escaped scope
 * opened at; the state a container returns to when it closes is derived from them</li>
 * <li>{@code state}, {@code streamOffset}, {@code documentEndOffset}, {@code line}, {@code column}</li>
 * <li>{@code memberSeparated}, {@code separatorBefore}: the separator context of the member being read</li>
 * <li>{@code segmenting}, {@code scalarSegment}: the delivery modes set by the caller</li>
 * <li>{@code fragmenting}, {@code fragmentStart}: a string delivered in fragments, and where the open fragment
 * began</li>
 * <li>{@code escapeArmed}, {@code lens}, {@code scopeStart}: an escaped scope armed or open</li>
 * <li>{@code resumeOp}, {@code resumeEscape}, {@code resumeUnicodePending}, {@code resumeUnicodeValue},
 * {@code resumeLiteralIndex}, {@code numberState}: a token part scanned, meaningful only while
 * {@code resumeOp} is not {@code NONE}</li>
 * <li>{@code valueStreamStart}, {@code valueStreamEnd}, {@code valueLine}, {@code valueColumn},
 * {@code unitStartOffset}, {@code unitLine}, {@code unitColumn}: the span and rewind points of a token part
 * scanned, written at the start of each token</li>
 * </ul>
 * Window-local:
 * <ul>
 * <li>{@code terminalEof}, {@code windowLength}, {@code windowStart}, {@code buffer}, {@code bufferOffset},
 * {@code bufferLimit}, {@code wrapOffset}, {@code wrapStreamOffset}: the window and the cursor into it</li>
 * <li>{@code pendingEvent}, {@code starved}, {@code declinedBytes}, {@code stringVerbatim}: the outcome of the
 * latest {@code advance}, nothing at a boundary between events</li>
 * <li>{@code viewStart}, {@code viewChars}, {@code viewPlain}, {@code viewPending}, {@code viewOffset},
 * {@code viewLength}, {@code unitViewChars}, {@code pairOpen}: the string read in place from the window</li>
 * <li>{@code scopeEnded}, {@code lensUnit}, {@code lensIndex}, {@code lensCount}, {@code lensSize}: the escape
 * being read out of the window, cleared whenever the cursor is repositioned</li>
 * <li>{@code mark}: the capture {@code rewind} returns to, valid only within the window it was taken in</li>
 * </ul>
 */
final class JsonTokenizerImpl implements JsonTokenizer
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
        ARR_AFTER_VALUE,
        ESC_START,
        ESC_DONE
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

    private static final int NO_VIEW = -1;
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGHS = 0x8080808080808080L;
    private static final long QUOTES = 0x2222222222222222L;
    private static final long BACKSLASHES = 0x5c5c5c5c5c5c5c5cL;
    private static final long SPACES = 0x2020202020202020L;

    private final boolean quoted;
    private boolean terminalEof;
    // byte length of the current input window; a value whose own bytes reach this length without
    // completing fills the window and is delivered as fragments rather than reassembled across windows.
    private int windowLength = Integer.MAX_VALUE;
    // absolute stream offset where the current input window began; onScalarStarved() measures a value's
    // own bytes against the room actually left for it in this window (windowStart + windowLength), not
    // against the window's full length, since preceding tokens (a key, sibling values) sharing the same
    // window with the value's own start already used up some of that length
    private long windowStart;

    // the buffer-resident window wrap() wraps: readByte()/the quoted/number peeks read directly from it, and
    // seek() translates an absolute streamOffset back into a position in it for every rollback (rewind,
    // rewindLookahead, the quoted-key rollback, the starve rollback)
    private DirectBufferEx buffer;
    private int bufferOffset;
    private int bufferLimit;
    private int wrapOffset;
    private long wrapStreamOffset;

    // whether each open container is an array, one bit per level, and how many are open; the state a container
    // returns to when it closes is derived from this path and scopeDepth, so no stack of return states is kept
    private long path;
    private int pathDepth;

    private ParseState state = ParseState.DOC_START;
    private JsonEvent pendingEvent;
    // a value-string that completes within the current window is not copied into scratch: its chars are read in
    // place from the window bytes. viewStart is where its bytes begin in the window buffer (NO_VIEW when the
    // string is not read in place: a key, a number, a verbatim or fragmented string), viewChars and viewPlain
    // are counted as the bytes are validated, and viewPending marks a completed value waiting to be read from
    // [viewOffset, viewOffset + viewLength) of the window buffer
    private int viewStart = NO_VIEW;
    private int viewChars;
    private boolean viewPlain;
    private boolean viewPending;
    private int viewOffset;
    private int viewLength;
    // the chars counted before the last unit boundary, and whether the last unit was the first half of an
    // escaped surrogate pair, whose second half then belongs to the same unit
    private int unitViewChars;
    private boolean pairOpen;
    private int declinedBytes;
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

    // an escaped scope: the string value that follows the key being read is armed, and is then read as a document
    // of its own, in place. The bytes of the document are those of the string with its escapes undone, which the
    // reads below produce a unit at a time, so the document is tokenized from the window with no copy. scopeStart
    // is where the string's content begins, scopeEnded is set once a read meets the quote that closes the string,
    // which ends the document as the end of the final window ends a document, and the register holds the bytes of
    // the escape being read out, an escape being up to four bytes of UTF-8.
    private boolean escapeArmed;
    private boolean lens;
    private boolean scopeEnded;
    private long scopeStart;
    private int scopeDepth;
    private long lensUnit;
    private int lensIndex;
    private int lensCount;
    private int lensSize;

    // scalar resume state (valid when resumeOp != NONE)
    private ResumeOp resumeOp = ResumeOp.NONE;
    private boolean resumeEscape;
    private int resumeUnicodePending;   // hex digits remaining for backslash-u escape
    private int resumeUnicodeValue;
    private int resumeLiteralIndex;     // chars matched so far for true/false/null

    private Mark mark;

    // the state a mark holds, kept apart so a parser that never marks carries only the reference
    private static final class Mark
    {
        ParseState state;
        long path;
        int pathDepth;
        long streamOffset;
        long valueStreamStart;
        long valueStreamEnd;
        long documentEndOffset;
        long line;
        long column;
        long valueLine;
        long valueColumn;
        long unitLine;
        long unitColumn;
        long fragmentStart;
        long unitStartOffset;
        NumberState numberState;
        boolean stringVerbatim;
        boolean memberSeparated;
        boolean separatorBefore;
        boolean segmenting;
        boolean scalarSegment;
        int viewChars;
        boolean viewPlain;
        int viewOffset;
        int viewLength;
        boolean viewPending;
        int unitViewChars;
        boolean pairOpen;
        boolean lens;
        long scopeStart;
        int scopeDepth;
    }

    // quoted holds a key back until its separator, whitespace and the value's first byte are in the
    // window, so the position after a key is the start of its (possibly quoted) value token.
    JsonTokenizerImpl(
        boolean quoted)
    {
        this.quoted = quoted;
    }

    @Override
    public void reset()
    {
        if (mark != null)
        {
            mark.state = null;
        }
        numberState = NumberState.START;
        stringVerbatim = false;
        memberSeparated = false;
        separatorBefore = false;
        path = 0L;
        pathDepth = 0;
        scopeDepth = 0;
        state = ParseState.DOC_START;
        pendingEvent = null;
        viewStart = NO_VIEW;
        viewPending = false;
        declinedBytes = 0;
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
        escapeArmed = false;
        lens = false;
        scopeEnded = false;
        lensIndex = 0;
        lensCount = 0;
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

    // Ends the current document (state must be DOC_DONE, reached only once its own event delivered
    // END_DOCUMENT) and begins the next top-level value at the current stream position -- distinct from
    // reset(), which rewinds streamOffset and every other field to their start-of-input values.
    @Override
    public void nextDocument()
    {
        assert state == ParseState.DOC_DONE;
        state = ParseState.DOC_START;
    }

    // Takes the mark rewind() returns to, between events: no event staged and no token part way scanned, so the
    // scalar state below is the whole of what a later rewind has to put back. The caller keeps the window and
    // its bytes, so a rewind lands on the same input.
    @Override
    public void mark()
    {
        if (pendingEvent != null || resumeOp != ResumeOp.NONE || fragmenting)
        {
            throw new IllegalStateException("mark requires a whole-token boundary");
        }

        if (mark == null)
        {
            mark = new Mark();
        }

        mark.state = state;
        mark.path = path;
        mark.pathDepth = pathDepth;
        mark.streamOffset = streamOffset;
        mark.valueStreamStart = valueStreamStart;
        mark.valueStreamEnd = valueStreamEnd;
        mark.documentEndOffset = documentEndOffset;
        mark.line = line;
        mark.column = column;
        mark.valueLine = valueLine;
        mark.valueColumn = valueColumn;
        mark.unitLine = unitLine;
        mark.unitColumn = unitColumn;
        mark.fragmentStart = fragmentStart;
        mark.unitStartOffset = unitStartOffset;
        mark.numberState = numberState;
        mark.stringVerbatim = stringVerbatim;
        mark.memberSeparated = memberSeparated;
        mark.separatorBefore = separatorBefore;
        mark.segmenting = segmenting;
        mark.scalarSegment = scalarSegment;
        mark.viewChars = viewChars;
        mark.viewPlain = viewPlain;
        mark.viewOffset = viewOffset;
        mark.viewLength = viewLength;
        mark.viewPending = viewPending;
        mark.unitViewChars = unitViewChars;
        mark.pairOpen = pairOpen;
        mark.lens = lens;
        mark.scopeStart = scopeStart;
        mark.scopeDepth = scopeDepth;
    }

    // Returns to the mark: the scan resumes at the marked byte with the state it had there. Only the container
    // the mark was taken in is put back, so the parse must not have left it; stacks the mark did not cover are
    // dropped back to the marked depth. Whatever the events since the mark staged or decoded is discarded.
    @Override
    public void rewind()
    {
        if (mark == null || mark.state == null)
        {
            throw new IllegalStateException("rewind requires a mark");
        }

        if (mark.streamOffset < wrapStreamOffset)
        {
            throw new IllegalStateException("rewind requires the window the mark was taken in");
        }

        if (pathDepth < mark.pathDepth || returnDepth(pathDepth, lens) < returnDepth(mark.pathDepth, mark.lens))
        {
            throw new IllegalStateException("rewind requires the parse to remain within the marked container");
        }

        path = mark.path;
        pathDepth = mark.pathDepth;
        state = mark.state;
        valueStreamStart = mark.valueStreamStart;
        valueStreamEnd = mark.valueStreamEnd;
        documentEndOffset = mark.documentEndOffset;
        valueLine = mark.valueLine;
        valueColumn = mark.valueColumn;
        unitLine = mark.unitLine;
        unitColumn = mark.unitColumn;
        fragmentStart = mark.fragmentStart;
        unitStartOffset = mark.unitStartOffset;
        numberState = mark.numberState;
        stringVerbatim = mark.stringVerbatim;
        memberSeparated = mark.memberSeparated;
        separatorBefore = mark.separatorBefore;
        segmenting = mark.segmenting;
        scalarSegment = mark.scalarSegment;
        viewChars = mark.viewChars;
        viewPlain = mark.viewPlain;
        viewOffset = mark.viewOffset;
        viewLength = mark.viewLength;
        unitViewChars = mark.unitViewChars;
        pairOpen = mark.pairOpen;
        lens = mark.lens;
        scopeStart = mark.scopeStart;
        scopeDepth = mark.scopeDepth;
        seek(mark.streamOffset, mark.line, mark.column);
        pendingEvent = null;
        viewStart = NO_VIEW;
        viewPending = mark.viewPending;
        starved = false;
        fragmenting = false;
        resumeOp = ResumeOp.NONE;
        resumeEscape = false;
        resumeUnicodePending = 0;
        resumeUnicodeValue = 0;
        resumeLiteralIndex = 0;
    }

    @Override
    public void wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        this.buffer = buffer;
        this.bufferOffset = offset;
        this.bufferLimit = limit;
        this.wrapOffset = offset;
        this.wrapStreamOffset = streamOffset;
        this.terminalEof = last;

        if (declinedBytes > 0)
        {
            declinedBytes = 0;
            viewPending = false;
        }
    }

    // Set per input window: its byte length is the fragmentation bound — a value whose own bytes reach
    // it without completing is delivered as fragments instead of reassembled across windows.
    @Override
    public void window(
        int length)
    {
        this.windowLength = length;
        this.windowStart = streamOffset;
    }

    // Repositions the scan to an earlier absolute stream offset within the current window, restoring the
    // line/column that went with it; every rollback (rewind, rewindLookahead, the quoted-key
    // rollback, the starve rollback) goes through this one place so streamOffset and the buffer cursor it
    // derives from never drift apart.
    private void seek(
        long targetStreamOffset,
        long targetLine,
        long targetColumn)
    {
        this.streamOffset = targetStreamOffset;
        this.line = targetLine;
        this.column = targetColumn;
        this.bufferOffset = wrapOffset + (int) (targetStreamOffset - wrapStreamOffset);
        this.lensIndex = 0;
        this.lensCount = 0;
        this.scopeEnded = false;
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
        viewPending = false;

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
        if (scopeEnded)
        {
            throw new JsonParsingException("Incomplete document in escaped string", null);
        }

        final boolean midScalar =
            (resumeOp == ResumeOp.VALUE_STRING || resumeOp == ResumeOp.VALUE_NUMBER ||
                resumeOp == ResumeOp.KEY_STRING) && !segmenting;
        boolean delivered = false;
        if (midScalar)
        {
            final long valueBytes = streamOffset - valueStreamStart;
            // room actually left for the value in this window, not the window's own full length: a key or
            // sibling value sharing the window ahead of this value's start already spent some of that
            // length, so comparing against windowLength directly would never trip for a value that doesn't
            // begin at the window's own first byte, however large the value truly is
            final long valueRoom = windowStart + windowLength - valueStreamStart;
            // a kept scalar leaf fragments verbatim on any starve; a decoded value-string only once it fills the
            // room left for it in the window. A key or number is never fragmented.
            final boolean fragment = resumeOp == ResumeOp.VALUE_STRING &&
                !eof() && (scalarSegment || fragmenting || valueBytes >= valueRoom);
            if (fragment)
            {
                // rewind to the last complete code-point/escape boundary, leaving the partial unit for the caller
                seek(unitStartOffset, unitLine, unitColumn);
                viewChars = unitViewChars;
                pairOpen = false;
                resumeEscape = false;
                resumeUnicodePending = 0;
                resumeUnicodeValue = 0;
                fragmenting = true;
                // ship a fragment only when this round scanned new bytes
                if (streamOffset > fragmentStart)
                {
                    valueStreamStart = fragmentStart;
                    valueStreamEnd = streamOffset;
                    stringVerbatim = scalarSegment || segmenting;
                    pendingEvent = JsonEvent.VALUE_STRING;
                    // capture lazily: a verbatim/segmented consumer reads only getSegment() and never
                    // materializes the decoded chars, so leave them in scratch (cleared when the next
                    // fragment resumes) and let stringValue() take them on demand
                    viewPending = false;
                    endFragmentView();
                    delivered = true;
                }

                viewStart = NO_VIEW;
            }
            else
            {
                // value fits a window (or terminal), or is a key or number: rewind to its start and
                // reassemble whole next window
                seek(valueStreamStart, valueLine, valueColumn);
                viewStart = NO_VIEW;
                pairOpen = false;
                resumeOp = ResumeOp.NONE;
                resumeEscape = false;
                resumeUnicodePending = 0;
                resumeUnicodeValue = 0;
            }
        }
        return delivered;
    }

    // Gives back the separator and whitespace scanned past the last delivered token when a non-final window
    // ended before the next token began, so only whole tokens are ever consumed and the caller re-presents the
    // rest with the next window. A separator consumed that way is never lost to, or emitted ahead of, a decision
    // about the member it introduces. A comma goes back to the state before it, which is the state that
    // expects a separator or the container's close.
    @Override
    public void rewindLookahead(
        long offset,
        long offsetLine,
        long offsetColumn)
    {
        if (!terminalEof && resumeOp == ResumeOp.NONE && streamOffset > offset)
        {
            switch (state)
            {
            case OBJ_AFTER_COMMA:
                state = ParseState.OBJ_AFTER_VALUE;
                seek(offset, offsetLine, offsetColumn);
                break;
            case ARR_AFTER_COMMA:
                state = ParseState.ARR_AFTER_VALUE;
                seek(offset, offsetLine, offsetColumn);
                break;
            case OBJ_AFTER_OPEN:
            case OBJ_AFTER_VALUE:
            case ARR_AFTER_OPEN:
            case ARR_AFTER_VALUE:
                seek(offset, offsetLine, offsetColumn);
                break;
            default:
                break;
            }
        }
    }

    @Override
    public JsonEvent event()
    {
        return pendingEvent;
    }

    @Override
    public void clearEvent()
    {
        pendingEvent = null;
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
    public void escape()
    {
        if (lens)
        {
            throw new JsonException("escaped scope is already open");
        }

        escapeArmed = true;
    }

    @Override
    public boolean inScope()
    {
        return lens;
    }

    @Override
    public long documentEndOffset()
    {
        return documentEndOffset;
    }

    @Override
    public boolean inObjectContext()
    {
        return JsonParseState.pathInObject(path, pathDepth);
    }

    @Override
    public boolean inArrayContext()
    {
        return JsonParseState.pathInArray(path, pathDepth);
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

    private void pushPath(
        boolean inArray)
    {
        if (JsonParseState.pathFull(pathDepth))
        {
            throw new JsonParsingException("JSON depth exceeds " + JsonParseState.MAX_DEPTH, null);
        }
        path = JsonParseState.pushPath(path, pathDepth, inArray);
        pathDepth++;
    }

    private void popPath()
    {
        path = JsonParseState.popPath(path, pathDepth);
        pathDepth--;
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
        case ESC_START:
            consumeScopeValue();
            break;
        case ESC_DONE:
            consumeScopeEnd();
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
            // keep the part of the prior fragment the consumer did not take (a decliner keeps all of it
            // via consumed(0)) so the next fragment accumulates onto the remainder and the key is
            // re-presented whole; a consumer that took the whole fragment drops it all, as before
            fragmentStart = streamOffset;
            continueStringContent();
            if (!starved)
            {
                finishKeyValue();
            }
            break;
        case VALUE_STRING:
            // keep the part of the prior fragment the consumer did not take (a decliner keeps all of it
            // via consumed(0)) so the next fragment accumulates onto the remainder and the value is
            // re-presented whole; a consumer that took the whole fragment drops it all, as before
            fragmentStart = streamOffset;
            beginView();
            continueStringContent();
            if (!starved)
            {
                finishStringValue();
            }
            break;
        case VALUE_NUMBER:
            // keep the unconsumed remainder (a decliner keeps all of it via consumed(0)) so the next
            // fragment accumulates onto it and the number is re-presented whole; full consumption drops it
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
                pendingEvent = JsonEvent.VALUE_TRUE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case VALUE_FALSE:
            continueLiteral("false");
            if (!starved)
            {
                pendingEvent = JsonEvent.VALUE_FALSE;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        case VALUE_NULL:
            continueLiteral("null");
            if (!starved)
            {
                pendingEvent = JsonEvent.VALUE_NULL;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        default:
            throw new IllegalStateException("Unexpected resumeOp: " + resumeOp);
        }
    }

    // Completes a VALUE_STRING scan: continueStringContent only returns here once the closing quote is
    // seen (otherwise it leaves starved set and onScalarStarved decides fragment-vs-reassemble), so the
    // string is whole — the final fragment of a fragmented value, or a value that fit one window. It is
    // captured lazily and the parse state advances.
    private void finishStringValue()
    {
        valueStreamStart = fragmentStart;
        valueStreamEnd = streamOffset;
        stringVerbatim = scalarSegment || segmenting;
        pendingEvent = JsonEvent.VALUE_STRING;
        viewPending = false;
        endView();
        fragmenting = false;
        scalarSegment = false;
        resumeOp = ResumeOp.NONE;
        afterValueConsumed();
    }

    private void beginView()
    {
        viewStart = segmenting || streamingValue() ? NO_VIEW : bufferOffset;
        viewChars = 0;
        unitViewChars = 0;
        viewPlain = true;
        pairOpen = false;
    }

    // the closing quote has been read, so the value's bytes end just before it, which in a scope is the whole of the
    // escape that spells the quote
    private void endView()
    {
        if (viewStart != NO_VIEW)
        {
            viewOffset = viewStart;
            viewLength = bufferOffset - lastUnitSize() - viewStart;
            viewPending = true;
            viewStart = NO_VIEW;
        }
    }

    // a number has no closing quote: its bytes end where the scan stopped
    private void endLexemeView()
    {
        if (viewStart != NO_VIEW)
        {
            viewOffset = viewStart;
            viewLength = bufferOffset - viewStart;
            viewPending = true;
            viewStart = NO_VIEW;
        }
    }

    // a fragment ends at the unit boundary the scan has been rewound to, so it is the bytes up to there
    private void endFragmentView()
    {
        if (viewStart != NO_VIEW)
        {
            viewOffset = viewStart;
            viewLength = bufferOffset - viewStart;
            viewPending = true;
            viewStart = NO_VIEW;
        }
    }

    // The current key, string or number as a range of the window it was read from, or -1 in length when its bytes
    // are not read in place, as a string streamed as segments. The caller reads the chars from the window, in
    // place, so a value is never copied.
    @Override
    public int tokenOffset()
    {
        return viewOffset;
    }

    @Override
    public int tokenLength()
    {
        return viewPending ? viewLength : -1;
    }

    @Override
    public int tokenChars()
    {
        return viewChars;
    }

    @Override
    public boolean tokenPlain()
    {
        return viewPlain;
    }

    // A value declined in fragments is not copied anywhere: the caller keeps its bytes in the window it holds, so a
    // value that does not fit it cannot be had whole. The consumer left the last bytes of the pending fragment, so
    // the position goes back to the first of them, and the fragment is read again, longer, from the next window.
    @Override
    public void decline(
        int bytes)
    {
        if (fragmenting && viewPending && bytes != declinedBytes)
        {
            final int delta = declinedBytes - bytes;
            final int columns = lens ? Math.abs(delta) : characters(Math.min(bufferOffset, bufferOffset + delta),
                Math.max(bufferOffset, bufferOffset + delta));
            column += delta < 0 ? -columns : columns;
            bufferOffset += delta;
            streamOffset += delta;
            declinedBytes = bytes;
        }
    }

    // how many characters begin in the bytes [from, to) of the window: each byte that is not a UTF-8 continuation
    private int characters(
        int from,
        int to)
    {
        int characters = 0;
        for (int index = from; index < to; index++)
        {
            if ((buffer.getByte(index) & 0xC0) != 0x80)
            {
                characters++;
            }
        }
        return characters;
    }

    @Override
    public boolean declined()
    {
        return declinedBytes > 0;
    }

    // Completes a VALUE_NUMBER scan: continueNumberContent only returns here once the number terminator
    // (or the terminal window's end) is seen, so the number is whole. The grammar was validated char by
    // char as the lexeme was scanned; here only the accepting-state check remains. The whole lexeme, when
    // a consumer needs it, lives in scratch via consumed(0) accumulation — no separate retained buffer.
    private void finishNumberValue()
    {
        validateNumberComplete();
        valueStreamStart = fragmentStart;
        valueStreamEnd = streamOffset;
        pendingEvent = JsonEvent.VALUE_NUMBER;
        viewPending = false;
        endLexemeView();
        fragmenting = false;
        resumeOp = ResumeOp.NONE;
        afterValueConsumed();
    }

    // Completes a KEY_STRING scan: continueStringContent only returns here once the closing quote is seen
    // (otherwise onScalarStarved decides fragment-vs-reassemble), so the key is whole — the final fragment
    // of a fragmented key, or a key that fit one window.
    private void finishKeyValue()
    {
        pendingEvent = JsonEvent.KEY_NAME;
        viewPending = false;
        endView();
        fragmenting = false;
        resumeOp = ResumeOp.NONE;
        state = ParseState.OBJ_AFTER_KEY;
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
                emitEnd(JsonEvent.END_ARRAY);
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
                emitEnd(JsonEvent.END_OBJECT);
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
                emitEnd(inObject ? JsonEvent.END_OBJECT : JsonEvent.END_ARRAY);
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
        if (escapeArmed)
        {
            enterScope(c);
            return;
        }

        // a scalar-segment arm applies only to a value-string; clear it for any other value
        if (c != '"')
        {
            scalarSegment = false;
        }
        switch (c)
        {
        case '{':
            pendingEvent = JsonEvent.START_OBJECT;
            pushAndEnter(ParseState.OBJ_AFTER_OPEN);
            pushPath(false);
            break;
        case '[':
            pendingEvent = JsonEvent.START_ARRAY;
            pushAndEnter(ParseState.ARR_AFTER_OPEN);
            pushPath(true);
            break;
        case '"':
            startToken();
            resumeEscape = false;
            resumeUnicodePending = 0;
            resumeOp = ResumeOp.VALUE_STRING;
            beginView();
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
                pendingEvent = JsonEvent.VALUE_TRUE;
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
                pendingEvent = JsonEvent.VALUE_FALSE;
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
                pendingEvent = JsonEvent.VALUE_NULL;
                resumeOp = ResumeOp.NONE;
                afterValueConsumed();
            }
            break;
        default:
            if (c == '-' || c >= '0' && c <= '9')
            {
                startToken();
                numberState = NumberState.START;
                beginView();
                if (viewStart != NO_VIEW)
                {
                    viewStart -= lastUnitSize();
                }
                countChar();
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
        startToken();
        resumeEscape = false;
        resumeUnicodePending = 0;
        resumeOp = ResumeOp.KEY_STRING;
        beginView();
        continueStringContent();
        if (!starved)
        {
            finishKeyValue();

            if (quoted)
            {
                alignKeyBoundary();
            }
        }
    }

    // Moves past the separator and whitespace to the first byte of the member's value, leaving that byte
    // unread. A non-final window that ends before it takes the key back to its opening quote, in the state that
    // preceded it, so the key is delivered only when the position after it can be a token boundary. The buffer
    // is already resident for the whole window, so aligning is a plain peek at bufferOffset — no mark/reset.
    private void alignKeyBoundary()
    {
        consumeColon();

        boolean aligned = false;

        while (!starved && !aligned)
        {
            if (bufferOffset >= bufferLimit)
            {
                starved = true;
            }
            else
            {
                final int c = peekByte();
                if (c < 0)
                {
                    starved = true;
                }
                else if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
                {
                    takeByte(c);
                }
                else
                {
                    aligned = true;
                }
            }
        }

        if (starved && !eof())
        {
            seek(valueStreamStart, valueLine, valueColumn);
            state = memberSeparated ? ParseState.OBJ_AFTER_COMMA : ParseState.OBJ_AFTER_OPEN;
            pendingEvent = null;
            viewPending = false;
            viewStart = NO_VIEW;
        }
    }

    private void pushAndEnter(
        ParseState nested)
    {
        state = nested;
    }

    // the state the value opened at this level returns to once it ends: that of the container it sits in
    private ParseState returnState(
        int level)
    {
        final ParseState returnState;
        if (level == 0)
        {
            returnState = ParseState.DOC_DONE;
        }
        else
        {
            returnState = JsonParseState.pathArrayAt(path, level - 1) ? ParseState.ARR_AFTER_VALUE : ParseState.OBJ_AFTER_VALUE;
        }
        return returnState;
    }

    // how many values are open: each open container, and the escaped string whose content is the document
    private static int returnDepth(
        int pathDepth,
        boolean lens)
    {
        return lens ? pathDepth + 1 : pathDepth;
    }

    private void afterValueConsumed()
    {
        switch (state)
        {
        case DOC_START:
            state = ParseState.DOC_DONE;
            documentEndOffset = streamOffset;
            break;
        case ESC_START:
            state = ParseState.ESC_DONE;
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
        JsonEvent endEvent)
    {
        pendingEvent = endEvent;
        popPath();
        state = lens && pathDepth == scopeDepth ? ParseState.ESC_DONE : returnState(pathDepth);
        if (state == ParseState.DOC_DONE)
        {
            documentEndOffset = streamOffset;
        }
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
            if (viewStart != NO_VIEW && !resumeEscape && resumeUnicodePending == 0)
            {
                skipPlain();
            }

            // Track each complete-unit boundary so a starve mid-char/escape can rewind here: the chars
            // decoded so far ship as a fragment and the partial unit's bytes stay unconsumed
            // (position() reports unitStartOffset) for the caller to re-present on the next window. The two
            // escapes of a surrogate pair are one unit, like the four bytes of a raw one, so the boundary
            // is not moved after the first.
            final boolean pairing = pairOpen;
            pairOpen = false;
            if (!resumeEscape && resumeUnicodePending == 0 && !pairing)
            {
                unitStartOffset = streamOffset;
                unitLine = line;
                unitColumn = column;
                unitViewChars = viewChars;
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
                        countChar();
                        pairOpen = Character.isHighSurrogate((char) resumeUnicodeValue);
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
                        countChar();
                        break;
                    case 'b':
                        countChar();
                        break;
                    case 'f':
                        countChar();
                        break;
                    case 'n':
                        countChar();
                        break;
                    case 'r':
                        countChar();
                        break;
                    case 't':
                        countChar();
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
                    viewPlain = false;
                }
                else if (c < 0x20)
                {
                    throw new JsonParsingException("Unescaped control character in string: " + describe(c), null);
                }
                else if (c < 0x80)
                {
                    countChar();
                }
                else
                {
                    int codePoint = decodeUtf8(c);
                    viewPlain = false;
                    if (!starved)
                    {
                        countCodePoint(codePoint);
                    }
                }
            }
        }
    }

    // A string read in place needs nothing from a stretch of plain ASCII, so the scan passes over it a word at a
    // time. A byte ends the stretch if its high bit is set, or if it is a quote, a backslash or a control char, and
    // each of those is found in the word without looking at its bytes one by one. Plain ASCII is one char a byte
    // and never a new line, so the position moves by the number of bytes skipped.
    private void skipPlain()
    {
        final int words = (bufferLimit - bufferOffset) / Long.BYTES;

        int offset = bufferOffset;
        boolean plain = true;

        for (int word = 0; plain && word < words; word++)
        {
            final long bytes = buffer.getLong(offset);
            final long quotes = bytes ^ QUOTES;
            final long backslashes = bytes ^ BACKSLASHES;

            plain = ((bytes | (bytes - SPACES) | (quotes - ONES) & ~quotes | (backslashes - ONES) & ~backslashes) & HIGHS) == 0L;

            if (plain)
            {
                offset += Long.BYTES;
            }
        }

        final int skipped = offset - bufferOffset;

        bufferOffset = offset;
        streamOffset += skipped;
        column += skipped;
        viewChars += skipped;
    }

    private void countChar()
    {
        if (viewStart != NO_VIEW)
        {
            viewChars++;
        }
    }

    private void countCodePoint(
        int codePoint)
    {
        if (viewStart != NO_VIEW)
        {
            viewChars += Character.charCount(codePoint);
        }
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

    // Numbers are not self-terminating, so the terminating char must be peeked without consuming it: a
    // resident buffer makes that a plain look at bufferOffset, advancing past it only when it is itself part
    // of the number — no mark/reset needed the way an InputStream peek would require.
    private void continueNumberContent()
    {
        boolean complete = false;
        while (!complete && !starved)
        {
            final int c = bufferOffset >= bufferLimit ? -1 : peekByte();
            if (c < 0)
            {
                // terminal window: its end ends the number; otherwise the window is exhausted mid-number
                starved = !eof();
                complete = eof();
            }
            else
            {
                if (c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')
                {
                    takeByte(c);
                    validateNumberChar(c);
                    countChar();
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
            throw new JsonParsingException("Invalid JSON number: " + lexeme(), null);
        }
    }

    private String lexeme()
    {
        return viewStart != NO_VIEW && !lens ? buffer.getStringWithoutLengthUtf8(viewStart, bufferOffset - viewStart) : "";
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

    private void enterScope(
        int c)
    {
        escapeArmed = false;

        if (c != '"')
        {
            throw new JsonParsingException("escaped scope requires a string value, found " + describe(c), null);
        }

        scalarSegment = false;
        scopeDepth = pathDepth;
        state = ParseState.ESC_START;
        lens = true;
        scopeEnded = false;
        scopeStart = streamOffset;
        lensIndex = 0;
        lensCount = 0;
        pendingEvent = JsonEvent.START_ESCAPED;
    }

    private void consumeScopeValue()
    {
        final int c = skipWhitespace();

        if (!starved)
        {
            parseValue(c);
        }
        else if (scopeEnded)
        {
            if (streamOffset != scopeStart)
            {
                throw new JsonParsingException("Incomplete document in escaped string", null);
            }

            exitScope();
        }
    }

    private void consumeScopeEnd()
    {
        final int c = skipWhitespace();

        if (!starved)
        {
            throw new JsonParsingException("escaped string continues with " + describe(c), null);
        }
        else if (scopeEnded)
        {
            exitScope();
        }
    }

    private void exitScope()
    {
        lens = false;
        scopeEnded = false;
        lensIndex = 0;
        lensCount = 0;
        bufferOffset++;
        streamOffset++;
        column++;
        starved = false;
        state = returnState(scopeDepth);
        pendingEvent = JsonEvent.END_ESCAPED;
    }

    // how many bytes the byte just read took: the byte itself, or the whole of the escape that spells it
    private int lastUnitSize()
    {
        return lens ? lensSize : 1;
    }

    private void startToken()
    {
        final int back = lastUnitSize();

        valueStreamStart = streamOffset - back;
        fragmentStart = valueStreamStart;
        valueLine = line;
        valueColumn = column - back;
    }

    private boolean eof()
    {
        return terminalEof || scopeEnded;
    }

    private int readByte()
    {
        return lens ? readLensByte() : readPlainByte();
    }

    private int peekByte()
    {
        return lens ? peekLensByte() : buffer.getByte(bufferOffset) & 0xff;
    }

    private void takeByte(
        int c)
    {
        if (lens)
        {
            readLensByte();
        }
        else
        {
            bufferOffset++;
            streamOffset++;
            advance(c);
        }
    }

    // The next byte of the document the string holds: a byte of the string as it is, or a byte of the escape the
    // string spells, taking the escape whole so that a window ending inside one leaves it unread. The quote that
    // closes the string is the end of the document.
    private int readLensByte()
    {
        int c = -1;

        if (lensIndex < lensCount)
        {
            c = JsonEscapeLens.byteAt(lensUnit, lensIndex++) & 0xff;
        }
        else if (bufferOffset >= bufferLimit)
        {
            starved = true;
        }
        else
        {
            final int b = buffer.getByte(bufferOffset) & 0xff;

            if (b == '"')
            {
                scopeEnded = true;
                starved = true;
            }
            else if (b != '\\')
            {
                bufferOffset++;
                streamOffset++;
                advance(b);
                lensSize = 1;
                c = b;
            }
            else
            {
                final long unit = JsonEscapeLens.decode(buffer, bufferOffset, bufferLimit, terminalEof);

                if (unit == JsonEscapeLens.NEEDS_MORE)
                {
                    starved = true;
                }
                else
                {
                    final int size = JsonEscapeLens.size(unit);

                    bufferOffset += size;
                    streamOffset += size;
                    column += size;
                    lensSize = size;
                    viewPlain = false;
                    lensUnit = unit;
                    lensCount = JsonEscapeLens.count(unit);
                    lensIndex = 1;
                    c = JsonEscapeLens.byteAt(unit, 0) & 0xff;
                }
            }
        }

        return c;
    }

    // The next byte of the document without taking it, or -1 when the window ends before it is whole or the string
    // closes first.
    private int peekLensByte()
    {
        int c = -1;

        if (lensIndex < lensCount)
        {
            c = JsonEscapeLens.byteAt(lensUnit, lensIndex) & 0xff;
        }
        else
        {
            final int b = buffer.getByte(bufferOffset) & 0xff;

            if (b == '"')
            {
                scopeEnded = true;
            }
            else if (b != '\\')
            {
                c = b;
            }
            else
            {
                final long unit = JsonEscapeLens.decode(buffer, bufferOffset, bufferLimit, terminalEof);

                if (unit != JsonEscapeLens.NEEDS_MORE)
                {
                    c = JsonEscapeLens.byteAt(unit, 0) & 0xff;
                }
            }
        }

        return c;
    }

    private int readPlainByte()
    {
        int c;
        if (bufferOffset < bufferLimit)
        {
            c = buffer.getByte(bufferOffset) & 0xff;
            bufferOffset++;
            streamOffset++;
            advance(c);
        }
        else
        {
            c = -1;
            starved = true;
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
