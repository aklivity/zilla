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
package io.aklivity.zilla.runtime.engine.budget;

/**
 * Callback invoked when flow control capacity that was previously unavailable becomes
 * available, allowing a parked sender to resume claiming.
 * <p>
 * The callback is edge-triggered: it fires when capacity transitions from unavailable to
 * available, not repeatedly while capacity remains available. A resumed sender re-attempts
 * its claim, which may park again if a different constraint is now the limiting one.
 * </p>
 * <p>
 * Invoked on the owning I/O thread only.
 * </p>
 *
 * @see BudgetDebit
 */
@FunctionalInterface
public interface BudgetFlusher
{
    /**
     * Invoked when flow control capacity becomes available for a parked sender.
     *
     * @param traceId  the trace identifier for diagnostics
     */
    void onResume(
        long traceId);
}
