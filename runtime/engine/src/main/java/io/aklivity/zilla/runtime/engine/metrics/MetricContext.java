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
package io.aklivity.zilla.runtime.engine.metrics;

import java.util.function.LongConsumer;

import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

/**
 * Per-thread recording context for a single {@link Metric}.
 * <p>
 * Obtained from {@link Metric#supply(EngineContext)}, a {@code MetricContext} supplies a
 * {@link MessageConsumer} interceptor that the engine inserts into the stream pipeline to
 * observe and record metric values as frames flow through. Because the interceptor is called
 * on a single I/O thread, metric recording requires no synchronization.
 * </p>
 *
 * @see Metric
 */
public interface MetricContext
{
    /**
     * The direction of stream traffic that a metric observes.
     */
    enum Direction
    {
        /** Metric is recorded on inbound (received) frames only. */
        RECEIVED,
        /** Metric is recorded on outbound (sent) frames only. */
        SENT,
        /** Metric is recorded on frames in both directions. */
        BOTH
    }

    /**
     * Returns the name of the metric group this context belongs to,
     * e.g. {@code "http"} or {@code "kafka"}.
     *
     * @return the metric group name
     */
    String group();

    /**
     * Returns the recording kind for the metric this context measures.
     *
     * @return the metric kind
     */
    Metric.Kind kind();

    /**
     * Returns the traffic direction that this metric context observes.
     *
     * @return the recording direction
     */
    Direction direction();

    /**
     * Returns a {@link MessageConsumer} interceptor that records values for this metric.
     * <p>
     * The returned consumer is inserted into the stream pipeline by the engine and calls
     * {@code recorder} with each observed value (e.g., frame payload length for a byte
     * counter, or elapsed time for a latency histogram).
     * </p>
     *
     * @param recorder  the {@link LongConsumer} that receives each observed metric value
     * @return a {@link MessageConsumer} to interpose on the stream pipeline
     */
    MessageConsumer supply(
        LongConsumer recorder);
}
