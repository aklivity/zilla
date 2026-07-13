/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.logs.internal.airline;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ZILLA_DIRECTORY_PROPERTY;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.agrona.ErrorHandler;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.command.logs.internal.printer.LogRecord;
import io.aklivity.zilla.runtime.command.logs.internal.printer.LogsPrinter;
import io.aklivity.zilla.runtime.command.logs.internal.printer.LogsReader;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

@Command(name = "logs", description = "Show engine event logs")
public final class ZillaLogsCommand extends ZillaCommand
{
    private static final String ZILLA_DIRECTORY = System.getProperty(ZILLA_DIRECTORY_PROPERTY, ".");
    private static final String OPTION_PROPERTIES_PATH_DEFAULT = String.format("%s/.zilla/zilla.properties", ZILLA_DIRECTORY);
    private static final String ZILLA_ENGINE_PATH_DEFAULT = String.format("%s/.zilla/engine", ZILLA_DIRECTORY);
    private static final int EVENT_COUNT_LIMIT = 8192;
    private static final long POLL_INTERVAL_MILLIS = 500L;

    @Option(name = {"--filter"},
        description = "Only show events whose type name matches this glob pattern (supports * and ?)")
    public String filter;

    @Option(name = {"-f", "--follow"},
        description = "Keep printing new events as they arrive after printing the current log")
    public boolean follow;

    @Option(name = {"-p", "--properties"},
        description = "Path to properties",
        hidden = true)
    public String propertiesPath;

    @Override
    public void run()
    {
        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), ZILLA_ENGINE_PATH_DEFAULT);
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
        final Configuration config = new EngineConfiguration(props);
        final ErrorHandler onError = Throwable::printStackTrace;

        try (Engine engine = Engine.builder()
            .config(config)
            .errorHandler(onError)
            .readonly()
            .build())
        {
            engine.start();

            LogsReader reader = new LogsReader(engine.supplyEventReader(), engine.supplyEventFormatter()::format,
                engine::supplyQName, engine::supplyLocalName);

            boolean matched = false;
            boolean polling = true;
            while (polling)
            {
                List<LogRecord> records = reader.read(EVENT_COUNT_LIMIT);

                LogsPrinter printer = new LogsPrinter(records, filter);
                matched = printer.print(System.out) || matched;

                polling = follow;
                if (polling)
                {
                    sleep(POLL_INTERVAL_MILLIS);
                }
            }

            if (filter != null && !follow && !matched)
            {
                throw new IllegalStateException(String.format("No events matched filter: %s", filter));
            }
        }
        catch (Throwable ex)
        {
            ex.printStackTrace();
            rethrowUnchecked(ex);
        }
    }

    private static void sleep(
        long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}
