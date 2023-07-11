/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
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
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import org.agrona.ErrorHandler;
import org.agrona.LangUtil;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import io.aklivity.zilla.runtime.engine.Configuration.PropertyDef;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineBuilder;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public final class EngineRule implements TestRule
{
    // needed by test annotations
    public static final String ENGINE_BUFFER_POOL_CAPACITY_NAME = "zilla.engine.buffer.pool.capacity";
    public static final String ENGINE_BUFFER_SLOT_CAPACITY_NAME = "zilla.engine.buffer.slot.capacity";
    public static final String ENGINE_CONFIG_URL_NAME = "zilla.engine.config.url";

    private static final long EXTERNAL_AFFINITY_MASK = 1L << (Long.SIZE - 1);
    private static final Pattern DATA_FILENAME_PATTERN = Pattern.compile("data\\d+");

    private final Properties properties;
    private final EngineBuilder builder;

    private Engine engine;

    private EngineConfiguration configuration;
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
        requireNonNull(value);
        properties.setProperty(property.name(), value.toString());
        return this;
    }

    public EngineRule configure(
        String name,
        String value)
    {
        requireNonNull(value);
        properties.setProperty(name, value);
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
        return external("test", binding);
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

    public <T extends Binding> T binding(
        Class<T> kind)
    {
        ensureDriveStarted();

        return requireNonNull(engine.binding(kind));
    }

    public EngineExtContext context()
    {
        return engine.context();
    }

    public long[][] counterIds()
    {
        return engine.counterIds();
    }

    public LongSupplier counter(
        long bindingId,
        long metricId)
    {
        return engine.counter(bindingId, metricId);
    }

    public LongConsumer counterWriter(
        long bindingId,
        long metricId,
        int core)
    {
        return engine.counterWriter(bindingId, metricId, core);
    }

    public long[][] gaugeIds()
    {
        return engine.gaugeIds();
    }

    public LongSupplier gauge(
        long bindingId,
        long metricId)
    {
        return engine.gauge(bindingId, metricId);
    }

    public LongConsumer gaugeWriter(
        long bindingId,
        long metricId,
        int core)
    {
        return engine.gaugeWriter(bindingId, metricId, core);
    }

    public long[][] histogramIds()
    {
        return engine.histogramIds();
    }

    public LongSupplier[] histogram(
        long bindingId,
        long metricId)
    {
        return engine.histogram(bindingId, metricId);
    }

    public LongConsumer histogramWriter(
        long bindingId,
        long metricId,
        int core)
    {
        return engine.histogramWriter(bindingId, metricId, core);
    }

    public LongConsumer contextCounterWriter(
        String namespace,
        String binding,
        String metric,
        int core)
    {
        return engine.context().counterWriter(namespace, binding, metric, core);
    }

    public int supplyLabelId(
        String label)
    {
        return engine.supplyLabelId(label);
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
        if (engine == null)
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
                URI configURI = URI.create(config.value());
                if (configURI.getScheme() != null)
                {
                    configure(ENGINE_CONFIG_URL, configURI.toURL());
                }
                else if (configurationRoot != null)
                {
                    String resourceName = String.format("%s/%s", configurationRoot, config.value());
                    URL configURL = testClass.getClassLoader().getResource(resourceName);
                    configure(ENGINE_CONFIG_URL, configURL);
                }
                else
                {
                    String resourceName = String.format("%s-%s", testClass.getSimpleName(), config.value());
                    URL configURL = testClass.getResource(resourceName);
                    configure(ENGINE_CONFIG_URL, configURL);
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
                engine = builder.config(config)
                                .errorHandler(errorHandler)
                                .build();

                try
                {
                    engine.start();

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
                        engine.close();
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
