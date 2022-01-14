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
package io.aklivity.zilla.runtime.command.log.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.LangUtil;
import org.agrona.concurrent.status.CountersReader;

import io.aklivity.zilla.runtime.command.log.internal.layouts.MetricsLayout;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public final class LogCountersCommand implements Runnable
{
    private static final Pattern METRICS_PATTERN = Pattern.compile("metrics(\\d+)");

    private final Path directory;
    private final boolean verbose;
    private final boolean separator;
    private final Logger out;
    private final Map<Path, CountersReader> countersByPath;
    private final Map<String, LongSupplier> valuesByName;

    LogCountersCommand(
        EngineConfiguration config,
        Logger out,
        boolean verbose,
        boolean separator)
    {
        this.directory = config.directory();
        this.verbose = verbose;
        this.separator = separator;
        this.out = out;
        this.countersByPath = new LinkedHashMap<>();
        this.valuesByName = new LinkedHashMap<>();
    }

    private boolean isMetricsFile(
        Path path)
    {
        return path.getNameCount() - directory.getNameCount() == 1 &&
                METRICS_PATTERN.matcher(path.getName(path.getNameCount() - 1).toString()).matches() &&
                Files.isRegularFile(path);
    }

    private void onDiscovered(
        Path path)
    {
        if (verbose)
        {
            out.printf("Discovered: %s\n", path);
        }
    }

    private CountersReader supplyCounters(
        Path metricsPath)
    {
        return countersByPath.computeIfAbsent(metricsPath, this::newCountersReader);
    }

    private CountersReader newCountersReader(
        Path metricsPath)
    {
        MetricsLayout layout = new MetricsLayout.Builder()
                .path(metricsPath)
                .readonly(true)
                .build();

        return new CountersReader(layout.labelsBuffer(), layout.valuesBuffer());
    }

    private void supplyValuesByName(
        CountersReader reader)
    {
        reader.forEach((id, name) ->
        {
            valuesByName.merge(name, () -> reader.getCounterValue(id), (v1, v2) -> () -> v1.getAsLong() + v2.getAsLong());
        });
    }

    private void counters()
    {
        final String valueFormat = separator ? ",d" : "d";

        valuesByName.forEach((name, value) -> out.printf(
                "{" +
                "\"name\": \"%s\"," +
                "\"value\":%" + valueFormat +
                "}\n", name, value.getAsLong()));
        out.printf("\n");
    }

    @Override
    public void run()
    {
        valuesByName.clear();

        try (Stream<Path> files = Files.walk(directory, 1))
        {
            files.filter(this::isMetricsFile)
                 .peek(this::onDiscovered)
                 .map(this::supplyCounters)
                 .forEach(this::supplyValuesByName);

            counters();
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }
}
