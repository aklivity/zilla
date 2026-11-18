/*
 * Copyright 2021-2026 Aklivity Inc
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
 * A chunk of a coalesced <em>verbatim</em> run, pulled via {@link JsonSource#getVerbatim(int)}. The run is the
 * original source bytes parsed since the last pull drained it. {@link #getSegment()} is the next contiguous piece
 * of those bytes, cut wherever the caller's limit falls, not on a token boundary. The remaining accessors
 * describe the <em>whole</em> run, and every chunk of a run repeats the same description, so a recipient decides
 * when to act on it: a recipient copying a run in pieces applies the structure once, typically when
 * {@link #getOffset()} is zero, before it takes any other event. The bytes already carry their own braces,
 * colons, commas, and whitespace, so a recipient splices the segment 1:1 and uses the description only to keep
 * its own generator state (depth, member occupancy, leading separator) coherent across the copy. It is not a
 * way to re-walk the run, and a recipient that needs to inspect or transform structure takes structured events
 * instead (it does not opt in via {@link JsonController#verbatim()}).
 * <p>
 * The description is relative to the container stack the recipient holds when the run starts. The run first
 * closes {@link #getClosed()} containers that were open before it started, leaving the container the run is
 * <em>in</em>: a member or element the run adds to that container is reported by {@link #isOccupied()} and
 * {@link #isBareValue()}. It then leaves {@link #getOpened()} containers open above it, outermost first, whose
 * kinds and occupancy are the bits of {@link #getOpenedArrays()} and {@link #getOpenedOccupied()}.
 * <p>
 * A chunk of a run that is not yet drained shares the run with the chunks that follow. An <em>empty</em> chunk,
 * a zero-length segment with no {@link #getLeading() leading} step, signals that nothing remains to pull. The
 * instance is non-owning and reused across calls, so a caller must not retain it past the current call.
 */
public interface JsonVerbatim
{
    /**
     * The next contiguous piece of the run's original source bytes, a non-owning, on-stack view spliced 1:1 and
     * pre-bounded to the caller's free output space so it always fits. Zero length when the run is drained.
     */
    DirectBufferEx getSegment();

    /**
     * The position of {@link #getSegment()} within its run, in bytes. Zero for the first chunk of a run.
     */
    int getOffset();

    /**
     * What the run begins with, or {@code null} when the run holds no token: bytes between tokens drained at
     * the end of a window. {@link JsonStep#SEPARATOR} when a comma precedes the run's first token, so the bytes
     * carry it; otherwise the kind of that token, so a recipient can tell whether the run begins a member or
     * element that was first in the source and so carries no comma of its own.
     */
    JsonStep getLeading();

    /**
     * The number of containers open before the run that the run closes.
     */
    int getClosed();

    /**
     * The number of containers the run leaves open at its end, above the container it is in.
     */
    int getOpened();

    /**
     * Bit {@code n} is set when the {@code n}th container the run leaves open, outermost first, is an array.
     */
    long getOpenedArrays();

    /**
     * Bit {@code n} is set when the {@code n}th container the run leaves open, outermost first, holds a member
     * or element.
     */
    long getOpenedOccupied();

    /**
     * Whether the run adds a member by naming a key in the container it is in.
     */
    boolean isOccupied();

    /**
     * Whether the run starts a value in the container it is in without naming a key first: an element when that
     * container is an array, otherwise the value of a key named before the run.
     */
    boolean isBareValue();

    /**
     * The last member-level token of the run: {@link JsonStep#KEY_NAME} when the run ends after a key whose
     * value is still to come, {@link JsonStep#VALUE} when a value was the last to start, or {@code null} when
     * the run holds neither.
     */
    JsonStep getLast();

    /**
     * Whether the colon after the key the run ends on is among the run's bytes. Meaningful only when
     * {@link #getLast()} is {@link JsonStep#KEY_NAME}.
     */
    boolean isColonRead();
}
