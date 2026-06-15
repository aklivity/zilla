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
     * Non-owning, on-stack char view of the current scalar value (a number lexeme or a decoded string),
     * valid only for the duration of the current event. The allocation-free counterpart to
     * {@link #getString()} for stages that read or re-emit the value without retaining it.
     */
    CharSequence getStringView();

    // valid only on a key event; non-owning, on-stack char view of the current key
    CharSequence getKey();

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
     * Whether the current value has bytes still deferred to later events — {@code true} while more of
     * this same value follows (the value is being streamed across input frames because it exceeds the
     * input window), {@code false} when this event completes it. A JSON string is quote-delimited, so
     * this is a "more is coming" signal, not a remaining byte count.
     */
    boolean deferredBytes();
}
