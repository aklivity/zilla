/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.binding.function;

/**
 * Reads batches of frames from an underlying ring buffer and dispatches them to a
 * {@link MessageConsumer} handler.
 * <p>
 * Used by the engine event pipeline to drain event ring buffers up to a configurable
 * per-call limit, enabling cooperative fair scheduling across multiple readers.
 * </p>
 *
 * @see MessageConsumer
 * @see EngineContext#supplyEventReader()
 */
public interface MessageReader
{
    /**
     * Reads up to {@code messageCountLimit} frames from the underlying source and dispatches
     * each to {@code handler} via {@link MessageConsumer#accept}.
     *
     * @param handler            the consumer to receive each frame
     * @param messageCountLimit  the maximum number of frames to read in this call
     * @return the number of frames actually read and dispatched
     */
    int read(
        MessageConsumer handler,
        int messageCountLimit);
}
