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

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.Reader.BINDING_ID_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.Reader.METRIC_ID_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.Reader.NUMBER_OF_VALUES;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.Reader.VALUES_INDEX;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.Reader.Kind.COUNTER;
import static io.aklivity.zilla.runtime.command.metrics.internal.layout.Reader.Kind.HISTOGRAM;
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
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.Reader;
import io.aklivity.zilla.runtime.command.metrics.internal.utils.LongArrayFunction;

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

    @Option(name = { "--namespace" })
    public String namespace;

    @Arguments(title = {"name"})
    public List<String> args;

    private final LabelManager labels;
    private final Map<Integer, Map<Integer, Map<Integer, long[]>>> metrics; // namespace -> binding -> metric -> values
    private final Map<Integer, Reader.Kind> metricTypes;
    private final Map<Reader.Kind, LongArrayFunction<String>> toStringConverters =
            Map.of(COUNTER, this::counterToStringConverter, HISTOGRAM, this::histogramToStringConverter);

    private int namespaceWidth = NAMESPACE_HEADER.length();
    private int bindingWidth = BINDING_HEADER.length();
    private int metricWidth = METRIC_HEADER.length();
    private int valueWidth = VALUE_HEADER.length();

    public ZillaMetricsCommand()
    {
        this.labels = new LabelManager(LABELS_DIRECTORY);
        this.metrics = new Int2ObjectHashMap<>();
        this.metricTypes = new Int2ObjectHashMap<>();
    }

    @Override
    public void run()
    {
        String binding = args != null && args.size() >= 1 ? args.get(0) : null;
        LongPredicate filterByNamespaceAndBinding = filterBy(namespace, binding);

        try (Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1))
        {
            for (Reader reader : readers(files))
            {
                for (LongSupplier[] recordReader : reader.recordReaders())
                {
                    if (filterByNamespaceAndBinding.test(recordReader[0].getAsLong()))
                    {
                        collectMetricValue(recordReader, reader.kind());
                        calculateColumnWidths(recordReader, reader.kind());
                    }
                }
                reader.close();
            }
            printMetrics();
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
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

    private List<Reader> readers(
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

    private Reader newCountersReader(
        Path path)
    {
        CountersLayout layout = new CountersLayout.Builder().path(path).build();
        return new CountersReader(layout);
    }

    private Reader newHistogramsReader(
        Path path)
    {
        HistogramsLayout layout = new HistogramsLayout.Builder().path(path).build();
        return new HistogramsReader(layout);
    }

    private void collectMetricValue(
        LongSupplier[] recordReader,
        Reader.Kind kind)
    {
        int numberOfValues = NUMBER_OF_VALUES.get(kind);
        int namespaceId = namespaceId(recordReader[BINDING_ID_INDEX].getAsLong());
        int bindingId = localId(recordReader[BINDING_ID_INDEX].getAsLong());
        int metricId = localId(recordReader[METRIC_ID_INDEX].getAsLong());

        metricTypes.putIfAbsent(metricId, kind);

        metrics.putIfAbsent(namespaceId, new Int2ObjectHashMap<>());
        Map<Integer, Map<Integer, long[]>> metricsByNamespace = metrics.get(namespaceId);

        metricsByNamespace.putIfAbsent(bindingId, new Int2ObjectHashMap<>());
        Map<Integer, long[]> metricsByBinding = metricsByNamespace.get(bindingId);

        long[] count = metricsByBinding.getOrDefault(metricId, new long[numberOfValues]);
        for (int i = 0; i < numberOfValues; i++)
        {
            // adding values across cores works for counters and histograms
            count[i] += recordReader[VALUES_INDEX + i].getAsLong();
        }
        metricsByBinding.put(metricId, count);
    }

    private void calculateColumnWidths(
        LongSupplier[] recordReader,
        Reader.Kind kind)
    {
        int namespaceId = namespaceId(recordReader[BINDING_ID_INDEX].getAsLong());
        int bindingId = localId(recordReader[BINDING_ID_INDEX].getAsLong());
        int metricId = localId(recordReader[METRIC_ID_INDEX].getAsLong());

        String namespace = labels.lookupLabel(namespaceId);
        String binding = labels.lookupLabel(bindingId);
        String metric = labels.lookupLabel(metricId);
        long[] values = new long[]{recordReader[VALUES_INDEX].getAsLong()}; // TODO: Ati
        String valuesAsString = toStringConverters.get(kind).apply(values);

        namespaceWidth = Math.max(namespaceWidth, namespace.length());
        bindingWidth = Math.max(bindingWidth, binding.length());
        metricWidth = Math.max(metricWidth, metric.length());
        valueWidth = Math.max(valueWidth, valuesAsString.length());
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

    private String counterToStringConverter(long... value)
    {
        return String.valueOf(value[0]);
    }

    private String histogramToStringConverter(long[] values)
    {
        return Arrays.toString(values);
    }

    private void printMetrics()
    {
        String format = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
                valueWidth + "s\n";
        System.out.format(format, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
        for (Integer namespaceId : metrics.keySet())
        {
            for (Integer bindingId : metrics.get(namespaceId).keySet())
            {
                for (Integer metricId : metrics.get(namespaceId).get(bindingId).keySet())
                {
                    String namespace = labels.lookupLabel(namespaceId);
                    String binding = labels.lookupLabel(bindingId);
                    String metric = labels.lookupLabel(metricId);
                    long[] value = metrics.get(namespaceId).get(bindingId).get(metricId);
                    String valueAsString = toStringConverters.get(metricTypes.get(metricId)).apply(value);
                    System.out.format(format, namespace, binding, metric, valueAsString);
                }
            }
        }
    }
}
