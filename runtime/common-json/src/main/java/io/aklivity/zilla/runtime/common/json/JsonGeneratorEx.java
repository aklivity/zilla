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
import java.math.BigInteger;

import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * A {@link JsonGenerator} extended with the streaming-to-buffer surface the standard
 * {@code jakarta.json.stream} contract lacks. This is the {@code *Ex} pattern for going beyond
 * JSON-P: a sub-interface of the standard type adding the extra methods a streaming, buffer-
 * backed caller needs, implemented internally by {@code JsonGeneratorImpl} and obtained via
 * {@link JsonEx#createGenerator()}.
 * <p>
 * Every inherited {@link JsonGenerator} method is redeclared with a covariant {@code
 * JsonGeneratorEx} return so the standard write methods chain fluently alongside the extensions.
 */
public interface JsonGeneratorEx extends JsonGenerator
{
    /**
     * Re-targets the generator at {@code buffer} starting at {@code offset} with a hard byte
     * {@code limit}, the bound a chunking driver watches via {@link #remaining()} to decide when to
     * drain and resume; {@code limit} must be supported by the buffer capacity. Structural context
     * (open object/array depth and pending separators) is preserved, so a value paused by
     * {@link JsonPipeline.Status#SUSPENDED} continues across the drain as one uninterrupted
     * serialization. For an unbounded value pass the buffer's capacity as {@code limit}. Reuse a
     * single instance per worker thread across values.
     */
    JsonGeneratorEx wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit);

    /**
     * Variant of {@link #wrap(MutableDirectBuffer, int, int)} that additionally selects {@code escape}
     * mode. When {@code escape} is {@code true} every byte the generator emits is escaped as JSON string
     * <em>content</em> as it is written — structural bytes ({@code &#123; &#125; : , [ ]}) and UTF-8
     * continuation bytes pass through, while {@code "}, {@code \}, and control characters are escaped.
     * This composes with the generator's existing value-escaping, so the whole output stream becomes the
     * escaped form of the document — exactly the inner content of a JSON-in-JSON string. The caller writes
     * the surrounding quotes and any outer envelope; the generator emits only the inner escaped content.
     * When {@code escape} is {@code false} the behavior is identical to the three-argument {@code wrap}.
     */
    JsonGeneratorEx wrap(
        MutableDirectBuffer buffer,
        int offset,
        int limit,
        boolean escape);

    /**
     * Clears the structural context (open object/array depth and pending separators) without
     * re-targeting the buffer, readying the instance for a fresh top-level value. Reuse across pooled
     * callers calls this — via the pipeline's {@code reset()} cascade — so an instance returned mid-value
     * does not leak open structure into the next value.
     */
    void reset();

    /**
     * Reports the number of bytes written since the last {@link #wrap}.
     */
    int length();

    /**
     * Bytes that may still be written before reaching the {@code limit} set at {@link #wrap}. A driver
     * checks this at an event boundary to decide whether to suspend (drain) before the next write.
     */
    int remaining();

    /**
     * Emits a numeric literal verbatim, preserving the exact source lexeme (e.g. {@code -2.5e3})
     * without round-tripping through a {@link BigDecimal}.
     */
    JsonGeneratorEx writeNumber(
        String literal);

    /**
     * Emits a numeric literal from a {@code CharSequence} verbatim without first materializing a
     * {@link String}, letting a streaming caller forward a non-owning char view of the lexeme.
     * Semantics match {@link #writeNumber(String)}.
     */
    JsonGeneratorEx writeNumber(
        CharSequence literal);

    /**
     * Splices a pre-encoded JSON value from {@code source} verbatim as the next value, with no
     * re-encoding.
     */
    JsonGeneratorEx writeRaw(
        DirectBuffer source,
        int index,
        int length);

    /**
     * Appends the continuation bytes of a value already begun by {@link #writeRaw} verbatim, with no
     * re-encoding and without emitting a structural separator. Used to splice a value delivered as
     * multiple fragments so the whole fragment run counts as a single value.
     */
    JsonGeneratorEx writeRawContinue(
        DirectBuffer source,
        int index,
        int length);

    /**
     * Appends up to {@code length} content bytes of a value verbatim from {@code source} starting at
     * {@code index}, with no structural separator (the value's leading separator is emitted once, before
     * its first segment). The write is <em>consumption-driven</em>: the generator copies as many
     * <em>source</em> bytes as fit the output bound — escaping them when in {@code escape} mode, where one
     * source byte may expand to several output bytes — and <em>returns the number of source bytes
     * consumed</em>. A driver fragments a value larger than what fits by writing what it can, draining when
     * the returned count is less than {@code length}, then continuing on resume from the unconsumed
     * remainder. In the non-escape case the returned count is {@code min(length, remaining())}.
     */
    int writeSegment(
        DirectBuffer source,
        int index,
        int length);

    @Override
    JsonGeneratorEx writeStartObject();

    @Override
    JsonGeneratorEx writeStartObject(
        String name);

    @Override
    JsonGeneratorEx writeStartArray();

    @Override
    JsonGeneratorEx writeStartArray(
        String name);

    @Override
    JsonGeneratorEx writeKey(
        String name);

    /**
     * Writes an object key from a {@code CharSequence} without first materializing a {@link String},
     * letting a streaming caller forward a non-owning char view (e.g. a key still buffered upstream)
     * straight to the wire. Semantics match {@link #writeKey(String)}; the chars are escaped and
     * encoded as they are read.
     */
    JsonGeneratorEx writeKey(
        CharSequence name);

    @Override
    JsonGeneratorEx writeEnd();

    @Override
    JsonGeneratorEx write(
        String value);

    /**
     * Writes a string value from a {@code CharSequence} without first materializing a {@link String},
     * the value-side counterpart to {@link #writeKey(CharSequence)}. Semantics match
     * {@link #write(String)}; the chars are escaped and encoded as they are read.
     */
    JsonGeneratorEx write(
        CharSequence value);

    @Override
    JsonGeneratorEx write(
        BigDecimal value);

    @Override
    JsonGeneratorEx write(
        BigInteger value);

    @Override
    JsonGeneratorEx write(
        int value);

    @Override
    JsonGeneratorEx write(
        long value);

    @Override
    JsonGeneratorEx write(
        double value);

    @Override
    JsonGeneratorEx write(
        boolean value);

    @Override
    JsonGeneratorEx writeNull();

    @Override
    JsonGeneratorEx write(
        String name,
        String value);

    @Override
    JsonGeneratorEx write(
        String name,
        BigInteger value);

    @Override
    JsonGeneratorEx write(
        String name,
        BigDecimal value);

    @Override
    JsonGeneratorEx write(
        String name,
        int value);

    @Override
    JsonGeneratorEx write(
        String name,
        long value);

    @Override
    JsonGeneratorEx write(
        String name,
        double value);

    @Override
    JsonGeneratorEx write(
        String name,
        boolean value);

    @Override
    JsonGeneratorEx writeNull(
        String name);

    @Override
    JsonGeneratorEx write(
        JsonValue value);

    @Override
    JsonGeneratorEx write(
        String name,
        JsonValue value);
}
