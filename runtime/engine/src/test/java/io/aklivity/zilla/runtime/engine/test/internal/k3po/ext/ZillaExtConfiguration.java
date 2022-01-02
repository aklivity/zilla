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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import io.aklivity.zilla.runtime.engine.cog.Configuration;

public final class ZillaExtConfiguration extends Configuration
{
    public static final PropertyDef<String> ZILLA_EXT_DIRECTORY;
    public static final IntPropertyDef ZILLA_EXT_BUDGETS_BUFFER_CAPACITY;
    public static final IntPropertyDef ZILLA_EXT_STREAMS_BUFFER_CAPACITY;
    public static final IntPropertyDef ZILLA_EXT_COMMAND_BUFFER_CAPACITY;
    public static final IntPropertyDef ZILLA_EXT_RESPONSE_BUFFER_CAPACITY;
    public static final IntPropertyDef ZILLA_EXT_COUNTERS_BUFFER_CAPACITY;
    public static final IntPropertyDef ZILLA_EXT_MAXIMUM_MESSAGES_PER_READ;
    public static final LongPropertyDef ZILLA_EXT_BACKOFF_MAX_SPINS;
    public static final LongPropertyDef ZILLA_EXT_BACKOFF_MAX_YIELDS;
    public static final LongPropertyDef ZILLA_EXT_BACKOFF_MIN_PARK_NANOS;
    public static final LongPropertyDef ZILLA_EXT_BACKOFF_MAX_PARK_NANOS;

    private static final ConfigurationDef ZILLA_EXT_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("k3po.zilla.ext");

        ZILLA_EXT_DIRECTORY = config.property("directory", ZillaExtConfiguration::defaultDirectory);
        ZILLA_EXT_BUDGETS_BUFFER_CAPACITY = config.property("budgets.buffer.capacity", 1024 * 1024);
        ZILLA_EXT_STREAMS_BUFFER_CAPACITY = config.property("streams.buffer.capacity", 1024 * 1024);
        ZILLA_EXT_COMMAND_BUFFER_CAPACITY = config.property("command.buffer.capacity", 1024 * 1024);
        ZILLA_EXT_RESPONSE_BUFFER_CAPACITY = config.property("response.buffer.capacity", 1024 * 1024);
        ZILLA_EXT_COUNTERS_BUFFER_CAPACITY = config.property("counters.buffer.capacity", 1024 * 1024);
        ZILLA_EXT_MAXIMUM_MESSAGES_PER_READ = config.property("maximum.messages.per.read", Integer.MAX_VALUE);
        ZILLA_EXT_BACKOFF_MAX_SPINS = config.property("backoff.idle.strategy.max.spins", 64L);
        ZILLA_EXT_BACKOFF_MAX_YIELDS = config.property("backoff.idle.strategy.max.yields", 64L);
        // TODO: shorten property name string values to match constant naming
        ZILLA_EXT_BACKOFF_MIN_PARK_NANOS = config.property("backoff.idle.strategy.min.park.period", NANOSECONDS.toNanos(64L));
        ZILLA_EXT_BACKOFF_MAX_PARK_NANOS = config.property("backoff.idle.strategy.max.park.period", MILLISECONDS.toNanos(1L));
        ZILLA_EXT_CONFIG = config;
    }

    public ZillaExtConfiguration()
    {
        super();
    }

    public ZillaExtConfiguration(
        Configuration config)
    {
        super(ZILLA_EXT_CONFIG, config);
    }

    public ZillaExtConfiguration(
        Properties properties)
    {
        super(ZILLA_EXT_CONFIG, properties);
    }

    public ZillaExtConfiguration(
        Configuration config,
        Properties defaultOverrides)
    {
        super(ZILLA_EXT_CONFIG, config, defaultOverrides);
    }

    @Override
    public Path directory()
    {
        return Paths.get(ZILLA_EXT_DIRECTORY.get(this));
    }

    public int maximumMessagesPerRead()
    {
        return ZILLA_EXT_MAXIMUM_MESSAGES_PER_READ.getAsInt(this);
    }

    public int budgetsBufferCapacity()
    {
        return ZILLA_EXT_BUDGETS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int streamsBufferCapacity()
    {
        return ZILLA_EXT_STREAMS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int commandBufferCapacity()
    {
        return ZILLA_EXT_COMMAND_BUFFER_CAPACITY.get(this);
    }

    public int responseBufferCapacity()
    {
        return ZILLA_EXT_RESPONSE_BUFFER_CAPACITY.getAsInt(this);
    }

    public int counterValuesBufferCapacity()
    {
        return ZILLA_EXT_COUNTERS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int counterLabelsBufferCapacity()
    {
        return ZILLA_EXT_COUNTERS_BUFFER_CAPACITY.getAsInt(this) * 2;
    }

    public long maxSpins()
    {
        return ZILLA_EXT_BACKOFF_MAX_SPINS.getAsLong(this);
    }

    public long maxYields()
    {
        return ZILLA_EXT_BACKOFF_MAX_YIELDS.getAsLong(this);
    }

    public long minParkNanos()
    {
        return ZILLA_EXT_BACKOFF_MIN_PARK_NANOS.getAsLong(this);
    }

    public long maxParkNanos()
    {
        return ZILLA_EXT_BACKOFF_MAX_PARK_NANOS.getAsLong(this);
    }

    private static String defaultDirectory(
        Configuration config)
    {
        String directory = "target/zilla-itests";
        String workingDir = System.getProperty("user.dir");
        if (workingDir != null)
        {
            directory = Paths.get(workingDir).resolve(directory).toString();
        }
        return directory;
    }
}
