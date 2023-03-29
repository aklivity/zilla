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
package io.aklivity.zilla.runtime.command.metrics.internal.airline;

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.BINDING_ID_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.HISTOGRAM_BUCKET_LIMITS;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.METRIC_ID_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.NUMBER_OF_VALUES;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.VALUES_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.COUNTER;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader.Kind.HISTOGRAM;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.LangUtil;
import org.agrona.collections.Int2ObjectHashMap;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.metrics.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsReader;
import io.aklivity.zilla.runtime.command.metrics.internal.utils.IntIntIntFunction;

@Command(name = "metrics", description = "Show engine metrics")
public final class ZillaMetricsCommand extends ZillaCommand
{
    private static final Path METRICS_DIRECTORY = Paths.get(".zilla", "engine", "metrics");
    private static final Path LABELS_DIRECTORY = Paths.get(".zilla", "engine");
    private static final Pattern COUNTERS_PATTERN = Pattern.compile("counters(\\d+)");
    private static final Pattern HISTOGRAMS_PATTERN = Pattern.compile("histograms(\\d+)");
    private static final String NAMESPACE_HEADER = "namespace";
    private static final String BINDING_HEADER = "binding";
    private static final String METRIC_HEADER = "metric";
    private static final String VALUE_HEADER = "value";
    private static final int NUMBER_OF_HISTOGRAM_STATS = 4; // minimum, maximum, count, average

    @Option(name = { "--namespace" })
    public String namespace;

    @Option(name = { "-i", "--interval" })
    public Integer interval;

    @Arguments(title = {"name"})
    public List<String> args;

    private final LabelManager labels;
    private final Map<Integer, FileReader.Kind> metricTypes;
    // namespace -> binding -> metric -> values
    private final Map<Integer, Map<Integer, Map<Integer, long[]>>> metricValues;
    // namespace -> binding -> metric -> stats: [minimum, maximum, count, average]
    private final Map<Integer, Map<Integer, Map<Integer, long[]>>> histogramStats;
    private final Map<FileReader.Kind, IntIntIntFunction<String>> metricValueFormatters =
            Map.of(COUNTER, this::counterFormatter, HISTOGRAM, this::histogramFormatter);

    private int namespaceWidth = NAMESPACE_HEADER.length();
    private int bindingWidth = BINDING_HEADER.length();
    private int metricWidth = METRIC_HEADER.length();
    private int valueWidth = VALUE_HEADER.length();

    public ZillaMetricsCommand()
    {
        this.labels = new LabelManager(LABELS_DIRECTORY);
        this.metricTypes = new Int2ObjectHashMap<>();
        this.metricValues = new Int2ObjectHashMap<>();
        this.histogramStats = new Int2ObjectHashMap<>();
    }

