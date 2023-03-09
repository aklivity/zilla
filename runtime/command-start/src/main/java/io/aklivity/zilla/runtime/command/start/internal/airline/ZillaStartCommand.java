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
package io.aklivity.zilla.runtime.command.start.internal.airline;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_VERBOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static java.lang.Runtime.getRuntime;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.command.ZillaCommand;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

@Command(name = "start", description = "Start engine")
public final class ZillaStartCommand extends ZillaCommand
{
    private static final String OPTION_PROPERTIES_PATH_DEFAULT = ".zilla/zilla.properties";

    private final CountDownLatch stop = new CountDownLatch(1);
    private final CountDownLatch stopped = new CountDownLatch(1);

    @Option(name = { "-c", "--config" },
            description = "Configuration location",
            hidden = true)
    public URI configURI;

    @Option(name = { "-v", "--verbose" },
            description = "Show verbose output")
    public boolean verbose;

    @Option(name = { "-w", "--workers" },
            description = "Worker count")
    public int workers = -1;

    @Option(name = { "-P", "--property" },
            description = "Property name=value",
            hidden = true)
    public List<String> properties;

    @Option(name = { "-p", "--properties" },
            description = "Path to properties",
            hidden = true)
    public String propertiesPath;

    @Option(name = "-e",
            description = "Show exception traces",
            hidden = true)
    public boolean exceptions;

    @Override
    public void run()
    {
        Runtime runtime = getRuntime();
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

        if (properties != null)
        {
            for (String property : properties)
            {
                final int equalsAt = property.indexOf('=');
                String name = equalsAt != -1 ? property.substring(0, equalsAt) : property;
                String value = equalsAt != -1 ? property.substring(equalsAt + 1) : "true";

                props.put(name, value);
            }
        }

        if (configURI != null)
        {
            props.setProperty(ENGINE_CONFIG_URL.name(), configURI.toString());
        }

        if (workers >= 0)
        {
            props.setProperty(ENGINE_WORKERS.name(), Integer.toString(workers));
        }

        if (verbose)
        {
            props.setProperty(ENGINE_VERBOSE.name(), Boolean.toString(verbose));
        }

        EngineConfiguration config = new EngineConfiguration(props);
        URL configURL = config.configURL();
        if ("file".equals(configURL.getProtocol()))
        {
            Path configPath = Paths.get(configURL.getPath());
            if (configPath.endsWith("zilla.yaml") && Files.notExists(configPath))
            {
                String configJSON = String.format("file:%s", configPath.resolveSibling("zilla.json"));
                props.setProperty(ENGINE_CONFIG_URL.name(), configJSON);
                configPath = Paths.get(config.configURL().getPath());
                System.out.println("zilla.yaml file not found, loading zilla.json instead");
            }
            if (configPath.getFileName().toString().endsWith(".json"))
            {
                System.out.println("warning: json syntax is deprecated, migrate to yaml");
            }
        }

        try (Engine engine = Engine.builder()
            .config(config)
            .errorHandler(this::onError)
            .build())
        {
            engine.start();

            System.out.println("started");

            runtime.addShutdownHook(new Thread(this::onShutdown));

            stop.await();

            System.out.println("stopped");

            stopped.countDown();
        }
        catch (Throwable ex)
        {
            System.out.println("error");
            rethrowUnchecked(ex);
        }
    }

    private void onError(
        Throwable error)
    {
        if (exceptions)
        {
            error.printStackTrace(System.err);
        }
        else
        {
            System.err.println(error.getMessage());
        }
        stop.countDown();
    }

    private void onShutdown()
    {
        try
        {
            stop.countDown();
            stopped.await();
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }
    }
}
