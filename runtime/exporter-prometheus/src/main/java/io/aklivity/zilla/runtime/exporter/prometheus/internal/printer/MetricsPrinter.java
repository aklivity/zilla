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

import java.io.PrintStream;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;
import io.aklivity.zilla.runtime.engine.metrics.record.CounterGaugeRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.record.MetricRecord;

public class MetricsPrinter
{
    private final MetricsReader metricsReader;
    private final Function<String, String> supplyKind;
    private final Function<String, String> supplyName;
    private final Function<String, String> supplyDescription;

    public MetricsPrinter(
        MetricsReader metricsReader,
        Function<String, String> supplyKind,
        Function<String, String> supplyName,
        Function<String, String> supplyDescription)
    {
        this.metricsReader = metricsReader;
        this.supplyKind = supplyKind;
        this.supplyName = supplyName;
        this.supplyDescription = supplyDescription;
    }

    public void print(
        PrintStream out)
    {
        for (MetricRecord metric : metricsReader.getRecords())
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
            result = formatCounterGauge((CounterGaugeRecord) metric);
        }
        else if (metric.getClass().equals(HistogramRecord.class))
        {
            HistogramRecord record = (HistogramRecord) metric;
            result = formatHistogram(record);
        }
        return result;
    }

    private String formatCounterGauge(
        CounterGaugeRecord record)
    {
        String kind = supplyKind.apply(record.metricName());
        String extName = supplyName.apply(record.metricName());
        String description = supplyDescription.apply(record.metricName());
        String format =
            "# HELP %s %s\n" +
            "# TYPE %s %s\n" +
            "%s{namespace=\"%s\",binding=\"%s\"} %d";
        return String.format(format, extName, description, extName, kind, extName,
            record.namespaceName(), record.bindingName(), record.valueReader().getAsLong());
    }

    private String formatHistogram(
        HistogramRecord record)
    {
        record.update();
        StringBuilder sb = new StringBuilder();
        String kind = supplyKind.apply(record.metricName());
        String extName = supplyName.apply(record.metricName());
        String description = supplyDescription.apply(record.metricName());
        long sum = record.stats()[2];
        long count = record.stats()[3];
        sb.append(String.format("# HELP %s %s\n# TYPE %s %s\n", extName, description, extName, kind));
        long cumulativeValue = 0;
        for (int i = 0; i < record.buckets(); i++)
        {
            String limit = i == record.buckets() - 1 ? "+Inf" : String.valueOf(record.bucketLimits()[i]);
            cumulativeValue += record.bucketValues()[i];
            sb.append(String.format("%s_bucket{le=\"%s\",namespace=\"%s\",binding=\"%s\"} %d\n",
                extName, limit, record.namespaceName(), record.bindingName(), cumulativeValue));
        }
        sb.append(String.format("%s_sum{namespace=\"%s\",binding=\"%s\"} %d\n",
            extName, record.namespaceName(), record.bindingName(), sum));
        sb.append(String.format("%s_count{namespace=\"%s\",binding=\"%s\"} %d\n",
            extName, record.namespaceName(), record.bindingName(), count));
        return sb.toString();
    }
}
