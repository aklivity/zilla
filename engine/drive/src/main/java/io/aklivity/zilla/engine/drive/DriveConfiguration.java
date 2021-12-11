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
package io.aklivity.zilla.engine.drive;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Function;

import org.agrona.LangUtil;

import io.aklivity.zilla.engine.drive.cog.Configuration;

public class DriveConfiguration extends Configuration
{
    public static final boolean DEBUG_BUDGETS = Boolean.getBoolean("zilla.engine.debug.budgets");

    public static final PropertyDef<String> DRIVE_NAME;
    public static final PropertyDef<String> DRIVE_DIRECTORY;
    public static final PropertyDef<Path> DRIVE_CACHE_DIRECTORY;
    public static final PropertyDef<HostResolver> DRIVE_HOST_RESOLVER;
    public static final IntPropertyDef DRIVE_BUDGETS_BUFFER_CAPACITY;
    public static final IntPropertyDef DRIVE_LOAD_BUFFER_CAPACITY;
    public static final IntPropertyDef DRIVE_STREAMS_BUFFER_CAPACITY;
    public static final IntPropertyDef DRIVE_COMMAND_BUFFER_CAPACITY;
    public static final IntPropertyDef DRIVE_RESPONSE_BUFFER_CAPACITY;
    public static final IntPropertyDef DRIVE_COUNTERS_BUFFER_CAPACITY;
    public static final IntPropertyDef DRIVE_BUFFER_POOL_CAPACITY;
    public static final IntPropertyDef DRIVE_BUFFER_SLOT_CAPACITY;
    public static final IntPropertyDef DRIVE_ROUTES_BUFFER_CAPACITY;
    public static final BooleanPropertyDef DRIVE_TIMESTAMPS;
    public static final IntPropertyDef DRIVE_MAXIMUM_MESSAGES_PER_READ;
    public static final IntPropertyDef DRIVE_MAXIMUM_EXPIRATIONS_PER_POLL;
    public static final IntPropertyDef DRIVE_TASK_PARALLELISM;
    public static final LongPropertyDef DRIVE_BACKOFF_MAX_SPINS;
    public static final LongPropertyDef DRIVE_BACKOFF_MAX_YIELDS;
    public static final LongPropertyDef DRIVE_BACKOFF_MIN_PARK_NANOS;
    public static final LongPropertyDef DRIVE_BACKOFF_MAX_PARK_NANOS;
    public static final BooleanPropertyDef DRIVE_DRAIN_ON_CLOSE;
    public static final BooleanPropertyDef DRIVE_SYNTHETIC_ABORT;
    public static final LongPropertyDef DRIVE_ROUTED_DELAY_MILLIS;
    public static final LongPropertyDef DRIVE_CREDITOR_CHILD_CLEANUP_LINGER_MILLIS;
    public static final BooleanPropertyDef DRIVE_VERBOSE;

