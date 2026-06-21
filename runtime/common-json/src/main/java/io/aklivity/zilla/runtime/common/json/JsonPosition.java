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
 * A container-anchored insertion point, read via {@link JsonSource#getPosition()} on a verbatim&rarr;inject
 * transition so a generator can seed its structural state (open object/array depth and pending separators)
 * without emitting — the brackets the verbatim copy already wrote are not re-emitted. It is the ordered list
 * of {@link JsonStep}, root ({@code index 0}) to the insertion point ({@code index depth()-1}), one step per
 * descended level. Every non-terminal step is necessarily a {@code CONTINUE_*} (descending into a child makes
 * each ancestor non-empty); only the terminal step's kind and occupancy are load-bearing for a single
 * injection. The instance is non-owning and reused across calls (valid on-stack only), like the other
 * {@link JsonSource} accessors.
 */
public interface JsonPosition
{
    /**
     * The number of descended container levels, root to insertion point; a generator seeds one frame per level.
     */
    int depth();

    /**
     * The {@link JsonStep} at {@code index} (0 = root, {@code depth()-1} = the insertion point's container).
     */
    JsonStep step(
        int index);
}
