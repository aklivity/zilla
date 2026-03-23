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
 * Issues flow control credits to upstream senders on the I/O hot path.
 * <p>
 * Zilla uses a credit-based flow control model: a downstream receiver grants credit to an
 * upstream sender before the sender is permitted to write data. The {@code BudgetCreditor}
 * manages a per-thread shared budget table from which individual stream slots are acquired
 * and released. Parent-child budget relationships allow hierarchical flow control (e.g., a
 * shared connection window with per-stream sub-windows).
 * </p>
 * <p>
 * All methods are called on the owning I/O thread only.
 * </p>
 *
 * @see BudgetDebitor
 * @see EngineContext#creditor()
 */
public interface BudgetCreditor
{
    /** Sentinel value returned when no creditor slot is available. */
    long NO_CREDITOR_INDEX = -1L;

    /** Sentinel budget id indicating that no budget is associated with a stream. */
    long NO_BUDGET_ID = 0;

    /**
     * Acquires a slot in the budget table for the given budget id and returns its index.
     * <p>
     * The index is used in subsequent {@link #credit} and {@link #release} calls.
     * Returns {@link #NO_CREDITOR_INDEX} if the table is full.
     * </p>
     *
     * @param budgetId  the budget id to acquire a slot for
     * @return the acquired slot index, or {@link #NO_CREDITOR_INDEX} if unavailable
     */
    long acquire(
        long budgetId);

    /**
     * Adds flow control credit to the budget slot identified by {@code budgetIndex}.
     * <p>
     * This unblocks upstream senders that are waiting for capacity on this budget.
     * Returns the updated budget value after applying the credit.
     * </p>
     *
     * @param traceId      the trace identifier for diagnostics
     * @param budgetIndex  the slot index returned by {@link #acquire}
     * @param credit       the number of bytes of credit to add
     * @return the updated budget value
     */
    long credit(
        long traceId,
        long budgetIndex,
        long credit);

    /**
     * Releases a previously acquired budget slot, making it available for reuse.
     *
     * @param budgetIndex  the slot index to release
     */
    void release(
        long budgetIndex);

    /**
     * Allocates a child budget id derived from the given parent budget id.
     * <p>
     * Child budgets are constrained by their parent's available credit, enabling
     * hierarchical flow control (e.g., HTTP/2 stream windows within a connection window).
     * </p>
     *
     * @param budgetId  the parent budget id
     * @return a new child budget id
     */
    long supplyChild(
        long budgetId);

    /**
     * Releases a child budget that was previously allocated via {@link #supplyChild}.
     *
     * @param budgetId  the child budget id to release
     */
    void cleanupChild(
        long budgetId);
}
