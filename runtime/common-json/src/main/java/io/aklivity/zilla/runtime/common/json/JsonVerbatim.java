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

import java.util.List;

import org.agrona.DirectBuffer;

/**
 * A bounded block of a coalesced <em>verbatim</em> run, pulled via {@link JsonSource#getVerbatim(int)} — a
 * structured segment. {@link #getSegment()} is the entire contiguous run of original source bytes the block
 * represents; {@link #getStructure()} is the ordered structural transcript of those bytes. The bytes already
 * carry their own braces, colons, commas, and whitespace, so a recipient splices {@code getSegment()} 1:1 and
 * walks {@code getStructure()} only to keep its own generator/parser state (depth, member occupancy, leading
 * separator) coherent across the copy — the steps are not a way to re-walk the block's contents, and a
 * recipient that needs to inspect or transform structure takes structured events instead (it does not opt in
 * via {@link JsonController#verbatim()}).
 * <p>
 * The block is bounded to a whole-token prefix of the run that fits the caller's byte limit, so the bytes and
 * the structure always agree on a token boundary. An <em>empty</em> block — {@link #getStructure()} empty and
 * {@link #getSegment()} zero length — signals the run is fully drained (nothing remains to pull). The instance
 * is non-owning and reused across calls, and {@link #getStructure()} is a read-only view over shared state, so
 * a caller must neither retain it past the current call nor attempt to mutate it.
 */
public interface JsonVerbatim
{
    /**
     * The ordered structural steps of this block's bytes — a read-only, non-owning view valid on-stack only. A
     * recipient applies each step to advance its own depth and member occupancy as the bytes splice through, and
     * consults the leading step (a {@link JsonStep#SEPARATOR}, or a member/element start without one) to decide
     * whether a displaced former-first member needs a synthesized leading separator. Empty when the run is drained.
     */
    List<JsonStep> getStructure();

    /**
     * The entire contiguous block of original source bytes — a non-owning, on-stack view spliced 1:1, pre-bounded
     * to the caller's free output space so it always fits. Zero length when the run is drained.
     */
    DirectBuffer getSegment();
}
