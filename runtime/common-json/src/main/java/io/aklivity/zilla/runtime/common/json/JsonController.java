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
 * <p>
 * A terminal stage that splices a kept value's verbatim bytes also calls {@link #consumed(int)} after
 * each segment chunk, reporting how many <em>source</em> bytes it actually took, so the upstream advances
 * its {@code position()} by exactly that count and re-exposes the value remainder on resume. A mediating
 * stage forwards {@code consumed} to its own upstream, the same way it relays {@code segmentable}.
 */
public interface JsonController
{
    void segmentable();

    /**
     * Reports that {@code sourceBytes} source bytes of the current verbatim segment were consumed by the
     * terminal write, always on a UTF-8 character and JSON escape-sequence boundary. The upstream advances
     * its {@code position()} by this count so the next pull/resume re-exposes the value remainder. The
     * default is a no-op for upstreams that do not track segment consumption.
     */
    default void consumed(
        int sourceBytes)
    {
    }
}
