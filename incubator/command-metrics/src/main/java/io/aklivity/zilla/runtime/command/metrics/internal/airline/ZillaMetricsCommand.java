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
import io.aklivity.zilla.runtime.command.metrics.internal.layout.CountersReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.FileReader;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsLayout;
import io.aklivity.zilla.runtime.command.metrics.internal.layout.HistogramsReader;
import io.aklivity.zilla.runtime.command.metrics.internal.processor.MetricsProcessor;

@Command(name = "metrics", description = "Show engine metrics")
public final class ZillaMetricsCommand extends ZillaCommand
{
    private static final Path METRICS_DIRECTORY = Paths.get(".zilla", "engine", "metrics");
    private static final Path LABELS_DIRECTORY = Paths.get(".zilla", "engine");
    private static final Pattern COUNTERS_PATTERN = Pattern.compile("counters(\\d+)");
    private static final Pattern HISTOGRAMS_PATTERN = Pattern.compile("histograms(\\d+)");

    @Option(name = { "--namespace" })
    public String namespace;

    @Option(name = { "-i", "--interval" })
    public Integer interval;

    @Arguments(title = {"name"})
    public List<String> args;

    @Override
    public void run()
    {
        String binding = args != null && args.size() >= 1 ? args.get(0) : null;
        List<FileReader> fileReaders = List.of();
        try
        {
            final LabelManager labels = new LabelManager(LABELS_DIRECTORY);
            MetricsProcessor metrics = new MetricsProcessor(counterFileReaders(), histogramFileReaders(), labels,
                    namespace, binding);
            do
            {
                metrics.print(System.out);
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

    private List<CountersReader> counterFileReaders() throws IOException
    {
        Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1);
        return files.filter(this::isCountersFile).map(this::newCountersReader).collect(toList());
    }

    private List<HistogramsReader> histogramFileReaders() throws IOException
    {
        Stream<Path> files = Files.walk(METRICS_DIRECTORY, 1);
        return files.filter(this::isHistogramsFile).map(this::newHistogramsReader).collect(toList());
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

    private CountersReader newCountersReader(
        Path path)
    {
        CountersLayout layout = new CountersLayout.Builder().path(path).build();
        return new CountersReader(layout);
    }

    private HistogramsReader newHistogramsReader(
        Path path)
    {
        HistogramsLayout layout = new HistogramsLayout.Builder().path(path).build();
        return new HistogramsReader(layout);
    }
}
