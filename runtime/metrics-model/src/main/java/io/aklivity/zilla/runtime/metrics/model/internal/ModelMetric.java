/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.metrics.model.internal;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;

public final class ModelMetric implements Metric
{
    static final String FIELDS_REPLACED = "model.fields.replaced";
    static final String FIELDS_DECLINED = "model.fields.declined";
    static final String REJECTED_INVALID = "model.rejected.invalid";
    static final String REJECTED_WITHHELD = "model.rejected.withheld";
    static final String TRANSFORM_DURATION = "model.transform.duration";

    private final String name;
    private final Kind kind;
    private final Unit unit;
    private final String description;

    ModelMetric(
        String name,
        Kind kind,
        Unit unit,
        String description)
    {
        this.name = name;
        this.kind = kind;
        this.unit = unit;
        this.description = description;
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public Kind kind()
    {
        return kind;
    }

    @Override
    public Unit unit()
    {
        return unit;
    }

    @Override
    public String description()
    {
        return description;
    }

    @Override
    public MetricContext supply(
        EngineContext context)
    {
        return new ModelMetricContext(ModelMetricGroup.NAME, kind);
    }
}
