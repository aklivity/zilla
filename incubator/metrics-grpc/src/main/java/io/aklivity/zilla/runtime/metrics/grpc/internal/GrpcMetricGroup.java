/*
 * Copyright 2021-2022 Aklivity Inc
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

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class GrpcMetricGroup implements MetricGroup
{
    public static final String NAME = "grpc";

    private final Map<String, Supplier<Metric>> grpcMetrics = Map.of(
        "grpc.request.size", GrpcRequestSizeMetric::new,
        "grpc.response.size", GrpcResponseSizeMetric::new,
        "grpc.active.requests", GrpcActiveRequestsMetric::new,
        "grpc.duration", GrpcDurationMetric::new
    );

    public GrpcMetricGroup(
        Configuration config)
    {
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/grpc.schema.patch.json");
    }

    @Override
    public Metric supply(
        String name)
    {
        return grpcMetrics.getOrDefault(name, () -> null).get();
    }

    @Override
    public Collection<String> metricNames()
    {
        return grpcMetrics.keySet();
    }
}
