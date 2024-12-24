/*
 * Copyright 2021-2024 Aklivity Inc
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
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.metrics.reader.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.ScalarRecord;

public class PrometheusMetricsPrinter
{
    private final List<MetricRecord> records;
    private final Function<String, String> supplyKind;
    private final Function<String, String> supplyName;
    private final Function<String, String> supplyDescription;
    private final Function<String, Boolean> supplyMilliseconds;

    public PrometheusMetricsPrinter(
        List<MetricRecord> records,
        Function<String, String> supplyKind,
        Function<String, String> supplyName,
        Function<String, String> supplyDescription,
        Function<String, Boolean> supplyMilliseconds)
    {
        this.records = records;
        this.supplyKind = supplyKind;
        this.supplyName = supplyName;
        this.supplyDescription = supplyDescription;
        this.supplyMilliseconds = supplyMilliseconds;
    }

    public void print(
        PrintStream out)
    {
        for (MetricRecord metric : records)
        {
            out.println(format(metric));
            out.println();
        }
    }

    private String format(
        MetricRecord metric)
    {
        String result = null;
        if (metric.getClass().equals(ScalarRecord.class))
        {
            result = formatScalar((ScalarRecord) metric);
        }
        else if (metric.getClass().equals(HistogramRecord.class))
        {
            HistogramRecord record = (HistogramRecord) metric;
            result = formatHistogram(record);
        }
        return result;
    }

    private String formatScalar(
        ScalarRecord record)
    {
        String kind = supplyKind.apply(record.metric());
        String extName = supplyName.apply(record.metric());
        String description = supplyDescription.apply(record.metric());
        boolean milliseconds = supplyMilliseconds.apply(record.metric());
        String format =
            "# HELP %s %s\n" +
            "# TYPE %s %s\n" +
            "%s{namespace=\"%s\",binding=\"%s\"} %d";
        String msFormat =
            "# HELP %s %s\n" +
            "# TYPE %s %s\n" +
            "%s{namespace=\"%s\",binding=\"%s\"} %f";
        return milliseconds ?
            String.format(Locale.US, msFormat, extName, description, extName, kind, extName,
                record.namespace(), record.binding(), record.millisecondsValueReader().getAsDouble()) :
            String.format(format, extName, description, extName, kind, extName,
                record.namespace(), record.binding(), record.valueReader().getAsLong());
    }

    private String formatHistogram(
        HistogramRecord record)
    {
        record.update();
        StringBuilder sb = new StringBuilder();
        String kind = supplyKind.apply(record.metric());
        String extName = supplyName.apply(record.metric());
        String description = supplyDescription.apply(record.metric());
        boolean milliseconds = supplyMilliseconds.apply(record.metric());
        long sum = milliseconds ?  record.millisecondStats()[2] : record.stats()[2];
        long count = milliseconds ?  record.millisecondStats()[3] : record.stats()[3];
        sb.append(String.format("# HELP %s %s\n# TYPE %s %s\n", extName, description, extName, kind));
        long cumulativeValue = 0;
        for (int i = 0; i < record.buckets(); i++)
        {
            String limit = i == record.buckets() - 1 ? "+Inf" : String.valueOf(record.bucketLimits()[i]);
            cumulativeValue += milliseconds ? record.millisecondBucketValues()[i] : record.bucketValues()[i];
            sb.append(String.format("%s_bucket{le=\"%s\",namespace=\"%s\",binding=\"%s\"} %d\n",
                extName, limit, record.namespace(), record.binding(), cumulativeValue));
        }
        sb.append(String.format("%s_sum{namespace=\"%s\",binding=\"%s\"} %d\n",
            extName, record.namespace(), record.binding(), sum));
        sb.append(String.format("%s_count{namespace=\"%s\",binding=\"%s\"} %d\n",
            extName, record.namespace(), record.binding(), count));
        return sb.toString();
    }
}
