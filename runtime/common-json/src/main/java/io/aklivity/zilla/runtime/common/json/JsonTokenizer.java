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

import jakarta.json.stream.JsonParser;

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

    JsonParser.Event event();

    void clearEvent();

    String stringValue();

    // Non-allocating view of the current string token (a deferred key or a readable value), valid while the
    // decoded chars are still retained because nobody has materialized them yet.
    CharSequence stringView();

    // The parser reports, before a resuming advance, how many chars of the current fragment the consumer took,
    // so resume keeps the unconsumed remainder rather than discarding the whole fragment.
    void markScratchConsumed(
        int consumed);

    long streamOffset();

    long line();

    long column();

    long valueStreamStart();

    long valueStreamEnd();

    boolean done();

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

    // Takes back a key whose first fragment was delivered and declined, so the caller re-presents it: the scan
    // goes back to priorEnd (the end of the token ahead of the key) when priorEnd is still available (at or
    // after floor), or to the key's own start otherwise.
    void rewindKey(
        long priorEnd,
        long priorLine,
        long priorColumn,
        long floor);

    // Gives back the separator and whitespace scanned past the last delivered token when the window ended
    // before the next token began, so only whole tokens are ever consumed and the caller re-presents the rest
    // with the next window.
    void rewindLookahead(
        long offset,
        long line,
        long column);
}
