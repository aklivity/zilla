/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.registry;

import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;

final class BindingRegistry
{
    private final BindingConfig binding;
    private final BindingContext context;

    private BindingHandler attached;
    private MetricHandler originMetricHandler = MetricHandler.NO_OP;
    private MetricHandler routedMetricHandler = MetricHandler.NO_OP;

    BindingRegistry(
        BindingConfig binding,
        BindingContext context)
    {
        this.binding = binding;
        this.context = context;
    }

    public void attach()
    {
        attached = context.attach(binding);
    }

    public void detach()
    {
        context.detach(binding);
        attached = null;
    }

    public BindingHandler streamFactory()
    {
        return attached;
    }

    public void setOriginMetricHandler(MetricHandler originMetricHandler)
    {
        this.originMetricHandler = originMetricHandler;
    }

    public void setRoutedMetricHandler(MetricHandler routedMetricHandler)
    {
        this.routedMetricHandler = routedMetricHandler;
    }

    public MetricHandler originMetricHandler()
    {
        return originMetricHandler;
    }

    public MetricHandler routedMetricHandler()
    {
        return routedMetricHandler;
    }
}
