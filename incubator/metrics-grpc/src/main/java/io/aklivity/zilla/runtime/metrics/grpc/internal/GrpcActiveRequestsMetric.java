/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.grpc.internal;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;

public class GrpcActiveRequestsMetric implements Metric
{
    private static final String GROUP = GrpcMetricGroup.NAME;
    private static final String NAME = String.format("%s.%s", GROUP, "active.requests");
    private static final String DESCRIPTION = "Number of active gRPC requests";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.GAUGE;
    }

    @Override
    public Unit unit()
    {
        return Unit.COUNT;
    }

    @Override
    public String description()
    {
        return DESCRIPTION;
    }

    @Override
    public MetricContext supply(
        EngineContext context)
    {
        return new GrpcActiveRequestsMetricContext(GROUP, kind());
    }
}
