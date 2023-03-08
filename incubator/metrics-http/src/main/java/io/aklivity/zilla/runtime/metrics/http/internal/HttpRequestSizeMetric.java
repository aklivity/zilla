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

import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricHandler;

public class HttpRequestSizeMetric implements Metric
{
    private static final String NAME = String.format("%s.%s", HttpMetrics.NAME, "request.size");

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public Kind kind()
    {
        return Kind.HISTOGRAM;
    }

    @Override
    public Unit unit()
    {
        return Unit.BYTES;
    }

    @Override
    public MetricHandler supplyReceived(
        long bindingId)
    {
        return null;
    }

    @Override
    public MetricHandler supplySent(
        long bindingId)
    {
        return null;
    }
}
