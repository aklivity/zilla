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
package io.aklivity.zilla.runtime.engine.classifier;

/**
 * Reports whether any of the labels it was created with is present in a value.
 * <p>
 * The labels are fixed when the detector is created by {@link ClassifierHandler#initDetector},
 * so the answer carries no label, position or score. A caller that needs different handling
 * per label creates a separate detector for each. Thresholds are applied by the
 * implementation, so a caller never sees a sub-threshold finding.
 * </p>
 * <p>
 * <b>Resolution always completes asynchronously.</b> {@link #detect} takes a completion
 * callback that fires <em>strictly later</em> than the call returns, never synchronously,
 * even when the answer could be produced inline. The callback fires on the caller's I/O
 * thread; the implementation owns thread alignment, deferring via
 * {@code EngineContext.dispatch(Runnable)} to the next event-loop tick of the same worker.
 * An implementation that cannot answer, including one that exceeds its own time limit,
 * reports {@link CompletionCallback#failed}. A call is never left unanswered.
 * </p>
 * <p>
 * <b>A value may be one window of a longer value.</b> A caller that examines a longer value as it
 * arrives presents it a window at a time. Each answer reports how many trailing characters of the
 * window the detector could not rule out, so the caller can withhold them and present them again at the
 * start of its next window. A match that straddles two windows is then seen whole, and nothing that could
 * still become part of a match is released before it is ruled out.
 * </p>
 *
 * @see ClassifierHandler
 */
public interface Detector
{
    /**
     * Detects the labels in {@code value} asynchronously.
     * <p>
     * The {@code contextId} supplied at the call site is echoed back through the callback
     * so a single shared {@link CompletionCallback} instance can route results to the
     * correct stream without per-call lambda capture.
     * </p>
     *
     * @param traceId     the trace identifier for diagnostics
     * @param bindingId   the binding identifier requesting the detection
     * @param contextId   a context identifier (e.g., stream id), echoed back to {@code completion}
     * @param value       the value to examine
     * @param completion  callback invoked with whether any label was detected, or
     *                    {@link CompletionCallback#failed} if the attempt failed
     */
    void detect(
        long traceId,
        long bindingId,
        long contextId,
        String value,
        CompletionCallback completion);

    /**
     * Completion handler for the async {@link #detect(long, long, long, String, CompletionCallback)}
     * operation. The {@code contextId} supplied by the caller is echoed back to both methods.
     */
    interface CompletionCallback
    {
        /**
         * Invoked when the operation completes successfully.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param detected   {@code true} if any of the labels is present in the value
         * @param retained   the number of trailing characters of the value that could not be ruled out, which a
         *                   caller presenting the value a window at a time withholds and presents again at the start
         *                   of its next window, or {@code 0} if the whole value is ruled out
         */
        void completed(
            long contextId,
            boolean detected,
            int retained);

        /**
         * Invoked when the operation fails.
         *
         * @param contextId  the {@code contextId} supplied to the originating call
         * @param ex         the failure cause
         */
        void failed(
            long contextId,
            Throwable ex);
    }
}
