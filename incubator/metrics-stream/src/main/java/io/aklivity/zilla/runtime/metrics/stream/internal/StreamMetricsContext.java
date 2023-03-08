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
package io.aklivity.zilla.runtime.metrics.stream.internal;

import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricsContext;

public class StreamMetricsContext implements MetricsContext
{
    private final Map<String, Supplier<Metric>> streamMetrics = Map.of(
        "stream.opens.received", StreamOpensReceivedMetric::new,
        "stream.opens.sent", StreamOpensSentMetric::new,
        "stream.data.received", StreamDataReceivedMetric::new,
        "stream.data.sent", StreamDataSentMetric::new,
        "stream.errors.received", StreamErrorsReceivedMetric::new,
        "stream.errors.sent", StreamErrorsSentMetric::new,
        "stream.closes.received", StreamClosesReceivedMetric::new,
        "stream.closes.sent", StreamClosesSentMetric::new
    );

    @Override
    public Metric resolve(
        String name)
    {
        return streamMetrics.getOrDefault(name, () -> null).get();
    }

    /*@Override
    public Collection<String> names()
    {
        return streamMetrics.keySet();
    }*/
}
