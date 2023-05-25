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
package io.aklivity.zilla.runtime.exporter.prometheus.internal.processor;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.aklivity.zilla.runtime.engine.metrics.layout.CountersLayoutRO;
import io.aklivity.zilla.runtime.engine.metrics.layout.GaugesLayoutRO;
import io.aklivity.zilla.runtime.engine.metrics.layout.HistogramsLayoutRO;
import io.aklivity.zilla.runtime.engine.metrics.layout.MetricsLayoutRO;

public class LayoutManager
{
    private static final Pattern COUNTERS_PATTERN = Pattern.compile("counters(\\d+)");
    private static final Pattern GAUGES_PATTERN = Pattern.compile("gauges(\\d+)");
    private static final Pattern HISTOGRAMS_PATTERN = Pattern.compile("histograms(\\d+)");

    private final Path metricsPath;

    public LayoutManager(
        Path engineDirectory)
    {
        this.metricsPath = engineDirectory.resolve("metrics");
    }

    public List<MetricsLayoutRO> countersLayouts() throws IOException
    {
        Stream<Path> files = Files.walk(metricsPath, 1);
        return files.filter(this::isCountersFile).map(this::newCountersLayout).collect(toList());
    }

    public List<MetricsLayoutRO> gaugesLayouts() throws IOException
    {
        Stream<Path> files = Files.walk(metricsPath, 1);
        return files.filter(this::isGaugesFile).map(this::newGaugesLayout).collect(toList());
    }

    public List<MetricsLayoutRO> histogramsLayouts() throws IOException
    {
        Stream<Path> files = Files.walk(metricsPath, 1);
        return files.filter(this::isHistogramsFile).map(this::newHistogramsLayout).collect(toList());
    }

    private boolean isCountersFile(
        Path path)
    {
        return path.getNameCount() - metricsPath.getNameCount() == 1 &&
            COUNTERS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
            Files.isRegularFile(path);
    }

    private boolean isGaugesFile(
        Path path)
    {
        return path.getNameCount() - metricsPath.getNameCount() == 1 &&
            GAUGES_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
            Files.isRegularFile(path);
    }

    private boolean isHistogramsFile(
        Path path)
    {
        return path.getNameCount() - metricsPath.getNameCount() == 1 &&
            HISTOGRAMS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
            Files.isRegularFile(path);
    }

    private CountersLayoutRO newCountersLayout(
        Path path)
    {
        return new CountersLayoutRO.Builder().path(path).build();
    }

    private GaugesLayoutRO newGaugesLayout(
        Path path)
    {
        return new GaugesLayoutRO.Builder().path(path).build();
    }

    private HistogramsLayoutRO newHistogramsLayout(
        Path path)
    {
        return new HistogramsLayoutRO.Builder().path(path).build();
    }
}
