/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cmd.start.internal.command;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static java.lang.Runtime.getRuntime;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.runtime.cli.ZillaCommand;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

@Command(name = "start", description = "Start engine")
public final class ZillaStartCommand extends ZillaCommand
{
    private final CountDownLatch stop = new CountDownLatch(1);
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final Collection<Throwable> errors = new LinkedHashSet<>();

    @Option(name = "-c", description = "config")
    public URI configURI = Paths.get("zilla.json").toUri();

    @Option(name = "-w", description = "workers")
    public int workers = Runtime.getRuntime().availableProcessors();

    @Option(name = "-p", description = "properties")
    public String properties = "zilla.props";

    @Override
    public void run()
    {
        Runtime runtime = getRuntime();
        Properties props = new Properties();
        props.setProperty(ENGINE_DIRECTORY.name(), ".zilla/engine");

        Path path = Paths.get(properties);
        if (Files.exists(path))
        {
            try
            {
                props.load(Files.newInputStream(path));
            }
            catch (IOException ex)
            {
                System.out.println("Failed to load properties: " + properties);
            }
        }

        EngineConfiguration config = new EngineConfiguration(props);

        try (Engine engine = Engine.builder()
            .config(config)
            .configURL(configURI.toURL())
            .threads(workers)
            .errorHandler(this::onError)
            .build())
        {
            engine.start().get();

            System.out.println("started");

            runtime.addShutdownHook(new Thread(this::onShutdown));

            stop.await();

            errors.forEach(e -> e.printStackTrace(System.err));

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
        errors.add(error);
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
