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
package io.aklivity.zilla.runtime.engine.internal.model;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricRecorder;

final class ModelMetrics
{
    static final String FIELDS_REPLACED = "model.fields.replaced";
    static final String FIELDS_DECLINED = "model.fields.declined";
    static final String REJECTED_INVALID = "model.rejected.invalid";
    static final String REJECTED_WITHHELD = "model.rejected.withheld";
    static final String TRANSFORM_DURATION = "model.transform.duration";

    private final EngineContext context;
    private final Long2ObjectHashMap<Recorders> recordersByBindingId;

    ModelMetrics(
        EngineContext context)
    {
        this.context = context;
        this.recordersByBindingId = new Long2ObjectHashMap<>();
    }

    Recorders supply(
        long bindingId)
    {
        Recorders recorders = recordersByBindingId.get(bindingId);

        if (recorders == null)
        {
            recorders = new Recorders(
                context.supplyMetricRecorder(bindingId, FIELDS_REPLACED),
                context.supplyMetricRecorder(bindingId, FIELDS_DECLINED),
                context.supplyMetricRecorder(bindingId, REJECTED_INVALID),
                context.supplyMetricRecorder(bindingId, REJECTED_WITHHELD),
                context.supplyMetricRecorder(bindingId, TRANSFORM_DURATION));
            recordersByBindingId.put(bindingId, recorders);
        }

        return recorders;
    }

    static final class Recorders
    {
        final MetricRecorder replaced;
        final MetricRecorder declined;
        final MetricRecorder invalid;
        final MetricRecorder withheld;
        final MetricRecorder duration;

        private Recorders(
            MetricRecorder replaced,
            MetricRecorder declined,
            MetricRecorder invalid,
            MetricRecorder withheld,
            MetricRecorder duration)
        {
            this.replaced = replaced;
            this.declined = declined;
            this.invalid = invalid;
            this.withheld = withheld;
            this.duration = duration;
        }
    }
}
