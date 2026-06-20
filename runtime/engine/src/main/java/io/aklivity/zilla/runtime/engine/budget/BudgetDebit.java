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
 * Producer-side flow control handle: claims send capacity before writing a frame downstream.
 * <p>
 * A {@code BudgetDebit} is obtained from {@link EngineContext#supplyDebit} and is recorded
 * against the stream it writes, so the engine releases the underlying budget resources when
 * the stream terminates; an explicit {@link #close()} is therefore optional. It unifies the
 * per-stream window and the shared budget behind one handle, so a binding no longer
 * hand-codes window arithmetic or the acquire/claim/release dance.
 * </p>
 * <p>
 * All methods are called on the owning I/O thread only.
 * </p>
 *
 * @see BudgetCredit
 * @see EngineContext#supplyDebit(long, long, BudgetFlusher)
 */
public interface BudgetDebit extends AutoCloseable
{
    /**
     * Declares the framing the engine applies to subsequent claims.
     * <p>
     * Re-callable, not restricted to stream open: framing parameters can change mid-stream
     * (for example when a negotiated setting or padding changes), so each call updates the
     * framing applied to later claims.
     * </p>
     *
     * @param traceId  the trace identifier for diagnostics
     * @param padding  the per-frame overhead reserved in addition to the claimed payload bytes
     * @param minimum  the least capacity the receiver will grant, composed with each claim's
     *                 own minimum
     */
    void declare(
        long traceId,
        int padding,
        int minimum);

    /**
     * Attempts to claim between {@code minimum} and {@code maximum} payload bytes for the one
     * frame about to be sent, reserving the declared per-frame padding in addition.
     *
     * @param traceId  the trace identifier for diagnostics
     * @param minimum  the minimum payload bytes required (the claim parks if unavailable)
     * @param maximum  the maximum payload bytes desired
     * @return the granted payload bytes, between {@code minimum} and {@code maximum}, or
     *         {@code 0} if insufficient capacity is available; on {@code 0} the sender is
     *         parked and the registered {@link BudgetFlusher} fires when capacity frees
     */
    default int claim(
        long traceId,
        int minimum,
        int maximum)
    {
        return claim(traceId, minimum, maximum, 0);
    }

    /**
     * Attempts to claim between {@code minimum} and {@code maximum} payload bytes for the one
     * frame about to be sent, reserving the declared per-frame padding in addition, and signals
     * that {@code deferred} further bytes will be written beyond this claim.
     *
     * @param traceId  the trace identifier for diagnostics
     * @param minimum  the minimum payload bytes required (the claim parks if unavailable)
     * @param maximum  the maximum payload bytes desired
     * @param deferred the additional bytes the producer will write beyond this claim, passed
     *                 through to the underlying flow-control claim
     * @return the granted payload bytes, between {@code minimum} and {@code maximum}, or
     *         {@code 0} if insufficient capacity is available; on {@code 0} the sender is
     *         parked and the registered {@link BudgetFlusher} fires when capacity frees
     */
    int claim(
        long traceId,
        int minimum,
        int maximum,
        int deferred);

    /**
     * Indicates whether this handle is currently backed by an available shared budget.
     * <p>
     * A producer that must not proceed without a shared budget can abort the stream when this
     * returns {@code false}. Producers that tolerate a not-yet-available budget ignore it: a
     * {@link #claim} grants the full requested amount until the budget becomes available. The
     * default is {@code true}; implementations backed by a shared budget report its presence.
     * </p>
     *
     * @return {@code true} when a shared budget is available to claim against
     */
    default boolean available()
    {
        return true;
    }

    /**
     * Releases the underlying budget resources held by this handle. Optional; resources are
     * released implicitly when the stream terminates.
     */
    @Override
    void close();
}
