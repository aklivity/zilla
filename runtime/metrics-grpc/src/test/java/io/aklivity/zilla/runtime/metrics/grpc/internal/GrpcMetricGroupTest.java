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
package io.aklivity.zilla.runtime.metrics.grpc.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;

public class GrpcMetricGroupTest
{
    @Test
    public void shouldReturnMetricNames()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Collection<String> metricNames = metricGroup.metricNames();

        // THEN
        assertThat(metricNames, containsInAnyOrder(
            "grpc.request.size",
            "grpc.response.size",
            "grpc.active.requests",
            "grpc.duration",
            "grpc.requests.per.rpc",
            "grpc.responses.per.rpc"
        ));
    }

    @Test
    public void shouldResolveGrpcRequestSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.request.size");

        // THEN
        assertThat(metric, instanceOf(GrpcRequestSizeMetric.class));
        assertThat(metric.name(), equalTo("grpc.request.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("gRPC request content length"));
    }

    @Test
    public void shouldResolveGrpcRequestSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.request.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcSizeMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveGrpcResponseSize()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.response.size");

        // THEN
        assertThat(metric, instanceOf(GrpcResponseSizeMetric.class));
        assertThat(metric.name(), equalTo("grpc.response.size"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.BYTES));
        assertThat(metric.description(), equalTo("gRPC response content length"));
    }

    @Test
    public void shouldResolveGrpcResponseSizeContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.response.size");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcSizeMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }

    @Test
    public void shouldResolveGrpcActiveRequests()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.active.requests");

        // THEN
        assertThat(metric, instanceOf(GrpcActiveRequestsMetric.class));
        assertThat(metric.name(), equalTo("grpc.active.requests"));
        assertThat(metric.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of active gRPC requests"));
    }

    @Test
    public void shouldResolveGrpcActiveRequestsContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.active.requests");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcActiveRequestsMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.GAUGE));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }

    @Test
    public void shouldResolveGrpcDuration()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.duration");

        // THEN
        assertThat(metric, instanceOf(GrpcDurationMetric.class));
        assertThat(metric.name(), equalTo("grpc.duration"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.NANOSECONDS));
        assertThat(metric.description(), equalTo("Duration of gRPC requests"));
    }

    @Test
    public void shouldResolveGrpcDurationContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.duration");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcDurationMetricContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.BOTH));
    }

    @Test
    public void shouldResolveGrpcRequestsPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.requests.per.rpc");

        // THEN
        assertThat(metric, instanceOf(GrpcRequestsPerRpcMetric.class));
        assertThat(metric.name(), equalTo("grpc.requests.per.rpc"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of gRPC requests per RPC"));
    }

    @Test
    public void shouldResolveGrpcRequestsPerRpcContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.requests.per.rpc");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcCountPerRpcContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.RECEIVED));
    }

    @Test
    public void shouldResolveGrpcResponsesPerRpc()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);

        // WHEN
        Metric metric = metricGroup.supply("grpc.responses.per.rpc");

        // THEN
        assertThat(metric, instanceOf(GrpcResponsesPerRpcMetric.class));
        assertThat(metric.name(), equalTo("grpc.responses.per.rpc"));
        assertThat(metric.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(metric.unit(), equalTo(Metric.Unit.COUNT));
        assertThat(metric.description(), equalTo("Number of gRPC responses per RPC"));
    }

    @Test
    public void shouldResolveGrpcResponsesPerRpcContext()
    {
        // GIVEN
        Configuration config = new Configuration();
        MetricGroup metricGroup = new GrpcMetricGroup(config);
        Metric metric = metricGroup.supply("grpc.responses.per.rpc");

        // WHEN
        MetricContext context = metric.supply(mock(EngineContext.class));

        // THEN
        assertThat(context, instanceOf(GrpcCountPerRpcContext.class));
        assertThat(context.group(), equalTo("grpc"));
        assertThat(context.kind(), equalTo(Metric.Kind.HISTOGRAM));
        assertThat(context.direction(), equalTo(MetricContext.Direction.SENT));
    }
}