    @Override
    public void run()
    {
        String binding = args != null && args.size() >= 1 ? args.get(0) : null;
        LongPredicate filterByNamespaceAndBinding = filterBy(namespace, binding);

        List<FileReader> fileReaders = List.of();
        try (Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1))
        {
            fileReaders = fileReaders(files);
            do
            {
                resetMetrics();
                calculateMetrics(fileReaders, filterByNamespaceAndBinding);
                printMetrics();
                Thread.sleep(interval * 1000);
            } while (interval != null);
        }
        catch (IOException | InterruptedException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        finally
        {
            fileReaders.forEach(FileReader::close);
        }
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

    private List<FileReader> fileReaders(
        Stream<Path> files)
    {
        return files.flatMap(path ->
        {
            if (isCountersFile(path))
            {
                return Stream.of(newCountersReader(path));
            }
            else if (isHistogramsFile(path))
            {
                return Stream.of(newHistogramsReader(path));
            }
            else
            {
                return Stream.of();
            }
        }).collect(toList());
    }

    private boolean isCountersFile(
        Path path)
    {
        return path.getNameCount() - METRICS_DIRECTORY.getNameCount() == 1 &&
                COUNTERS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private boolean isHistogramsFile(
        Path path)
    {
        return path.getNameCount() - METRICS_DIRECTORY.getNameCount() == 1 &&
                HISTOGRAMS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private FileReader newCountersReader(
        Path path)
    {
        CountersLayout layout = new CountersLayout.Builder().path(path).build();
        return new CountersReader(layout);
    }

    private FileReader newHistogramsReader(
        Path path)
    {
        HistogramsLayout layout = new HistogramsLayout.Builder().path(path).build();
        return new HistogramsReader(layout);
    }

    private void resetMetrics()
    {
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
        metricTypes.keySet().forEach(i -> metricTypes.remove(i));
    }

    private void calculateMetrics(List<FileReader> fileReaders, LongPredicate filter)
    {
        for (FileReader fileReader : fileReaders)
        {
            FileReader.Kind kind = fileReader.kind();
            for (LongSupplier[] recordReader : fileReader.recordReaders())
            {
                if (filter.test(recordReader[0].getAsLong()))
                {
                    int namespaceId = namespaceId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int bindingId = localId(recordReader[BINDING_ID_INDEX].getAsLong());
                    int metricId = localId(recordReader[METRIC_ID_INDEX].getAsLong());
                    int numberOfValues = NUMBER_OF_VALUES.get(kind);

                    metricTypes.putIfAbsent(metricId, kind);
                    collectMetricValue(namespaceId, bindingId, metricId, numberOfValues, recordReader);
                    if (kind == HISTOGRAM)
                    {
                        calculateHistogramStats(namespaceId, bindingId, metricId, numberOfValues, recordReader);
                    }
                    calculateColumnWidths(namespaceId, bindingId, metricId, kind);
                }
            }
        }
    }

    private void collectMetricValue(
        int namespaceId,
        int bindingId,
        int metricId,
        int numberOfValues,
        LongSupplier[] recordReader)
    {
        metricValues.putIfAbsent(namespaceId, new Int2ObjectHashMap<>());
        Map<Integer, Map<Integer, long[]>> metricsByNamespace = metricValues.get(namespaceId);

        metricsByNamespace.putIfAbsent(bindingId, new Int2ObjectHashMap<>());
        Map<Integer, long[]> metricsByBinding = metricsByNamespace.get(bindingId);

        long[] count = metricsByBinding.getOrDefault(metricId, new long[numberOfValues]);
        for (int i = 0; i < numberOfValues; i++)
        {
            // summing values across cores works for counters and histograms
            count[i] += recordReader[VALUES_INDEX + i].getAsLong();
        }
        metricsByBinding.put(metricId, count);
    }

    private void calculateHistogramStats(
        int namespaceId,
        int bindingId,
        int metricId,
        int numberOfValues,
        LongSupplier[] recordReader)
    {
        long count = 0L;
        long sum = 0L;
        int minIndex = -1;
        int maxIndex = -1;
        for (int bucketIndex = 0; bucketIndex < numberOfValues; bucketIndex++)
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
        String value = metricValueFormatters.get(kind).apply(namespaceId, bindingId, metricId);
        valueWidth = Math.max(valueWidth, value.length());
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

    private String counterFormatter(
        int namespaceId,
        int bindingId,
        int metricId)
    {
        return String.valueOf(metricValues.get(namespaceId).get(bindingId).get(metricId)[0]); // TODO: Ati - npe?
    }

    private String histogramFormatter(
        int namespaceId,
        int bindingId,
        int metricId)
    {
        long[] stats = histogramStats.get(namespaceId).get(bindingId).get(metricId);
        return String.format("[min: %d | max: %d | cnt: %d | avg: %d]", stats[0], stats[1], stats[2], stats[3]);
    }

    private void printMetrics()
    {
        String format = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
                valueWidth + "s\n";
        System.out.format(format, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
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
                    String value = metricValueFormatters.get(kind).apply(namespaceId, bindingId, metricId);
                    System.out.format(format, namespace, binding, metric, value);
                }
            }
        }
        System.out.println();
    }
}
