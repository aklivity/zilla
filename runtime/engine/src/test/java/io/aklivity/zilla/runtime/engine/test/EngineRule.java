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
package io.aklivity.zilla.runtime.engine.test;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_COMMAND_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_COUNTERS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_RESPONSE_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_ROUTED_DELAY_MILLIS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_SYNTHETIC_ABORT;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.nio.file.Files.exists;
import static java.util.Objects.requireNonNull;
import static org.junit.runners.model.MultipleFailureException.assertEmpty;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.agrona.ErrorHandler;
import org.agrona.LangUtil;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineBuilder;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineStats;
import io.aklivity.zilla.runtime.engine.cog.Cog;
import io.aklivity.zilla.runtime.engine.cog.Configuration.PropertyDef;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public final class EngineRule implements TestRule
{
    // needed by test annotations
    public static final String ENGINE_BUFFER_POOL_CAPACITY_NAME = "zilla.engine.buffer.pool.capacity";
    public static final String ENGINE_BUFFER_SLOT_CAPACITY_NAME = "zilla.engine.buffer.slot.capacity";

    private static final long EXTERNAL_AFFINITY_MASK = 1L << (Long.SIZE - 1);
    private static final Pattern DATA_FILENAME_PATTERN = Pattern.compile("data\\d+");

    private final Properties properties;
    private final EngineBuilder builder;

    private Engine drive;

    private EngineConfiguration configuration;
    private URL configURL;
    private String configurationRoot;
    private boolean clean;

    public EngineRule()
    {
        this.builder = Engine.builder();
        this.properties = new Properties();

        configure(ENGINE_DRAIN_ON_CLOSE, true);
        configure(ENGINE_SYNTHETIC_ABORT, true);
        configure(ENGINE_ROUTED_DELAY_MILLIS, 500L);
        configure(ENGINE_WORKERS, 1);
    }

    public EngineRule directory(String directory)
    {
        return configure(ENGINE_DIRECTORY, directory);
    }

    public EngineRule commandBufferCapacity(int commandBufferCapacity)
    {
        return configure(ENGINE_COMMAND_BUFFER_CAPACITY, commandBufferCapacity);
    }

    public EngineRule responseBufferCapacity(int responseBufferCapacity)
    {
        return configure(ENGINE_RESPONSE_BUFFER_CAPACITY, responseBufferCapacity);
    }

    public EngineRule counterValuesBufferCapacity(int counterValuesBufferCapacity)
    {
        return configure(ENGINE_COUNTERS_BUFFER_CAPACITY, counterValuesBufferCapacity);
    }

    public EngineRule streamsBufferCapacity(int streamsBufferCapacity)
    {
        return configure(ENGINE_STREAMS_BUFFER_CAPACITY, streamsBufferCapacity);
    }

    public <T> EngineRule configure(
        PropertyDef<T> property,
        T value)
    {
        properties.setProperty(property.name(), value.toString());
        return this;
    }

    public EngineRule configure(
        String name,
        String value)
    {
        properties.setProperty(name, value);
        return this;
    }

    public EngineRule configURI(
        URL configURL)
    {
        this.configURL = configURL;
        return this;
    }

    public EngineRule configurationRoot(
        String configurationRoot)
    {
        this.configurationRoot = configurationRoot;
        return this;
    }

    public EngineRule external(
        String binding)
    {
        return external("default", binding);
    }

    public EngineRule external(
        String namespace,
        String binding)
    {
        builder.affinity(namespace, binding, EXTERNAL_AFFINITY_MASK);
        return this;
    }

    public EngineRule clean()
    {
        this.clean = true;
        return this;
    }

    public <T extends Cog> T cog(
        Class<T> kind)
    {
        ensureDriveStarted();

        return requireNonNull(drive.cog(kind));
    }

    public EngineStats stats(
        String namespace,
        String binding)
    {
        return drive.stats(namespace, binding);
    }

    public long counter(
        String name)
    {
        ensureDriveStarted();

        return drive.counter(name);
    }

    private EngineConfiguration configuration()
    {
        if (configuration == null)
        {
            configuration = new EngineConfiguration(properties);
        }
        return configuration;
    }

    private void ensureDriveStarted()
    {
        if (drive == null)
        {
            throw new IllegalStateException("Drive not started");
        }
    }

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        Class<?> testClass = description.getTestClass();
        final String testMethod = description.getMethodName().replaceAll("\\[.*\\]", "");
        try
        {
            Configure[] configures = testClass
                       .getDeclaredMethod(testMethod)
                       .getAnnotationsByType(Configure.class);
            Arrays.stream(configures).forEach(
                p -> properties.setProperty(p.name(), p.value()));

            Configuration config = description.getAnnotation(Configuration.class);
            if (config != null)
            {
                if (configurationRoot != null)
                {
                    String resourceName = String.format("%s/%s", configurationRoot, config.value());

                    configURL = testClass.getClassLoader().getResource(resourceName);
                }
                else
                {
                    String resourceName = String.format("%s-%s", testClass.getSimpleName(), config.value());

                    configURL = testClass.getResource(resourceName);
                }
            }

            cleanup();
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }


        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                EngineConfiguration config = configuration();
                final Thread baseThread = Thread.currentThread();
                final List<Throwable> errors = new ArrayList<>();
                final ErrorHandler errorHandler = ex ->
                {
                    errors.add(ex);
                    baseThread.interrupt();
                };
                drive = builder.config(config)
                                 .configURL(configURL)
                                 .errorHandler(errorHandler)
                                 .build();

                try
                {
                    drive.start().get();

                    base.evaluate();
                }
                catch (Throwable t)
                {
                    errors.add(t);
                }
                finally
                {
                    try
                    {
                        drive.close();
                    }
                    catch (Throwable t)
                    {
                        errors.add(t);
                    }
                    finally
                    {
                        assertEmpty(errors);
                    }
                }
            }
        };
    }

    private void cleanup() throws IOException
    {
        EngineConfiguration config = configuration();
        Path directory = config.directory();
        Path cacheDirectory = config.cacheDirectory();

        if (clean && exists(directory))
        {
            Files.walk(directory, FOLLOW_LINKS)
                 .filter(this::shouldDeletePath)
                 .map(Path::toFile)
                 .forEach(File::delete);
        }

        if (clean && exists(cacheDirectory))
        {
            Files.walk(cacheDirectory)
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }

    private boolean shouldDeletePath(
        Path path)
    {
        String filename = path.getFileName().toString();
        return "control".equals(filename) ||
               "routes".equals(filename) ||
               "streams".equals(filename) ||
               "labels".equals(filename) ||
               DATA_FILENAME_PATTERN.matcher(filename).matches();
    }
}
