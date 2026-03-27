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

import java.util.function.LongConsumer;

/**
 * Claims send capacity from a shared flow control budget before writing frames downstream.
 * <p>
 * A sender must successfully claim capacity via {@link #claim} before writing any data.
 * If insufficient credit is available, the sender registers a {@code flusher} callback
 * that the engine invokes when credit becomes available, allowing the sender to retry.
 * </p>
 * <p>
 * All methods are called on the owning I/O thread only.
 * </p>
 *
 * @see BudgetCreditor
 * @see EngineContext#supplyDebitor(long)
 */
public interface BudgetDebitor
{
    /** Sentinel value returned when no debitor slot could be acquired. */
    long NO_DEBITOR_INDEX = -1L;

    /**
     * Acquires a slot in the debitor table for the given budget id and registers a
     * {@code flusher} callback to be invoked when credit becomes available.
     *
     * @param budgetId   the budget id to claim capacity from
     * @param watcherId  the stream id of the waiting sender, used to identify the watcher
     * @param flusher    callback invoked with the budget id when credit is available
     * @return the acquired debitor slot index, or {@link #NO_DEBITOR_INDEX} if unavailable
     */
    long acquire(
        long budgetId,
        long watcherId,
        LongConsumer flusher);

    /**
     * Attempts to claim between {@code minimum} and {@code maximum} bytes of send capacity,
     * with {@code deferred} bytes of additional capacity already reserved but not yet consumed.
     *
     * @param traceId      the trace identifier for diagnostics
     * @param budgetIndex  the debitor slot index returned by {@link #acquire}
     * @param watcherId    the stream id of the claiming sender
     * @param minimum      the minimum number of bytes required (claim fails if unavailable)
     * @param maximum      the maximum number of bytes desired
     * @param deferred     bytes already reserved in a previous partial claim
     * @return the number of bytes claimed (between {@code minimum} and {@code maximum}),
     *         or {@code 0} if insufficient credit is available
     */
    int claim(
        long traceId,
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum,
        int deferred);

    /** @deprecated Use {@link #claim(long, long, long, int, int, int)} instead. */
    @Deprecated
    int claim(
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum);

    /** @deprecated Use {@link #claim(long, long, long, int, int, int)} instead. */
    @Deprecated
    int claim(
        long budgetIndex,
        long watcherId,
        int minimum,
        int maximum,
        int deferred);

    /**
     * Releases the debitor slot identified by {@code budgetIndex} for the given watcher,
     * removing the flush callback registration and freeing the slot for reuse.
     *
     * @param budgetIndex  the debitor slot index to release
     * @param watcherId    the stream id of the watcher to deregister
     */
    void release(
        long budgetIndex,
        long watcherId);
}
