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
package io.aklivity.zilla.runtime.command.metrics.internal.airline;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static java.util.Objects.requireNonNull;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

import org.agrona.ErrorHandler;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.metrics.internal.printer.MetricsPrinter;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricRecord;
import io.aklivity.zilla.runtime.engine.metrics.reader.MetricsReader;

@Command(name = "metrics", description = "Show engine metrics")
public final class ZillaMetricsCommand extends ZillaCommand
{
    private static final String OPTION_PROPERTIES_PATH_DEFAULT = ".zilla/zilla.properties";

    @Option(name = { "--namespace" })
    public String namespace;

    @Option(name = { "-i", "--interval" })
    public int interval;

    @Option(name = {"-p", "--properties"},
        description = "Path to properties",
        hidden = true)
    public String propertiesPath;

    @Arguments(title = {"name"})
    public List<String> args;

    @Override
    public void run()
    {
        String binding = args != null && args.size() >= 1 ? args.get(0) : null;
        Engine engine = engine();
        requireNonNull(engine);
        MetricsReader metrics = new MetricsReader(engine, engine::supplyLocalName);
        LongPredicate filterPredicate = filterPredicate(namespace, binding, engine::supplyLabelId);
        List<MetricRecord> records = filter(metrics.records(), filterPredicate);
        MetricsPrinter printer = new MetricsPrinter(records);
        do
        {
            printer.print(System.out);
            sleep(interval);
        } while (interval != 0);
        close(engine);
    }

    private Engine engine()
    {
        final Configuration config = engineConfiguration();
        final ErrorHandler onError = Throwable::printStackTrace;

        try (Engine engine = Engine.builder()
            .config(config)
            .errorHandler(onError)
            .readonly()
            .build())
        {
            engine.start();
            return engine;
        }
        catch (Throwable ex)
        {
            System.out.println("error");
            rethrowUnchecked(ex);
        }
        return null;
    }

    private Configuration engineConfiguration()
    {
        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), ".zilla/engine");

        Path path = Paths.get(propertiesPath != null ? propertiesPath : OPTION_PROPERTIES_PATH_DEFAULT);
        if (Files.exists(path) || propertiesPath != null)
        {
            try
            {
                props.load(Files.newInputStream(path));
            }
            catch (IOException ex)
            {
                System.out.println("Failed to load properties: " + path);
                rethrowUnchecked(ex);
            }
        }
        return new EngineConfiguration(props);
    }

    private void close(
        Engine engine)
    {
        try
        {
            engine.close();
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
    }

    private void sleep(
        long interval)
    {
        try
        {
            Thread.sleep(interval * 1000L);
        }
        catch (InterruptedException ex)
        {
            rethrowUnchecked(ex);
        }
    }

    private LongPredicate filterPredicate(
        String namespace,
        String binding,
        Function<String, Integer> lookupLabelId)
    {
        int namespaceId = namespace != null ? Math.max(lookupLabelId.apply(namespace), 0) : 0;
        int bindingId = binding != null ? Math.max(lookupLabelId.apply(binding), 0) : 0;
        long namespacedId = (long) namespaceId << Integer.SIZE | (long) bindingId << 0;
        long mask =
            (namespace != null ? 0xffff_ffff_0000_0000L : 0x0000_0000_0000_0000L) |
                (binding != null ? 0x0000_0000_ffff_ffffL : 0x0000_0000_0000_0000L);
        LongPredicate filter = id -> (id & mask) == namespacedId;
        return filter;
    }

    private List<MetricRecord> filter(
        List<MetricRecord> records,
        LongPredicate filterPredicate)
    {
        return records.stream()
            .filter(r -> filterPredicate.test(r.namespacedBindingId()))
            .collect(Collectors.toList());
    }
}
