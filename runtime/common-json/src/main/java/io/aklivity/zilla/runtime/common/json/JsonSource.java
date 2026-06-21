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

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

import org.agrona.DirectBuffer;

/**
 * Immutable, read-only view of the value observed at the current event as a {@link JsonStream}
 * pipeline pumps events through its stages. A {@code JsonSource} exposes only the accessors a stage
 * needs to read the current scalar (and its location for diagnostics); it has no {@code next()} —
 * advancing the cursor is the parser's job, not a stage's — so a stage cannot disturb the pump.
 */
public interface JsonSource
{
    String getString();

    /**
     * Non-owning, on-stack char view of the current scalar token — a decoded string value, a number
     * lexeme, or an object key — valid only for the duration of the current event. The allocation-free
     * counterpart to {@link #getString()} for stages that read or re-emit the value (or key) without
     * retaining it.
     */
    CharSequence getStringView();

    BigDecimal getBigDecimal();

    boolean isIntegralNumber();

    /**
     * The current number as an {@code int}. Valid only on an integral number that fits one window;
     * a fragmented number throws (read it via {@link #getBigDecimal()} instead).
     */
    int getInt();

    /**
     * The current number as a {@code long}. Valid only on an integral number that fits one window;
     * a fragmented number throws (read it via {@link #getBigDecimal()} instead).
     */
    long getLong();

    JsonLocation getLocation();

    /**
     * Valid only when the current event is {@link JsonEvent#segmented()}; non-owning view of the
     * current contiguous slice, valid on-stack only.
     */
    DirectBuffer getSegment();

    /**
     * Bounded pull of the coalesced <em>verbatim</em> run as a {@link JsonVerbatim} block: the original source
     * bytes parsed since the last call, bounded to the whole-token prefix that fits {@code limit}, advancing the
     * verbatim cursor by exactly that. The returned block's {@link JsonVerbatim#getSegment()} is the contiguous
     * bytes and {@link JsonVerbatim#getStructure()} their structural transcript, always agreeing on a token
     * boundary. An empty block (empty structure, zero-length segment) signals the run is fully drained. Valid on
     * a {@link JsonEvent#isVerbatim()} or {@link JsonEvent} {@code STRUCTURED} event when the consumer has opted
     * in via {@link JsonController#verbatim()}; lets a stage that inspects structure (e.g. a validator) reproduce
     * the input byte-for-byte without the canonical re-serialization a structured replay imposes. The splice is
     * 1:1, so the caller pre-sizes {@code limit} to its free output space and the returned bytes always fit — no
     * {@link JsonController#consumed(int)} report is needed on this path. The returned instance is non-owning and
     * reused across calls (valid on-stack only). A source that does not track verbatim runs rejects this.
     */
    JsonVerbatim getVerbatim(
        int limit);

    /**
     * Drops the value at the current member boundary — valid only when the current event is a
     * {@link JsonEvent#KEY_NAME} (an object member) — advancing the source past the whole member: its key, the
     * separator, and the value (a scalar, or a container to its matching close). The dropped member's
     * sub-events are consumed internally, so the calling stage never sees them. The verbatim cursor advances
     * past the dropped bytes so they are never emitted, and the source folds in the leading-separator trim a
     * prune needs: dropping the first surviving member of a container defers trimming the next kept sibling's
     * leading separator, so the survivors stay well-formed (e.g. dropping {@code a} from {@code {"a":1,"b":2}}
     * yields {@code {"b":2}}, not {@code {,"b":2}}). Like {@link #getVerbatim(int)} this requires the consumer
     * to have opted in via {@link JsonController#verbatim()}; a source that does not track verbatim runs rejects
     * it.
     */
    void skipValue();

    /**
     * Whether the current value has bytes still deferred to later events — {@code true} while more of
     * this same value follows (the value is being streamed across input frames because it exceeds the
     * input window), {@code false} when this event completes it. A JSON string is quote-delimited, so
     * this is a "more is coming" signal, not a remaining byte count.
     */
    boolean deferredBytes();
}
