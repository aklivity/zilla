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

import jakarta.json.JsonException;
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

    // The consumer has taken consumed chars of the pending string fragment, which is read from the window: the
    // position moves to the first byte it left, so the caller re-presents the rest with the next window.
    void positionAfter(
        int consumed);

    // Whether the consumer left part of the pending string fragment.
    boolean declined();

    // The window is being replaced, so the part of the fragment the consumer left is no longer pending.
    void acceptDeclined();

    long streamOffset();

    long line();

    long column();

    long valueStreamStart();

    long valueStreamEnd();

    boolean done();

    // Arms the string value that follows to be read as a document of its own, in place: the tokenizer reads the
    // string with its escapes undone, delivering START_ESCAPED and END_ESCAPED as markers around the events of the
    // document. A tokenizer that cannot read a string as a document rejects.
    default void escape()
    {
        throw new JsonException("escaped scope not supported");
    }

    // Whether an event, or a scope marker, is waiting to be taken.
    default boolean pending()
    {
        return event() != null;
    }

    // The scope marker waiting to be taken, or null.
    default JsonEvent marker()
    {
        return null;
    }

    default void clearMarker()
    {
    }

    // Whether the tokenizer is reading the document of an escaped scope.
    default boolean inScope()
    {
        return false;
    }

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
