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
package io.aklivity.zilla.runtime.common.avro;

/**
 * The per-edge control handle an {@link AvroStream} stage uses to steer its immediate upstream. A
 * stage calls {@link #segmentable()} on {@link AvroEvent#START_MESSAGE} to opt in to receiving the
 * current datum as a verbatim segment run rather than as structured events. Best-effort: the upstream
 * may honor it (subsequent events satisfy {@link AvroEvent#segmented()}) or decline (structured events
 * follow); the caller determines which by observing the events that follow. A mediating
 * {@link AvroTransform} supplies its own {@code AvroController} to its downstream; a non-mediating
 * stage passes its own through.
 */
public interface AvroController
{
    void segmentable();

    /**
     * Reports {@code sourceBytes} payload bytes consumed by a bounded value write, so the upstream advances
     * its segment cursor and re-exposes the value's unconsumed remainder on resume — the output-side
     * back-pressure pushback that lets a terminal sink stream a length-delimited value without keeping its
     * own offset. The default does nothing for a non-mediating edge that forwards no value.
     */
    default void consumed(
        int sourceBytes)
    {
    }
}
