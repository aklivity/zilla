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

    @Option(name = {"--grep"},
        description = "Only show events whose type name contains this text")
    public String grep;

    @Option(name = {"--wait-for"},
        description = "Wait for an event whose type name equals this text, then exit")
    public String waitFor;

    @Option(name = {"--timeout"},
        description = "Maximum seconds to wait when using --wait-for; defaults to a single check, " +
            "suitable for a Docker HEALTHCHECK where retries are driven externally")
    public int timeout;

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
            long deadline = System.currentTimeMillis() + timeout * 1000L;
            boolean polling = true;
            while (polling)
            {
                List<LogRecord> records = reader.read(EVENT_COUNT_LIMIT);

                LogsPrinter printer = new LogsPrinter(records, grep);
                matched = printer.print(System.out, waitFor) || matched;

                polling = waitFor != null && !matched && System.currentTimeMillis() < deadline;
                if (polling)
                {
                    sleep(POLL_INTERVAL_MILLIS);
                }
            }

            if (waitFor != null && !matched)
            {
                throw new IllegalStateException(String.format("Timed out waiting for event: %s", waitFor));
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
