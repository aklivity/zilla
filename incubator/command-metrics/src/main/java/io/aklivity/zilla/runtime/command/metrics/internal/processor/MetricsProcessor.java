/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.command.metrics.internal.processor;

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.BINDING_ID_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.HISTOGRAM_BUCKET_LIMITS;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.METRIC_ID_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.NUMBER_OF_VALUES;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.VALUES_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.COUNTER;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.HISTOGRAM;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.command.metrics.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsReader;
import io.aklivity.zilla.runtime.command.metrics.internal.utils.AggregatorFunction;
import io.aklivity.zilla.runtime.command.metrics.internal.utils.IntIntIntFunction;

public class MetricsProcessor
{
    private static final Path LABELS_DIRECTORY = Paths.get(".zilla", "engine");
    private static final String NAMESPACE_HEADER = "namespace";
    private static final String BINDING_HEADER = "binding";
    private static final String METRIC_HEADER = "metric";
    private static final String VALUE_HEADER = "value";
    // minimum, maximum, count, average
    private static final int NUMBER_OF_HISTOGRAM_STATS = 4;

    private final List<CountersReader> counterFileReaders;
    private final List<HistogramsReader> histogramFileReaders;
    private final LabelManager labels;
    private final LongPredicate filter;

    private final List<MetricRecord> counterRecords;

    private final Map<Integer, FileReader.Kind> metricTypes;
    // namespace -> binding -> metric -> values
    private final Map<Integer, Map<Integer, Map<Integer, long[]>>> metricValues;
    // namespace -> binding -> metric -> stats: [minimum, maximum, count, average]
    private final Map<Integer, Map<Integer, Map<Integer, long[]>>> histogramStats;
    private final Map<FileReader.Kind, IntIntIntFunction<String>> formatters = Map.of(
            COUNTER, this::formatCounter,
            HISTOGRAM, this::formatHistogram);
    private final Map<FileReader.Kind, AggregatorFunction> aggregators = Map.of(
            COUNTER, this::aggregateCounter,
            HISTOGRAM, this::aggregateHistogram);

    private int namespaceWidth = NAMESPACE_HEADER.length();
    private int bindingWidth = BINDING_HEADER.length();
    private int metricWidth = METRIC_HEADER.length();
    private int valueWidth = VALUE_HEADER.length();

    public MetricsProcessor(
        List<CountersReader> counterFileReaders,
        List<HistogramsReader> histogramFileReaders,
        LabelManager labels,
        String namespaceName,
        String bindingName)
    {
        this.counterFileReaders = counterFileReaders;
        this.histogramFileReaders = histogramFileReaders;
        this.labels = labels;
        this.filter = filterBy(namespaceName, bindingName);
        this.metricTypes = new Int2ObjectHashMap<>();
        this.metricValues = new Int2ObjectHashMap<>();
        this.histogramStats = new Int2ObjectHashMap<>();
        this.counterRecords = new LinkedList<>();
    }

    public void doProcess(PrintStream out)
    {
        reset();
        calculate();
        printNew(out);
        print(out);
    }

    private LongPredicate filterBy(
        String namespace,
        String binding)
    {
        int namespaceId = namespace != null ? Math.max(labels.lookupLabelId(namespace), 0) : 0;
        int bindingId = binding != null ? Math.max(labels.lookupLabelId(binding), 0) : 0;

        long namespacedId =
                (long) namespaceId << Integer.SIZE |
                        (long) bindingId << 0;

        long mask =
                (namespace != null ? 0xffff_ffff_0000_0000L : 0x0000_0000_0000_0000L) |
                        (binding != null ? 0x0000_0000_ffff_ffffL : 0x0000_0000_0000_0000L);
        return id -> (id & mask) == namespacedId;
    }

    private void reset()
    {
        metricTypes.keySet().forEach(metricTypes::remove);
        for (Integer namespaceId : metricValues.keySet())
        {
            for (Integer bindingId : metricValues.get(namespaceId).keySet())
            {
                for (Integer metricId : metricValues.get(namespaceId).get(bindingId).keySet())
                {
                    Arrays.fill(metricValues.get(namespaceId).get(bindingId).get(metricId), 0L);
                }
            }
        }
        for (Integer namespaceId : histogramStats.keySet())
        {
            for (Integer bindingId : histogramStats.get(namespaceId).keySet())
            {
                for (Integer metricId : histogramStats.get(namespaceId).get(bindingId).keySet())
                {
                    Arrays.fill(histogramStats.get(namespaceId).get(bindingId).get(metricId), 0L);
                }
            }
        }
    }

