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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.printer;

import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKETS;
import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKET_LIMITS;

import java.io.PrintStream;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.metrics.processor.MetricsProcessor;
import io.aklivity.zilla.runtime.engine.metrics.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.MetricRecord;

public class MetricsPrinter
{
    private final MetricsProcessor metricsProcessor;
    private final Function<String, String> supplyKind;
    private final Function<String, String> supplyName;
    private final Function<String, String> supplyDescription;

    public MetricsPrinter(
        MetricsProcessor metricsProcessor,
        Function<String, String> supplyKind,
        Function<String, String> supplyName,
        Function<String, String> supplyDescription)
    {
        this.metricsProcessor = metricsProcessor;
        this.supplyKind = supplyKind;
        this.supplyName = supplyName;
        this.supplyDescription = supplyDescription;
    }

    public void print(
        PrintStream out)
    {
        for (MetricRecord metric : metricsProcessor.getRecords())
        {
            out.println(format(metric));
            out.println();
        }
    }

    private String format(
        MetricRecord metric)
    {
        String result = null;
        if (metric.getClass().equals(CounterGaugeRecord.class))
        {
            CounterGaugeRecord record = (CounterGaugeRecord) metric;
            result = formatCounterGauge(record.metricName(), record.namespaceName(), record.bindingName(), record.value());
        }
        else if (metric.getClass().equals(HistogramRecord.class))
        {
            // TODO: Ati
            HistogramRecord record = (HistogramRecord) metric;
            result = record.metricName() + " histogram TODO";
        }
        return result;
    }

    private String formatCounterGauge(
        String metric,
        String namespace,
        String binding,
        long value)
    {
        String kind = supplyKind.apply(metric);
        String extName = supplyName.apply(metric);
        String description = supplyDescription.apply(metric);
        String format =
            "# HELP %s %s\n" +
            "# TYPE %s %s\n" +
            "%s{namespace=\"%s\",binding=\"%s\"} %d";
        return String.format(format, extName, description, extName, kind, extName, namespace, binding, value);
    }

    private String formatHistogram(
        String metric,
        String namespace,
        String binding,
        long[] values,
        long[] stats)
    {
        StringBuilder sb = new StringBuilder();
        String kind = supplyKind.apply(metric);
        String extName = supplyName.apply(metric);
        String description = supplyDescription.apply(metric);
        long sum = stats[2];
        long count = stats[3];
        sb.append(String.format("# HELP %s %s\n# TYPE %s %s\n", extName, description, extName, kind));
        for (int i = 0; i < BUCKETS; i++)
        {
            String limit = i == BUCKETS - 1 ? "+Inf" : String.valueOf(BUCKET_LIMITS[i]);
            sb.append(String.format("%s_bucket{le=\"%s\",namespace=\"%s\",binding=\"%s\"} %d\n",
                extName, limit, namespace, binding, values[i]));
        }
        sb.append(String.format("%s_sum{namespace=\"%s\",binding=\"%s\"} %d\n", extName, namespace, binding, sum));
        sb.append(String.format("%s_count{namespace=\"%s\",binding=\"%s\"} %d\n", extName, namespace, binding, count));
        return sb.toString();
    }
}
