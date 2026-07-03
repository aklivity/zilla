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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * The {@link CharSequence} returned by {@link JsonTokenizer#stringView()}, with an optional hint for a
 * caller that can do better than character-by-character access.
 * <p>
 * {@link #getSegment()} is the contiguous run of original source bytes this view's characters were decoded
 * from, when such a run exists and corresponds 1:1 to this view's content — {@code null} when the
 * characters were produced by transforming the source bytes during decoding (an escape sequence, a
 * multi-byte UTF-8 sequence folded into canonical form, etc.) and so have no single corresponding byte
 * range in the source document. A caller able to exploit a direct source-byte range — a generator splicing
 * an unmodified value straight through instead of re-encoding it — treats this as an opportunistic hint,
 * not a requirement, and falls back to ordinary {@link CharSequence} access whenever it is {@code null}.
 * <p>
 * The instance is non-owning and reused across calls; a caller must not retain it, or any segment obtained
 * from it, past the current call.
 */
public interface JsonStringView extends CharSequence
{
    DirectBufferEx getSegment();
}