    private void calculate()
    {
        // hack: get metadata
        LongSupplier[][] longSuppliers = counterFileReaders.get(0).recordReaders();
        for (LongSupplier[] longSupplier : longSuppliers) // iterate over metadata (nsBinding/nsMetric pairs)
        {
            long namespacedBindingId = longSupplier[0].getAsLong();
            long namespacedMetricId = longSupplier[1].getAsLong();
            if (filter.test(namespacedBindingId))
            {
                MetricRecord record = new MetricRecord(namespacedBindingId, namespacedMetricId, COUNTER, labels::lookupLabel);
                for (CountersReader counterFileReader : counterFileReaders)
                {
                    record.addReader(counterFileReader.layout().supplyReader(namespacedBindingId, namespacedMetricId));
                }
                counterRecords.add(record);
                calculateColumnWidthsNew(record);
            }
        }

        /*List<FileReader> fileReaders = new LinkedList<>();
        fileReaders.addAll(counterFileReaders);
        fileReaders.addAll(histogramFileReaders);*/
        for (CountersReader counterFileReader : counterFileReaders)
        {
            FileReader.Kind kind = COUNTER; //fileReader.kind();
            for (LongSupplier[] recordReader : counterFileReader.recordReaders())
            {
                if (filter.test(recordReader[0].getAsLong()))
                {
                    int namespaceId = namespaceId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int bindingId = localId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int metricId = localId(recordReader[METRIC_ID_INDEX].getAsLong());

                    metricTypes.putIfAbsent(metricId, kind);
                    collectMetricValue(namespaceId, bindingId, metricId, kind, recordReader);
                    calculateColumnWidths(namespaceId, bindingId, metricId, kind);
                }
            }
        }
        for (HistogramsReader histogramFileReader : histogramFileReaders)
        {
            FileReader.Kind kind = HISTOGRAM; //fileReader.kind();
            for (LongSupplier[] recordReader : histogramFileReader.recordReaders())
            {
                if (filter.test(recordReader[0].getAsLong()))
                {
                    int namespaceId = namespaceId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int bindingId = localId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int metricId = localId(recordReader[METRIC_ID_INDEX].getAsLong());

                    metricTypes.putIfAbsent(metricId, kind);
                    collectMetricValue(namespaceId, bindingId, metricId, kind, recordReader);
                    calculateHistogramStats(namespaceId, bindingId, metricId, recordReader);
                    calculateColumnWidths(namespaceId, bindingId, metricId, kind);
                }
            }
        }
        /*for (FileReader fileReader : fileReaders)
        {
            FileReader.Kind kind = fileReader.kind();
            for (LongSupplier[] recordReader : fileReader.recordReaders())
            {
                if (filter.test(recordReader[0].getAsLong()))
                {
                    int namespaceId = namespaceId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int bindingId = localId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int metricId = localId(recordReader[METRIC_ID_INDEX].getAsLong());

                    metricTypes.putIfAbsent(metricId, kind);
                    collectMetricValue(namespaceId, bindingId, metricId, kind, recordReader);
                    if (kind == HISTOGRAM)
                    {
                        calculateHistogramStats(namespaceId, bindingId, metricId, recordReader);
                    }
                    calculateColumnWidths(namespaceId, bindingId, metricId, kind);
                }
            }
        }*/
    }

    private static int namespaceId(
        long packedId)
    {
        return (int) (packedId >> Integer.SIZE) & 0xffff_ffff;
    }

    private static int localId(
        long packedId)
    {
        return (int) (packedId >> 0) & 0xffff_ffff;
    }

    private void collectMetricValue(
        int namespaceId,
        int bindingId,
        int metricId,
        FileReader.Kind kind,
        LongSupplier[] recordReader)
    {
        metricValues.putIfAbsent(namespaceId, new Int2ObjectHashMap<>());
        Map<Integer, Map<Integer, long[]>> metricsByNamespace = metricValues.get(namespaceId);

        metricsByNamespace.putIfAbsent(bindingId, new Int2ObjectHashMap<>());
        Map<Integer, long[]> metricsByBinding = metricsByNamespace.get(bindingId);

        long[] accumulator = metricsByBinding.getOrDefault(metricId, new long[NUMBER_OF_VALUES.get(kind)]);
        metricsByBinding.put(metricId, aggregators.get(kind).apply(accumulator, recordReader));
    }

    private long[] aggregateCounter(long[] accumulator, LongSupplier[] recordReader)
    {
        accumulator[0] += recordReader[VALUES_INDEX + 0].getAsLong();
        return accumulator;
    }

    private long[] aggregateHistogram(long[] accumulator, LongSupplier[] recordReader)
    {
        for (int i = 0; i < accumulator.length; i++)
        {
            accumulator[i] += recordReader[VALUES_INDEX + i].getAsLong();
        }
        return accumulator;
    }

