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
     * Config key (for {@link JsonEx#createParser(java.util.Map)}) whose {@link Integer} value caps the
     * number of decoded chars retained for a single value or key. A consumer that needs a value whole
     * declines its fragments (consumed(0)) so the source accumulates them; this bounds that accumulation
     * so an adversarially large value fails closed with a {@link JsonPipeline.Status#REJECTED} rather than
     * exhausting memory. Absent ⇒ a generous default.
     */
    String MAX_VALUE_SIZE = "io.aklivity.zilla.runtime.common.json.parser.maxValueSize";

    /**
     * Borrows {@code buffer} as the input window for the next pump, the half-open range
     * {@code [offset, limit)}. The buffer is read in place for the duration of the pump.
     * <p>
     * The window is also the fragmentation bound: a value that fits the window is delivered whole; a
     * value whose own bytes fill the window without completing is delivered as fragments — the same
     * structured event repeated with {@code deferredBytes()} {@code true} until the value completes.
     * The caller carries any unconsumed tail ({@code remaining()} bytes) into the next window, so a
     * value that merely straddles a window boundary is reassembled whole. Consequently a single whole
     * {@code getString()} and {@code getInt()}/{@code getLong()} are valid only for a value that fits
     * one window; a larger value is read by accumulating {@code getString()} fragments (or splicing
     * {@code getSegment()}), and a fragmented number via {@code getBigDecimal()}/{@code getBigInteger()}.
     */
    JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int limit);

    /**
     * Wraps the next input window {@code [offset, limit)} of a chunked feed; {@code last} marks the final
     * window, so its EOF is the terminal delimiter (completing a trailing scalar, rejecting a truncated
     * value) rather than a frame boundary with more bytes to come. The three-argument
     * {@link #wrap(DirectBuffer, int, int)} is the {@code last == true} shorthand.
     */
    JsonParserEx wrap(
        DirectBuffer buffer,
        int offset,
        int limit,
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
     * The number of bytes at the tail of the current window not yet consumed — what the caller retains and
     * re-presents, contiguous, at the front of the next window. The window-relative peer of the absolute
     * {@code getLocation().getStreamOffset()}: a caller buffering across windows keeps exactly this many bytes
     * without tracking the window's absolute base. Reported at a whole-token boundary, so on starvation it is
     * the length of the partial trailing unit (e.g. a multibyte character split across the window); zero once
     * the window is fully consumed.
     */
    int remaining();

    /**
     * Whether another {@link #nextEvent()} is available — {@code true} until the document's
     * {@link JsonEvent#END_DOCUMENT} has been pulled, {@code false} when the window is consumed mid-document
     * (more input is needed).
     */
    boolean hasNextEvent();

    /**
     * How {@link #nextEvent(Mode)} delivers the value at the current boundary: {@code STRUCTURED} emits the
     * typed event stream; {@code SEGMENTED} opts the value (and its descendants) in to verbatim delivery as a
     * {@link JsonEvent#segmented()} run, read via {@link #getSegment()}. Best-effort and consulted only at a
     * value boundary; for every other event it is ignored.
     */
    enum Mode
    {
        STRUCTURED,
        SEGMENTED
    }

    /**
     * Advances and returns the next {@link JsonEvent} in {@link Mode#STRUCTURED} mode.
     */
    default JsonEvent nextEvent()
    {
        return nextEvent(Mode.STRUCTURED);
    }

    /**
     * Advances and returns the next {@link JsonEvent} of the pipeline event stream — a superset of the
     * standard {@code jakarta.json.stream.JsonParser.Event} adding document framing and segment delivery. At a
     * value boundary {@code mode} chooses structured events ({@link Mode#STRUCTURED}) or a verbatim segment run
     * ({@link Mode#SEGMENTED}); elsewhere it is ignored. This is the mode-driven peer of a stage's
     * {@link JsonController#segmentable()}, so the segment request need not narrow onto the parser surface.
     */
    JsonEvent nextEvent(
        Mode mode);

    /**
     * A non-owning, on-stack {@link CharSequence} view of the current scalar token — a string value, a
     * number lexeme, or an object key — valid until the parser advances. The zero-copy peer of
     * {@link #getString()}: a streaming caller reads or matches the value (or key) without materializing a
     * {@code String}. The backing buffer is reused across events, so copy out anything needed beyond the
     * current event. This promotes the {@link JsonSource#getStringView()} accessor onto the parser surface
     * so a caller holding a {@code JsonParserEx} reads scalars without narrowing to {@link JsonSource}.
     */
    CharSequence getStringView();

    /**
     * Valid only when the current event is {@link JsonEvent#segmented()}; non-owning view of the current
     * contiguous slice, valid on-stack only. The {@link JsonSource#getSegment()} accessor promoted onto the
     * parser surface so a pipeline exposes it to a stage without narrowing to {@link JsonSource}.
     */
    DirectBuffer getSegment();

    /**
     * Bounded pull of the coalesced <em>verbatim</em> run — the original source bytes parsed since the last
     * {@code getVerbatim} call — returning at most {@code limit} bytes (a non-owning, on-stack view) and
     * advancing the verbatim cursor by exactly that, so the next call continues the run with no gap or
     * overlap. Unlike {@link #getSegment()} this is valid alongside the structured event stream (the parser
     * keeps delivering typed events; this reads their underlying bytes), letting a stage inspect structure
     * yet reproduce the input byte-for-byte. The pull is denominated in source bytes and the splice is 1:1,
     * so the caller pre-bounds it to its free output space and never needs {@link JsonController#consumed(int)}
     * to report partial progress. The {@link JsonSource#getVerbatim(int)} accessor promoted onto the parser
     * surface.
     */
    DirectBuffer getVerbatim(
        int limit);

    /**
     * Drops the value at the current member boundary — valid only when the current event is a
     * {@link JsonEvent#KEY_NAME} — advancing the parser past the whole member (key, separator, value) and the
     * verbatim cursor past its bytes so they are never emitted, folding in the leading-separator trim a prune
     * needs to keep the surviving siblings well-formed. The {@link JsonSource#skipValue()} accessor promoted
     * onto the parser surface.
     */
    void skipValue();

    /**
     * The structured {@link JsonEvent} the parser is currently positioned on — the last one delivered by
     * {@link #nextEvent(Mode)} — even when a stage forwards it to a sink relabeled as {@link JsonEvent#VERBATIM}.
     * A terminal sink copying a verbatim run reads this to drive its generator's structural state as the bytes
     * splice through. The {@link JsonSource#event()} accessor promoted onto the parser surface.
     */
    JsonEvent event();

    /**
     * Whether the member or element at the current event was preceded by a separator in the original source. The
     * {@link JsonSource#separated()} accessor promoted onto the parser surface.
     */
    boolean separated();

    /**
     * Whether the current value has bytes still deferred to later events — {@code true} while more of this same
     * value follows (the value is being streamed across input frames because it exceeds the input window),
     * {@code false} when this event completes it. The {@link JsonSource#deferredBytes()} accessor promoted onto
     * the parser surface.
     */
    boolean deferredBytes();

    /**
     * Reports {@code sourceBytes} source bytes consumed by a verbatim segment write so the parser advances
     * its {@code getLocation().getStreamOffset()} and re-exposes the value remainder on resume — the
     * output-side pushback that lets a terminal sink stream a length-delimited value without keeping its own
     * offset. The default does nothing, for a parser whose values are never delivered in bounded pieces.
     */
    default void consumed(
        int sourceBytes)
    {
    }
}
