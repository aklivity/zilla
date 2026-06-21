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
 * The per-edge control handle a {@link JsonStream} stage uses to steer its immediate upstream. A stage
 * calls {@link #segmentable()} at a value boundary to opt in to receiving the current value (and its
 * descendants) as a segment rather than as structured events. Best-effort: the upstream may honor it
 * (subsequent events satisfy {@link JsonEvent#segmented()}) or decline (structured events follow). The
 * caller determines which by observing the events that follow — there is no return value, because a
 * mediating upstream cannot know the outcome at call time. A mediating {@link JsonTransform} supplies
 * its own {@code JsonController} to its downstream; a non-mediating stage passes its own through.
 */
public interface JsonController
{
    void segmentable();

    /**
     * Opts in to receiving {@link JsonEvent#isVerbatim()} events: the caller is willing to read the current
     * value (and the run it coalesces into) as original source bytes via {@link JsonSource#getVerbatim(int)}
     * <em>alongside</em> the structured event stream, rather than re-serializing it canonically. Peer of
     * {@link #segmentable()} but additive, not substitutive — a mediating stage (e.g. a validator) absorbs the
     * request upstream (it still takes structured events to inspect) and re-asserts it downstream (it emits
     * verbatim events to its sink). Best-effort and demand-gated; when unset the upstream does no verbatim
     * tracking. The default does nothing, for an upstream that does not retain source bytes.
     */
    default void verbatim()
    {
    }

    /**
     * Reports {@code sourceBytes} source bytes consumed by a bounded value write where output width differs
     * from source width (a structured scalar the generator quotes/escapes, or a number lexeme it emits), so
     * the upstream advances its consumed cursor and re-exposes the remainder on resume. The 1:1 raw-copy paths
     * ({@link JsonSource#getSegment()}, {@link JsonSource#getVerbatim(int)}) are pre-bounded pulls and never
     * call this.
     */
    default void consumed(
        int sourceBytes)
    {
    }
}
