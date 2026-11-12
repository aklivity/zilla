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
package io.aklivity.zilla.runtime.common.json;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

// Pluggable scan engine behind JsonParserImpl, selected per JVM via JsonTokenizerFactory. A tokenizer owns the
// JSON grammar's resumable state machine over a caller-supplied, buffer-resident input window; the parser drives
// it token by token and never reaches into its internals. The split exists so an alternative implementation
// (e.g. a vectorized one) can replace the scanning engine without JsonParserImpl changing at all.
public interface JsonTokenizer
{
    void reset();

    // Wraps the next input window [offset, limit) to scan; last == true marks the final window, so its end is
    // the terminal delimiter (completing a trailing scalar, rejecting a truncated value) rather than a frame
    // boundary with more bytes still to come.
    void wrap(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last);

    // Sets the fragmentation bound for the wrapped window: a value whose own bytes reach it without completing
    // is delivered as fragments instead of reassembled across windows. Kept apart from wrap() because a caller
    // feeding this tokenizer through a compatibility adapter over an unbounded source needs an unbounded window
    // (Integer.MAX_VALUE) while still wrapping each buffer-resident chunk normally.
    void window(
        int length);

    boolean advance();

    JsonEvent event();

    void clearEvent();

    // The current key, string or number as a range of the wrapped window, so its chars are read in place and never
    // copied: where it begins, how many encoded bytes it spans, how many decoded chars it holds, and whether each
    // char is the one byte that encodes it, with no escape or multi-byte character. The length is -1 when the token
    // is not read in place, as a string streamed as segments. Valid until the tokenizer advances or the window
    // is replaced.
    int tokenOffset();

    int tokenLength();

    int tokenChars();

    boolean tokenPlain();

    // The consumer left the last bytes of the pending string fragment, which is read from the window: the position
    // moves back to the first byte it left, so the caller re-presents the rest with the next window.
    void decline(
        int bytes);

    // Whether the consumer left part of the pending string fragment.
    boolean declined();

    long streamOffset();

    long line();

    long column();

    long valueStreamStart();

    long valueStreamEnd();

    boolean done();

    // Arms the string value that follows to be read as a document of its own, in place: the tokenizer reads the
    // string with its escapes undone, delivering START_ESCAPED and END_ESCAPED as markers around the events of the
    // document.
    void escape();

    // Whether the tokenizer is reading the document of an escaped scope.
    boolean inScope();

    long documentEndOffset();

    boolean inObjectContext();

    boolean inArrayContext();

    boolean memberSeparated();

    boolean separatorBefore();

    boolean fragmenting();

    boolean stringVerbatim();

    void segmenting(
        boolean segmenting);

    void scalarSegment(
        boolean scalarSegment);

    // Ends the current document and begins the next top-level value at the current stream position, without
    // rewinding streamOffset/line/column the way reset() does — for a multi-document session over one feed.
    void nextDocument();

    // Takes a mark at the current whole-token boundary for a later rewind(); throws IllegalStateException if
    // called mid-token (a pending event, a resume in progress, or a fragment still open).
    void mark();

    // Returns to the last mark(): the scan resumes at the marked byte with the state it had there. Throws
    // IllegalStateException if there is no mark, if the mark's window has since been replaced by a later
    // wrap(), or if the parse has already left the container the mark was taken in.
    void rewind();

    // Gives back the separator and whitespace scanned past the last delivered token when the window ended
    // before the next token began, so only whole tokens are ever consumed and the caller re-presents the rest
    // with the next window.
    void rewindLookahead(
        long offset,
        long line,
        long column);
}
