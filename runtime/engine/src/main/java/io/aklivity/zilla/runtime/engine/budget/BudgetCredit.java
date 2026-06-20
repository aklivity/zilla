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
package io.aklivity.zilla.runtime.engine.budget;

/**
 * Consumer-side flow control handle: grants the capacity the holder has available downstream.
 * <p>
 * A {@code BudgetCredit} is obtained from {@link EngineContext#supplyCredit} and is recorded
 * against the stream it reads, so the engine releases the underlying budget resources when
 * the stream terminates; an explicit {@link #close()} is therefore optional. The engine
 * translates declared capacity into flow control credit for upstream senders, replacing the
 * per-binding credit translation.
 * </p>
 * <p>
 * All methods are called on the owning I/O thread only.
 * </p>
 *
 * @see BudgetDebit
 * @see EngineContext#supplyCredit(long, long)
 */
public interface BudgetCredit extends AutoCloseable
{
    /**
     * Grants {@code bytes} of downstream capacity to upstream senders.
     *
     * @param traceId  the trace identifier for diagnostics
     * @param bytes    the byte capacity available downstream
     */
    void capacity(
        long traceId,
        int bytes);

    /**
     * Grants downstream capacity on the byte axis together with a count-based axis (for
     * example a limit on in-flight messages) for protocols that constrain both.
     *
     * @param traceId   the trace identifier for diagnostics
     * @param messages  the message capacity available downstream
     * @param bytes     the byte capacity available downstream
     */
    void capacity(
        long traceId,
        int messages,
        int bytes);

    /**
     * Releases the underlying budget resources held by this handle. Optional; resources are
     * released implicitly when the stream terminates.
     */
    @Override
    void close();
}
