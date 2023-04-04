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
package io.aklivity.zilla.runtime.command.metrics.internal.airline;

import static io.aklivity.zilla.runtime.command.metrics.internal.layout.Layout.Mode.READ_ONLY;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.LangUtil;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.metrics.internal.labels.LabelManager;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.GaugesLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.processor.MetricsProcessor;

@Command(name = "metrics", description = "Show engine metrics")
public final class ZillaMetricsCommand extends ZillaCommand
{
    private static final Path METRICS_DIRECTORY = Paths.get(".zilla", "engine", "metrics");
    private static final Path LABELS_DIRECTORY = Paths.get(".zilla", "engine");
    private static final Pattern COUNTERS_PATTERN = Pattern.compile("counters(\\d+)");
    private static final Pattern GAUGES_PATTERN = Pattern.compile("gauges(\\d+)");
    private static final Pattern HISTOGRAMS_PATTERN = Pattern.compile("histograms(\\d+)");

    @Option(name = { "--namespace" })
    public String namespace;

    @Option(name = { "-i", "--interval" })
    public int interval;

    @Arguments(title = {"name"})
    public List<String> args;

    @Override
    public void run()
    {
        String binding = args != null && args.size() >= 1 ? args.get(0) : null;
        List<CountersLayout> countersLayouts = List.of();
        List<GaugesLayout> gaugesLayouts = List.of();
        List<HistogramsLayout> histogramsLayouts = List.of();
        try
        {
            countersLayouts = countersLayouts();
            gaugesLayouts = gaugesLayouts();
            histogramsLayouts = histogramsLayouts();
            final LabelManager labels = new LabelManager(LABELS_DIRECTORY);
            MetricsProcessor metrics = new MetricsProcessor(countersLayouts, gaugesLayouts, histogramsLayouts,
                    labels, namespace, binding);
            do
            {
                metrics.print(System.out);
                Thread.sleep(interval * 1000);
            } while (interval != 0);
        }
        catch (InterruptedException | IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        finally
        {
            countersLayouts.forEach(CountersLayout::close);
            gaugesLayouts.forEach(GaugesLayout::close);
            histogramsLayouts.forEach(HistogramsLayout::close);
        }
    }

    private List<CountersLayout> countersLayouts() throws IOException
    {
        Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1);
        return files.filter(this::isCountersFile).map(this::newCountersLayout).collect(toList());
    }

    private List<GaugesLayout> gaugesLayouts() throws IOException
    {
        Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1);
        return files.filter(this::isGaugesFile).map(this::newGaugesLayout).collect(toList());
    }

    private List<HistogramsLayout> histogramsLayouts() throws IOException
    {
        Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1);
        return files.filter(this::isHistogramsFile).map(this::newHistogramsLayout).collect(toList());
    }

    private boolean isCountersFile(
        Path path)
    {
        return path.getNameCount() - METRICS_DIRECTORY.getNameCount() == 1 &&
                COUNTERS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private boolean isGaugesFile(
        Path path)
    {
        return path.getNameCount() - METRICS_DIRECTORY.getNameCount() == 1 &&
                GAUGES_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private boolean isHistogramsFile(
        Path path)
    {
        return path.getNameCount() - METRICS_DIRECTORY.getNameCount() == 1 &&
                HISTOGRAMS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private CountersLayout newCountersLayout(
        Path path)
    {
        return new CountersLayout.Builder().path(path).mode(READ_ONLY).build();
    }

    private GaugesLayout newGaugesLayout(
        Path path)
    {
        return new GaugesLayout.Builder().path(path).mode(READ_ONLY).build();
    }

    private HistogramsLayout newHistogramsLayout(
        Path path)
    {
        return new HistogramsLayout.Builder().path(path).mode(READ_ONLY).build();
    }
}
