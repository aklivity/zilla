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
 * backed caller needs, implemented internally by {@code StreamingJsonGenerator} and obtained via
 * {@link StreamingJson#createGenerator()}.
 * <p>
 * Every inherited {@link JsonGenerator} method is redeclared with a covariant {@code
 * JsonGeneratorEx} return so the standard write methods chain fluently alongside the extensions.
 */
public interface JsonGeneratorEx extends JsonGenerator
{
    /**
     * (Re)targets the generator at {@code buffer} starting at {@code offset}, resetting all
     * context. Reuse a single instance per worker thread across values.
     */
    JsonGeneratorEx wrap(
        MutableDirectBuffer buffer,
        int offset);

    /**
     * Reports the number of bytes written since the last {@link #wrap(MutableDirectBuffer, int)}.
     */
    int length();

    /**
     * Emits a numeric literal verbatim, preserving the exact source lexeme (e.g. {@code -2.5e3})
     * without round-tripping through a {@link BigDecimal}.
     */
    JsonGeneratorEx writeNumber(
        String literal);

    /**
     * Splices a pre-encoded JSON value from {@code source} verbatim as the next value, with no
     * re-encoding.
     */
    JsonGeneratorEx writeRaw(
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

    @Override
    JsonGeneratorEx writeEnd();

    @Override
    JsonGeneratorEx write(
        String value);

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
