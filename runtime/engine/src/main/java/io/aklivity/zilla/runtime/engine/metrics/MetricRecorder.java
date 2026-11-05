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
package io.aklivity.zilla.runtime.engine.metrics;

/**
 * Records values for a metric that is not derived from stream frames.
 * <p>
 * Obtained from {@link io.aklivity.zilla.runtime.engine.EngineContext#supplyMetricRecorder(long, String)},
 * a recorder honors the {@code telemetry} selection and attributes of the component it was supplied for.
 * A recorder is called on a single I/O thread, so recording requires no synchronization.
 * </p>
 *
 * @see MetricContext.Direction#NONE
 */
public interface MetricRecorder
{
    /** A recorder that discards every value. */
    MetricRecorder NOOP = (authorization, value) ->
    {
    };

    /**
     * Records a value for the metric.
     * <p>
     * Attributes configured for the component that reference guarded values are evaluated
     * from the {@code authorization}.
     * </p>
     *
     * @param authorization  the authorization used to evaluate guarded attributes, or {@code 0L} if none
     * @param value          the value to record
     */
    void record(
        long authorization,
        long value);
}
