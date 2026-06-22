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

/**
 * One structural step of a {@link JsonVerbatim} block — the vocabulary a recipient needs to keep its own
 * generator/parser state coherent as it splices the block's bytes, nothing more. It is deliberately not the
 * full {@link JsonEvent} set: a block is an opaque contiguous run of original source bytes, and these steps
 * describe its structure so the recipient can repair depth and member occupancy after a blind byte copy —
 * they are not a re-walkable parse of the block's contents.
 * <p>
 * The five scalar {@code VALUE_*} events of {@link JsonEvent} collapse to a single {@link #VALUE}: a recipient
 * copying bytes never needs a scalar's type, only that a value occupies a slot. {@link #SEPARATOR} marks a
 * value separator present in the block's bytes; only a block's leading/trailing separator is consulted (to
 * stitch an injected value at a block seam without doubling or dropping a comma), interior separators ride in
 * the bytes and move no state. There is no {@code START_DOCUMENT}/{@code END_DOCUMENT} — framing is not part of
 * a spliced run.
 */
public enum JsonStep
{
    START_OBJECT,
    END_OBJECT,
    START_ARRAY,
    END_ARRAY,
    KEY_NAME,
    VALUE,
    SEPARATOR
}
