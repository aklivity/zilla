/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Read-only view of the content observed at the current {@link KafkaEvent} as a {@link KafkaTransform}
 * chain pumps events through its stages.
 * <p>
 * The buffer {@link #getValue()} exposes is a non-owning, on-stack view valid only for the duration of
 * the {@link KafkaTransform#transform} call that surfaced it. A stage that appends content to another
 * lane writes it there within that same call, which is what lets the whole message be composed in one
 * traversal with nothing held aside between lanes.
 * </p>
 *
 * @see KafkaTransform
 */
interface KafkaSource
{
    /**
     * The name of the content at the current event: the field's path rooted at the key or value it
     * belongs to (e.g. {@code $.name}) in the key and value lanes, and the header's name in the headers
     * lane.
     *
     * @return the content name, or {@code null} at a lane switch
     */
    String getPath();

    /**
     * Non-owning, on-stack view of the whole content value at the current event, its {@code capacity()}
     * being the value length. Empty at a lane switch.
     *
     * @return the content value bytes
     */
    DirectBufferEx getValue();
}
