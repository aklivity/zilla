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
package io.aklivity.zilla.runtime.metrics.http.internal;

import java.util.Map;
import java.util.function.Supplier;

import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricsContext;

public class HttpMetricsContext implements MetricsContext
{
    private final Map<String, Supplier<Metric>> httpMetrics = Map.of(
        "http.request.size", HttpRequestSizeMetric::new,
        "http.response.size", HttpResponseSizeMetric::new
    );

    @Override
    public Metric resolve(
        String name)
    {
        return httpMetrics.getOrDefault(name, () -> null).get();
    }

    /*@Override
    public Collection<String> names()
    {
        return httpMetrics.keySet();
    }*/
}