    private static final ConfigurationDef DRIVE_CONFIG;


    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.engine");
        DRIVE_NAME = config.property("name", "drive");
        DRIVE_DIRECTORY = config.property("directory", ".");
        DRIVE_CACHE_DIRECTORY = config.property(Path.class, "cache.directory", DriveConfiguration::cacheDirectory, "cache");
        DRIVE_HOST_RESOLVER = config.property(HostResolver.class, "host.resolver",
                DriveConfiguration::decodeHostResolver, DriveConfiguration::defaultHostResolver);
        DRIVE_BUDGETS_BUFFER_CAPACITY = config.property("budgets.buffer.capacity", 1024 * 1024);
        DRIVE_LOAD_BUFFER_CAPACITY = config.property("load.buffer.capacity", 1024 * 8);
        DRIVE_STREAMS_BUFFER_CAPACITY = config.property("streams.buffer.capacity", 1024 * 1024);
        DRIVE_COMMAND_BUFFER_CAPACITY = config.property("command.buffer.capacity", 1024 * 1024);
        DRIVE_RESPONSE_BUFFER_CAPACITY = config.property("response.buffer.capacity", 1024 * 1024);
        DRIVE_COUNTERS_BUFFER_CAPACITY = config.property("counters.buffer.capacity", 1024 * 1024);
        DRIVE_BUFFER_POOL_CAPACITY = config.property("buffer.pool.capacity", DriveConfiguration::defaultBufferPoolCapacity);
        DRIVE_BUFFER_SLOT_CAPACITY = config.property("buffer.slot.capacity", 64 * 1024);
        DRIVE_ROUTES_BUFFER_CAPACITY = config.property("routes.buffer.capacity", 1024 * 1024);
        DRIVE_TIMESTAMPS = config.property("timestamps", true);
        DRIVE_MAXIMUM_MESSAGES_PER_READ = config.property("maximum.messages.per.read", Integer.MAX_VALUE);
        DRIVE_MAXIMUM_EXPIRATIONS_PER_POLL = config.property("maximum.expirations.per.poll", Integer.MAX_VALUE);
        DRIVE_TASK_PARALLELISM = config.property("task.parallelism", 1);
        DRIVE_BACKOFF_MAX_SPINS = config.property("backoff.idle.strategy.max.spins", 64L);
        DRIVE_BACKOFF_MAX_YIELDS = config.property("backoff.idle.strategy.max.yields", 64L);
        // TODO: shorten property name string values to match constant naming
        DRIVE_BACKOFF_MIN_PARK_NANOS = config.property("backoff.idle.strategy.min.park.period", NANOSECONDS.toNanos(64L));
        DRIVE_BACKOFF_MAX_PARK_NANOS = config.property("backoff.idle.strategy.max.park.period", MILLISECONDS.toNanos(1L));
        DRIVE_DRAIN_ON_CLOSE = config.property("drain.on.close", false);
        DRIVE_SYNTHETIC_ABORT = config.property("synthetic.abort", false);
        DRIVE_ROUTED_DELAY_MILLIS = config.property("routed.delay.millis", 0L);
        DRIVE_CREDITOR_CHILD_CLEANUP_LINGER_MILLIS = config.property("child.cleanup.linger", SECONDS.toMillis(5L));
        DRIVE_VERBOSE = config.property("verbose", false);
        DRIVE_CONFIG = config;
    }

    public DriveConfiguration(
        Configuration config)
    {
        super(DRIVE_CONFIG, config);
    }

    public DriveConfiguration(
        Properties properties)
    {
        super(DRIVE_CONFIG, properties);
    }

    public DriveConfiguration(
        Configuration config,
        Properties defaultOverrides)
    {
        super(DRIVE_CONFIG, config, defaultOverrides);
    }

    public DriveConfiguration()
    {
        super(DRIVE_CONFIG, new Configuration());
    }

    public String name()
    {
        return DRIVE_NAME.get(this);
    }

    @Override
    public final Path directory()
    {
        return Paths.get(DRIVE_DIRECTORY.get(this));
    }

    public final Path cacheDirectory()
    {
        return DRIVE_CACHE_DIRECTORY.get(this);
    }

    public int bufferPoolCapacity()
    {
        return DRIVE_BUFFER_POOL_CAPACITY.getAsInt(this);
    }

    public int bufferSlotCapacity()
    {
        return DRIVE_BUFFER_SLOT_CAPACITY.getAsInt(this);
    }

    public int maximumStreamsCount()
    {
        return bufferPoolCapacity() / bufferSlotCapacity();
    }

    public int maximumMessagesPerRead()
    {
        return DRIVE_MAXIMUM_MESSAGES_PER_READ.getAsInt(this);
    }

    public int maximumExpirationsPerPoll()
    {
        return DRIVE_MAXIMUM_EXPIRATIONS_PER_POLL.getAsInt(this);
    }

    public int taskParallelism()
    {
        return DRIVE_TASK_PARALLELISM.getAsInt(this);
    }

    public int budgetsBufferCapacity()
    {
        return DRIVE_BUDGETS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int streamsBufferCapacity()
    {
        return DRIVE_STREAMS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int commandBufferCapacity()
    {
        return DRIVE_COMMAND_BUFFER_CAPACITY.get(this);
    }

    public int responseBufferCapacity()
    {
        return DRIVE_RESPONSE_BUFFER_CAPACITY.getAsInt(this);
    }

    public int loadBufferCapacity()
    {
        return DRIVE_LOAD_BUFFER_CAPACITY.getAsInt(this);
    }

    public int routesBufferCapacity()
    {
        return DRIVE_ROUTES_BUFFER_CAPACITY.get(this);
    }

    public int counterValuesBufferCapacity()
    {
        return DRIVE_COUNTERS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int counterLabelsBufferCapacity()
    {
        return DRIVE_COUNTERS_BUFFER_CAPACITY.getAsInt(this) * 2;
    }

    public boolean timestamps()
    {
        return DRIVE_TIMESTAMPS.getAsBoolean(this);
    }

    public long maxSpins()
    {
        return DRIVE_BACKOFF_MAX_SPINS.getAsLong(this);
    }

    public long maxYields()
    {
        return DRIVE_BACKOFF_MAX_YIELDS.getAsLong(this);
    }

    public long minParkNanos()
    {
        return DRIVE_BACKOFF_MIN_PARK_NANOS.getAsLong(this);
    }

    public long maxParkNanos()
    {
        return DRIVE_BACKOFF_MAX_PARK_NANOS.getAsLong(this);
    }

    public boolean drainOnClose()
    {
        return DRIVE_DRAIN_ON_CLOSE.getAsBoolean(this);
    }

    public boolean syntheticAbort()
    {
        return DRIVE_SYNTHETIC_ABORT.getAsBoolean(this);
    }

    public long routedDelayMillis()
    {
        return DRIVE_ROUTED_DELAY_MILLIS.getAsLong(this);
    }

    public long childCleanupLingerMillis()
    {
        return DRIVE_CREDITOR_CHILD_CLEANUP_LINGER_MILLIS.getAsLong(this);
    }

    public boolean verbose()
    {
        return DRIVE_VERBOSE.getAsBoolean(this);
    }

    public Function<String, InetAddress[]> hostResolver()
    {
        return DRIVE_HOST_RESOLVER.get(this)::resolve;
    }

    private static int defaultBufferPoolCapacity(
        Configuration config)
    {
        return DRIVE_BUFFER_SLOT_CAPACITY.get(config) * 64;
    }

    private static Path cacheDirectory(
        Configuration config,
        String cacheDirectory)
    {
        return Paths.get(DRIVE_DIRECTORY.get(config)).resolve(cacheDirectory);
    }

    @FunctionalInterface
    private interface HostResolver
    {
        InetAddress[] resolve(
            String name);
    }

    private static HostResolver decodeHostResolver(
        Configuration config,
        String value)
    {
        HostResolver resolver = null;

        try
        {
            MethodType signature = MethodType.methodType(InetAddress[].class, String.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            resolver = name ->
            {
                InetAddress[] addresses = null;

                try
                {
                    addresses = (InetAddress[]) method.invoke(name);
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return addresses;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return resolver;
    }

    private static HostResolver defaultHostResolver(
        Configuration config)
    {
        return name ->
        {
            InetAddress[] addresses = null;

            try
            {
                addresses = InetAddress.getAllByName(name);
            }
            catch (UnknownHostException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }

            return addresses;
        };
    }
}
