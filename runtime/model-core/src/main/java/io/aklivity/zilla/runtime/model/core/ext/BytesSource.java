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
package io.aklivity.zilla.runtime.model.core.ext;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Immutable, read-only view of the value bytes observed at the current {@link BytesEvent} as a
 * {@code bytes} pipeline pumps events through its stages.
 * <p>
 * A {@code BytesSource} has no cursor-advancing method, so a stage cannot disturb the pump. A stage that
 * substitutes a value feeds its downstream a {@code BytesSource} of its own rather than mutating this one.
 * </p>
 */
public interface BytesSource
{
    /**
     * Non-owning, on-stack view of the current contiguous run of value bytes, its {@code capacity()} being
     * the run length. Valid only when the current event is {@link BytesEvent#segmented()}, and only for the
     * duration of the call it was read in; a stage that needs the bytes beyond that must copy them out.
     *
     * @return the current run of value bytes
     */
    DirectBufferEx getSegment();
}
