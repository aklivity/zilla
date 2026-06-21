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
 * The ordered structural steps a verbatim run represents — the {@link JsonEvent}s the run's bytes would have
 * produced if generated structurally, each with its source occupancy. A terminal sink reads this for a run via
 * {@link JsonSource#getSteps()} and hands it to {@link JsonGeneratorEx#writeVerbatim(org.agrona.DirectBuffer,
 * int, int, JsonSteps)} so the generator advances its own structural state (depth, member occupancy, after-key)
 * across the whole run as the bytes splice through, and synthesizes a leading separator for a displaced
 * former-first member. Under per-event forwarding a run is a single step; a coalesced run spans several. The
 * instance is non-owning and reused across calls (valid on-stack only), like the other {@link JsonSource}
 * accessors.
 */
public interface JsonSteps
{
    /**
     * The number of structural steps in the run (≥ 0).
     */
    int count();

    /**
     * The {@link JsonEvent} of the step at {@code index} (0 = first in the run).
     */
    JsonEvent step(
        int index);

    /**
     * Whether the member or element at the step at {@code index} was preceded by a separator in the original
     * source — source occupancy, robust to how the run's bytes are chunked. Meaningful only for a member/element
     * start step; a generator consults it to decide whether a displaced former-first member needs a synthesized
     * leading separator.
     */
    boolean separated(
        int index);
}
