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
package io.aklivity.zilla.runtime.engine.event;

import org.agrona.DirectBuffer;

/**
 * Formats a structured engine event frame into a human-readable string.
 * <p>
 * Each binding that emits structured events provides an implementation, registered via
 * {@link java.util.ServiceLoader} through {@link EventFormatterFactorySpi}. The engine
 * calls {@link #format} when an exporter or the stdout event writer needs a readable
 * representation of an event frame — for example, to log a TLS handshake failure or
 * an HTTP authorization rejection with contextual detail.
 * </p>
 * <p>
 * The {@code buffer} slice passed to {@link #format} uses the same flyweight encoding
 * as the binding's internal event frames. Implementations decode the frame in-place
 * without allocating intermediate objects where possible.
 * </p>
 *
 * @see EventFormatterFactorySpi
 */
public interface EventFormatterSpi
{
    /**
     * Formats the event frame at {@code buffer[index..index+length)} into a
     * human-readable string.
     *
     * @param buffer  the buffer containing the event frame
     * @param index   the offset of the event frame in the buffer
     * @param length  the length of the event frame
     * @return a human-readable string describing the event, or {@code null} if the
     *         frame type is not recognised by this formatter
     */
    String format(
        DirectBuffer buffer,
        int index,
        int length);
}
