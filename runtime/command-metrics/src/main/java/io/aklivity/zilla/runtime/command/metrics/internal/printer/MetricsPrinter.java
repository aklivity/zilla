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
package io.aklivity.zilla.runtime.command.metrics.internal.printer;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.engine.metrics.reader.HistogramRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.ScalarRecord;

public class MetricsPrinter
{
    private static final String NAMESPACE_HEADER = "namespace";
    private static final String BINDING_HEADER = "binding";
    private static final String METRIC_HEADER = "metric";
    private static final String VALUE_HEADER = "value";

    private final List<MetricRecord> records;
    private final Map<MetricRecord, String> metricValues = new HashMap<>();

    private int namespaceWidth;
    private int bindingWidth;
    private int metricWidth;
    private int valueWidth;

    public MetricsPrinter(
        List<MetricRecord> records)
    {
        this.records = records;
    }

    public void print(
        PrintStream out)
    {
        formatMetrics();
        calculateColumnWidths();
        printRecords(out);
    }

    private void formatMetrics()
    {
        for (MetricRecord metric : records)
        {
            metricValues.put(metric, format(metric));
        }
    }

    private void calculateColumnWidths()
    {
        namespaceWidth = NAMESPACE_HEADER.length();
        bindingWidth = BINDING_HEADER.length();
        metricWidth = METRIC_HEADER.length();
        valueWidth = VALUE_HEADER.length();

        for (MetricRecord metric : records)
        {
            namespaceWidth = Math.max(namespaceWidth, metric.namespace().length());
            bindingWidth = Math.max(bindingWidth, metric.binding().length());
            metricWidth = Math.max(metricWidth, metric.metric().length());
            valueWidth = Math.max(valueWidth, metricValues.get(metric).length());
        }
    }

    private void printRecords(
        PrintStream out)
    {
        String format = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
            valueWidth + "s\n";
        out.format(format, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
        for (MetricRecord metric : records)
        {
            out.format(format, metric.namespace(), metric.binding(), metric.metric(), metricValues.get(metric));
        }
        out.println();
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
            result = formatHistogram((HistogramRecord) metric);
        }
        return result;
    }

    private String formatScalar(
        ScalarRecord record)
    {
        return String.valueOf(record.valueReader().getAsLong());
    }

    private String formatHistogram(
        HistogramRecord record)
    {
        record.update();
        return String.format("[min: %d | max: %d | cnt: %d | avg: %d]",
            record.stats()[0], record.stats()[1], record.stats()[3], record.stats()[4]);
    }
}