    private void calculateHistogramStats(
        int namespaceId,
        int bindingId,
        int metricId,
        LongSupplier[] recordReader)
    {
        long count = 0L;
        long sum = 0L;
        int minIndex = -1;
        int maxIndex = -1;
        for (int bucketIndex = 0; bucketIndex < NUMBER_OF_VALUES.get(HISTOGRAM); bucketIndex++)
        {
            long bucketValue = recordReader[VALUES_INDEX + bucketIndex].getAsLong();
            count += bucketValue;
            long value = HISTOGRAM_BUCKET_LIMITS.get(bucketIndex) - 1;
            sum += bucketValue * value;
            if (bucketValue != 0)
            {
                maxIndex = bucketIndex;
                if (minIndex == -1)
                {
                    minIndex = bucketIndex;
                }
            }
        }
        long minimum = minIndex == -1 ? 0L : HISTOGRAM_BUCKET_LIMITS.get(minIndex) - 1;
        long maximum = maxIndex == -1 ? 0L : HISTOGRAM_BUCKET_LIMITS.get(maxIndex) - 1;
        long average = count == 0L ? 0L : sum / count;

        histogramStats.putIfAbsent(namespaceId, new Int2ObjectHashMap<>());
        Map<Integer, Map<Integer, long[]>> histogramStatsByNamespace = histogramStats.get(namespaceId);

        histogramStatsByNamespace.putIfAbsent(bindingId, new Int2ObjectHashMap<>());
        Map<Integer, long[]> histogramStatsByBinding = histogramStatsByNamespace.get(bindingId);

        long[] stats = histogramStatsByBinding.getOrDefault(metricId, new long[NUMBER_OF_HISTOGRAM_STATS]);
        stats[0] = minimum;
        stats[1] = maximum;
        stats[2] = count;
        stats[3] = average;
        histogramStatsByBinding.put(metricId, stats);
    }

    private void calculateColumnWidthsNew(
        MetricRecord metric) // TODO: Ati
    {
        namespaceWidth = Math.max(namespaceWidth, metric.namespaceName().length());
        bindingWidth = Math.max(bindingWidth, metric.bindingName().length());
        metricWidth = Math.max(metricWidth, metric.metricName().length());
        valueWidth = Math.max(valueWidth, metric.stringValue().length());
    }

    private void calculateColumnWidths(
        int namespaceId,
        int bindingId,
        int metricId,
        FileReader.Kind kind)
    {
        String namespace = labels.lookupLabel(namespaceId);
        String binding = labels.lookupLabel(bindingId);
        String metric = labels.lookupLabel(metricId);

        namespaceWidth = Math.max(namespaceWidth, namespace.length());
        bindingWidth = Math.max(bindingWidth, binding.length());
        metricWidth = Math.max(metricWidth, metric.length());
        String value = formatters.get(kind).apply(namespaceId, bindingId, metricId);
        valueWidth = Math.max(valueWidth, value.length());
    }

    private String formatCounter(
        int namespaceId,
        int bindingId,
        int metricId)
    {
        return String.valueOf(metricValues.get(namespaceId).get(bindingId).get(metricId)[0]);
    }

    private String formatHistogram(
        int namespaceId,
        int bindingId,
        int metricId)
    {
        long[] stats = histogramStats.get(namespaceId).get(bindingId).get(metricId);
        return String.format("[min: %d | max: %d | cnt: %d | avg: %d]", stats[0], stats[1], stats[2], stats[3]);
    }

    private void printNew(
        PrintStream out) // TODO: Ati
    {
        String format = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
                valueWidth + "s\n";
        out.format(format, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
        for (MetricRecord metric : counterRecords)
        {
            out.format(format, metric.namespaceName(), metric.bindingName(), metric.metricName(), metric.stringValue());
        }
        out.println();
    }

    private void print(PrintStream out)
    {
        String format = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
                valueWidth + "s\n";
        out.format(format, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
        for (Integer namespaceId : metricValues.keySet())
        {
            for (Integer bindingId : metricValues.get(namespaceId).keySet())
            {
                for (Integer metricId : metricValues.get(namespaceId).get(bindingId).keySet())
                {
                    String namespace = labels.lookupLabel(namespaceId);
                    String binding = labels.lookupLabel(bindingId);
                    String metric = labels.lookupLabel(metricId);
                    FileReader.Kind kind = metricTypes.get(metricId);
                    String value = formatters.get(kind).apply(namespaceId, bindingId, metricId);
                    out.format(format, namespace, binding, metric, value);
                }
            }
        }
        out.println();
    }
}
