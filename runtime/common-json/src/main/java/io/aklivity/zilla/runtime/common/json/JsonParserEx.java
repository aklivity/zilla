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
package io.aklivity.zilla.runtime.common.json;

import jakarta.json.stream.JsonParser;

import org.agrona.DirectBuffer;

/**
 * A {@link JsonParser} extended with the streaming-over-buffers surface the standard
 * {@code jakarta.json.stream} contract lacks. This is the {@code *Ex} pattern for going beyond JSON-P:
 * a sub-interface of the standard type that drives a {@link JsonStream} pipeline.
 * <p>
 * Obtain an instance from {@link JsonEx#createParser()} (the implementation is internal). Reuse a
 * single instance per thread, calling {@link #wrap(DirectBuffer, int, int)} to borrow each frame's
 * buffer before pumping.
 */
public interface JsonParserEx extends JsonParser
{
    /**
     * Borrows {@code buffer} as the input window for the next pump, starting at {@code offset} for
     * {@code length} bytes. The buffer is read in place for the duration of the pump.
     * <p>
     * The window is also the fragmentation bound: a value that fits the window is delivered whole; a
     * value whose own bytes fill the window without completing is delivered as fragments — the same
     * structured event repeated with {@code deferredBytes()} {@code true} until the value completes.
     * The caller carries any unconsumed tail (up to {@code position()}) into the next window, so a
     * value that merely straddles a window boundary is reassembled whole. Consequently a single whole
     * {@code getString()} and {@code getInt()}/{@code getLong()} are valid only for a value that fits
     * one window; a larger value is read by accumulating {@code getString()} fragments (or splicing
     * {@code getSegment()}), and a fragmented number via {@code getBigDecimal()}/{@code getBigInteger()}.
     */
    JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int length);

    /**
     * Wraps the next input window of a chunked feed; {@code last} marks the final window, so its EOF is the
     * terminal delimiter (completing a trailing scalar, rejecting a truncated value) rather than a frame
     * boundary with more bytes to come. The three-argument {@link #wrap(DirectBuffer, int, int)} is the
     * {@code last == true} shorthand.
     */
    JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int length,
        boolean last);

    /**
     * Rewinds the parser to before the start of a document, clearing all tokenizer state (parse stack,
     * scratch, stream offset) so the next {@link #wrap(DirectBuffer, int, int)} begins a fresh top-level
     * value. A reused instance calls this once per document; it is not called between the windows of a
     * single document (those continue via {@code wrap}). Distinct from a window swap, which preserves
     * in-flight state.
     */
    void reset();

    /**
     * The number of input bytes committed since the document began — always at a whole-token boundary. On
     * starvation (a window consumed before the value completes) everything at or after this position is the
     * unconsumed tail the caller retains and re-presents, contiguous, in the next window.
     */
    long position();

    /**
     * Whether another {@link #nextEvent()} is available — {@code true} until the document's
     * {@link JsonEvent#END_DOCUMENT} has been pulled, {@code false} when the window is consumed mid-document
     * (more input is needed).
     */
    boolean hasNextEvent();

    /**
     * Advances and returns the next {@link JsonEvent} of the pipeline event stream — a superset of the
     * standard {@code jakarta.json.stream.JsonParser.Event} adding document framing and segment delivery.
     */
    JsonEvent nextEvent();

    /**
     * A non-owning, on-stack {@link CharSequence} view of the current scalar token — a string value, a
     * number lexeme, or an object key — valid until the parser advances. The zero-copy peer of
     * {@link #getString()}: a streaming caller reads or matches the value (or key) without materializing a
     * {@code String}. The backing buffer is reused across events, so copy out anything needed beyond the
     * current event. This promotes the {@link JsonSource#getStringView()} accessor onto the parser surface
     * so a caller holding a {@code JsonParserEx} reads scalars without narrowing to {@link JsonSource}.
     */
    CharSequence getStringView();
}
