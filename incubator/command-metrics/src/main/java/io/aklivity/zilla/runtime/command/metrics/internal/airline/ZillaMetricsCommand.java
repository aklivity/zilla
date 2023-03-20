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

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
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

@Command(name = "metrics", description = "Show engine metrics")
public final class ZillaMetricsCommand extends ZillaCommand
{
    private static final Path METRICS_DIRECTORY = Paths.get(".zilla", "engine", "metrics");
    private static final Path LABELS_DIRECTORY = Paths.get(".zilla", "engine");
    private static final Pattern COUNTERS_PATTERN = Pattern.compile("counters(\\d+)");
    private static final String NAMESPACE_HEADER = "namespace";
    private static final String BINDING_HEADER = "binding";
    private static final String METRIC_HEADER = "metric";
    private static final String VALUE_HEADER = "value";

    @Option(name = { "--namespace" })
    public String namespace;

    @Arguments(title = {"name"})
    public List<String> args;

    private final Map<Path, CountersReader> readersByPath;
    private final LabelManager labels;
    private final Map<Integer, Map<Integer, Map<Integer, Long>>> metrics; // namespace -> binding -> metric -> value

    private int namespaceWidth = NAMESPACE_HEADER.length();
    private int bindingWidth = BINDING_HEADER.length();
    private int metricWidth = METRIC_HEADER.length();
    private int valueWidth = VALUE_HEADER.length();

    public ZillaMetricsCommand()
    {
        this.readersByPath = new LinkedHashMap<>();
        this.labels = new LabelManager(LABELS_DIRECTORY);
        this.metrics = new Int2ObjectHashMap<>();
    }

    @Override
    public void run()
    {
        String binding = args != null && args.size() >= 1 ? args.get(0) : null;
        LongPredicate filterByNamespaceAndBinding = filterBy(namespace, binding);

        try (Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1))
        {
            for (CountersReader reader : readers(files))
            {
                for (long[] record : reader.records())
                {
                    if (filterByNamespaceAndBinding.test(record[0]))
                    {
                        collectMetric(record);
                        calculateColumnWidths(record);
                    }
                }
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

    private List<CountersReader> readers(
        Stream<Path> files)
    {
        return files.filter(this::isCountersFile)
                .map(this::supplyCounterReaders)
                .collect(toList());
    }

    private boolean isCountersFile(
        Path path)
    {
        return path.getNameCount() - METRICS_DIRECTORY.getNameCount() == 1 &&
                COUNTERS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private CountersReader supplyCounterReaders(
        Path path)
    {
        return readersByPath.computeIfAbsent(path, this::newCountersReader);
    }

    private CountersReader newCountersReader(
        Path loadPath)
    {
        CountersLayout layout = new CountersLayout.Builder().path(loadPath).build();
        return new CountersReader(layout);
    }

    private void collectMetric(
        long[] record)
    {
        int namespaceId = namespaceId(record[0]);
        int bindingId = localId(record[0]);
        int metricId = localId(record[1]);
        long value = record[2];

        metrics.putIfAbsent(namespaceId, new Int2ObjectHashMap<>());
        Map<Integer, Map<Integer, Long>> metricsByNamespace = metrics.get(namespaceId);

        metricsByNamespace.putIfAbsent(bindingId, new Int2ObjectHashMap<>());
        Map<Integer, Long> metricsByBinding = metricsByNamespace.get(bindingId);

        Long previousCount = metricsByBinding.getOrDefault(metricId, 0L);
        Long currentCount = previousCount + value; // this works only for counters
        metricsByBinding.put(metricId, currentCount);
    }

    private void calculateColumnWidths(long[] record)
    {
        int namespaceId = namespaceId(record[0]);
        int bindingId = localId(record[0]);
        int metricId = localId(record[1]);
        long value = record[2];

        String namespace = labels.lookupLabel(namespaceId);
        String binding = labels.lookupLabel(bindingId);
        String metric = labels.lookupLabel(metricId);
        String valueAsString = String.valueOf(value);

        namespaceWidth = Math.max(namespaceWidth, namespace.length());
        bindingWidth = Math.max(bindingWidth, binding.length());
        metricWidth = Math.max(metricWidth, metric.length());
        valueWidth = Math.max(valueWidth, valueAsString.length());
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

    private void printMetrics()
    {
        String headerFormat = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
                valueWidth + "s\n";
        String dataFormat = "%-" + namespaceWidth + "s    %-" + bindingWidth + "s    %-" + metricWidth + "s    %" +
                valueWidth + "d\n";
        System.out.format(headerFormat, NAMESPACE_HEADER, BINDING_HEADER, METRIC_HEADER, VALUE_HEADER);
        for (Integer namespaceId : metrics.keySet())
        {
            for (Integer bindingId : metrics.get(namespaceId).keySet())
            {
                for (Integer metricId : metrics.get(namespaceId).get(bindingId).keySet())
                {
                    String namespace = labels.lookupLabel(namespaceId);
                    String binding = labels.lookupLabel(bindingId);
                    String metric = labels.lookupLabel(metricId);
                    Long value = metrics.get(namespaceId).get(bindingId).get(metricId);
                    System.out.format(dataFormat, namespace, binding, metric, value);
                }
            }
        }
    }
}
