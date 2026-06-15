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
 * single instance per worker thread, calling {@link #wrap(DirectBuffer, int, int)} to borrow each frame's
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
     * Rewinds the parser to before the first event of a fresh top-level document, clearing all carried parse
     * state so a single instance can be reused across documents (one per worker thread). Call once per
     * document before {@link #wrap(DirectBuffer, int, int)}.
     */
    void reset();

    /**
     * A non-owning, on-stack {@link CharSequence} view of the current scalar token — the decoded content of
     * a {@code VALUE_STRING} (or key), or the lexeme of a {@code VALUE_NUMBER} — valid only until the parser
     * advances. The zero-copy peer of {@link #getString()} / {@link #getBigDecimal()}: a streaming caller
     * reads and re-encodes the value (or parses the number) without materializing a {@code String}. The
     * backing buffer is reused across tokens, so copy out anything needed beyond the current event. Valid for
     * a value that fits one window; a fragmented value is read via {@code getString()} fragments as before.
     */
    CharSequence getStringView();
}
