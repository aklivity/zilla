/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.BitUtil.findNextPositivePowerOfTwo;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

import org.agrona.LangUtil;

import com.sun.management.OperatingSystemMXBean;

import io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout;

public class EngineConfiguration extends Configuration
{
    public static final String ZILLA_NAME_PROPERTY = "zilla.name";
    public static final String ZILLA_DIRECTORY_PROPERTY = "zilla.directory";

    public static final boolean DEBUG_BUDGETS = Boolean.getBoolean("zilla.engine.debug.budgets");

    public static final PropertyDef<URL> ENGINE_CONFIG_URL;
    public static final PropertyDef<URI> ENGINE_CONFIG_URI;
    public static final BooleanPropertyDef ENGINE_CONFIG_WATCH;
    public static final IntPropertyDef ENGINE_CONFIG_POLL_INTERVAL_SECONDS;
    public static final PropertyDef<String> ENGINE_NAME;
    public static final PropertyDef<String> ENGINE_DIRECTORY;
    public static final PropertyDef<Path> ENGINE_CACHE_DIRECTORY;
    public static final PropertyDef<HostResolver> ENGINE_HOST_RESOLVER;
    public static final IntPropertyDef ENGINE_WORKER_CAPACITY;
    public static final DoublePropertyDef ENGINE_MEMORY_PERCENTAGE;
    public static final IntPropertyDef ENGINE_BUFFER_POOL_CAPACITY;
    public static final IntPropertyDef ENGINE_BUFFER_SLOT_CAPACITY;
    public static final IntPropertyDef ENGINE_STREAMS_BUFFER_CAPACITY;
    public static final IntPropertyDef ENGINE_EVENTS_BUFFER_CAPACITY;
    public static final IntPropertyDef ENGINE_COUNTERS_BUFFER_CAPACITY;
    public static final IntPropertyDef ENGINE_BUDGETS_BUFFER_CAPACITY;
    public static final BooleanPropertyDef ENGINE_TIMESTAMPS;
    public static final IntPropertyDef ENGINE_MAXIMUM_MESSAGES_PER_READ;
    public static final IntPropertyDef ENGINE_MAXIMUM_EXPIRATIONS_PER_POLL;
    public static final IntPropertyDef ENGINE_TASK_PARALLELISM;
    public static final LongPropertyDef ENGINE_BACKOFF_MAX_SPINS;
    public static final LongPropertyDef ENGINE_BACKOFF_MAX_YIELDS;
    public static final LongPropertyDef ENGINE_BACKOFF_MIN_PARK_NANOS;
    public static final LongPropertyDef ENGINE_BACKOFF_MAX_PARK_NANOS;
    public static final BooleanPropertyDef ENGINE_DRAIN_ON_CLOSE;
    public static final BooleanPropertyDef ENGINE_SYNTHETIC_ABORT;
    public static final LongPropertyDef ENGINE_ROUTED_DELAY_MILLIS;
    public static final LongPropertyDef ENGINE_CREDITOR_CHILD_CLEANUP_LINGER_MILLIS;
    public static final BooleanPropertyDef ENGINE_DEBUG;
    public static final BooleanPropertyDef ENGINE_VERBOSE;
    public static final BooleanPropertyDef ENGINE_VERBOSE_EXCEPTIONS;
    public static final BooleanPropertyDef ENGINE_VERBOSE_SCHEMA;
    public static final BooleanPropertyDef ENGINE_VERBOSE_SCHEMA_PLAIN;
    public static final BooleanPropertyDef ENGINE_VERBOSE_COMPOSITES;
    public static final IntPropertyDef ENGINE_WORKERS;
    public static final PropertyDef<String> ENGINE_CACERTS_STORE_TYPE;
    public static final PropertyDef<String> ENGINE_CACERTS_STORE;
    public static final PropertyDef<String> ENGINE_CACERTS_STORE_PASS;
    public static final PropertyDef<ErrorReporter> ENGINE_ERROR_REPORTER;

    private static final ConfigurationDef ENGINE_CONFIG;


    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.engine");
        ENGINE_CONFIG_URL = config.property(URL.class, "config.url", EngineConfiguration::configURL, "file:zilla.yaml");
        ENGINE_CONFIG_URI = config.property(URI.class, "config.uri", EngineConfiguration::decodeConfigURI,
            EngineConfiguration::defaultConfigURI);
        ENGINE_CONFIG_WATCH = config.property("config.watch", true);
        ENGINE_CONFIG_POLL_INTERVAL_SECONDS = config.property("config.poll.interval.seconds", 60);
        ENGINE_NAME = config.property("name", EngineConfiguration::defaultName);
        ENGINE_DIRECTORY = config.property("directory", EngineConfiguration::defaultDirectory);
        ENGINE_CACHE_DIRECTORY = config.property(Path.class, "cache.directory", EngineConfiguration::cacheDirectory, "cache");
        ENGINE_HOST_RESOLVER = config.property(HostResolver.class, "host.resolver",
                EngineConfiguration::decodeHostResolver, EngineConfiguration::defaultHostResolver);
        ENGINE_MEMORY_PERCENTAGE = config.property("memory.percentage", 0.75);
        ENGINE_WORKER_CAPACITY = config.property("worker.capacity", EngineConfiguration::defaultWorkersCapacity);
        ENGINE_BUFFER_POOL_CAPACITY = config.property("buffer.pool.capacity", EngineConfiguration::defaultBufferPoolCapacity);
        ENGINE_BUFFER_SLOT_CAPACITY = config.property("buffer.slot.capacity", 64 * 1024);
        ENGINE_STREAMS_BUFFER_CAPACITY = config.property("streams.buffer.capacity",
                EngineConfiguration::defaultStreamsBufferCapacity);
        ENGINE_EVENTS_BUFFER_CAPACITY = config.property("events.buffer.capacity",
                EngineConfiguration::defaultEventsBufferCapacity);
        ENGINE_BUDGETS_BUFFER_CAPACITY = config.property("budgets.buffer.capacity",
                EngineConfiguration::defaultBudgetsBufferCapacity);
        ENGINE_COUNTERS_BUFFER_CAPACITY = config.property("counters.buffer.capacity", 1024 * 1024);
        ENGINE_TIMESTAMPS = config.property("timestamps", true);
        ENGINE_MAXIMUM_MESSAGES_PER_READ = config.property("maximum.messages.per.read", Integer.MAX_VALUE);
        ENGINE_MAXIMUM_EXPIRATIONS_PER_POLL = config.property("maximum.expirations.per.poll", Integer.MAX_VALUE);
        ENGINE_TASK_PARALLELISM = config.property("task.parallelism", 1);
        ENGINE_BACKOFF_MAX_SPINS = config.property("backoff.idle.strategy.max.spins", 64L);
        ENGINE_BACKOFF_MAX_YIELDS = config.property("backoff.idle.strategy.max.yields", 64L);
        ENGINE_BACKOFF_MIN_PARK_NANOS = config.property("backoff.min.park.nanos", NANOSECONDS.toNanos(64L));
        ENGINE_BACKOFF_MAX_PARK_NANOS = config.property("backoff.max.park.nanos", MILLISECONDS.toNanos(100L));
        ENGINE_DRAIN_ON_CLOSE = config.property("drain.on.close", false);
        ENGINE_SYNTHETIC_ABORT = config.property("synthetic.abort", false);
        ENGINE_ROUTED_DELAY_MILLIS = config.property("routed.delay.millis", 0L);
        ENGINE_CREDITOR_CHILD_CLEANUP_LINGER_MILLIS = config.property("child.cleanup.linger", SECONDS.toMillis(5L));
        ENGINE_DEBUG = config.property("debug", false);
        ENGINE_VERBOSE = config.property("verbose", false);
        ENGINE_VERBOSE_COMPOSITES = config.property("verbose.composites", false);
        ENGINE_VERBOSE_SCHEMA = config.property("verbose.schema", false);
        ENGINE_VERBOSE_SCHEMA_PLAIN = config.property("verbose.schema.plain", false);
        ENGINE_VERBOSE_EXCEPTIONS = config.property("exception-traces", false);
        ENGINE_WORKERS = config.property("workers", Runtime.getRuntime().availableProcessors());
        ENGINE_CACERTS_STORE_TYPE = config.property("cacerts.store.type", EngineConfiguration::cacertsStoreTypeDefault);
        ENGINE_CACERTS_STORE = config.property("cacerts.store", EngineConfiguration::cacertsStoreDefault);
        ENGINE_CACERTS_STORE_PASS = config.property("cacerts.store.pass");
        ENGINE_ERROR_REPORTER = config.property(ErrorReporter.class, "error.reporter",
            EngineConfiguration::decodeErrorReporter, EngineConfiguration::defaultErrorReporter);
        ENGINE_CONFIG = config;
    }

    public EngineConfiguration(
        Configuration config)
    {
        super(ENGINE_CONFIG, config);
    }

    public EngineConfiguration(
        Properties properties)
    {
        super(ENGINE_CONFIG, properties);
    }

    public EngineConfiguration(
        Configuration config,
        Properties defaultOverrides)
    {
        super(ENGINE_CONFIG, config, defaultOverrides);
    }

    public EngineConfiguration()
    {
        super(ENGINE_CONFIG, new Configuration());
    }

    @Deprecated
    public URL configURL()
    {
        return ENGINE_CONFIG_URL.get(this);
    }

    public URI configURI()
    {
        return ENGINE_CONFIG_URI.get(this);
    }

    public boolean configWatch()
    {
        return ENGINE_CONFIG_WATCH.get(this);
    }

    public int configPollIntervalSeconds()
    {
        return ENGINE_CONFIG_POLL_INTERVAL_SECONDS.getAsInt(this);
    }

    public String name()
    {
        return ENGINE_NAME.get(this);
    }

    @Override
    public final Path directory()
    {
        return Paths.get(ENGINE_DIRECTORY.get(this));
    }

    public final Path cacheDirectory()
    {
        return ENGINE_CACHE_DIRECTORY.get(this);
    }

    public int bufferPoolCapacity()
    {
        return ENGINE_BUFFER_POOL_CAPACITY.getAsInt(this);
    }

    public int bufferSlotCapacity()
    {
        return ENGINE_BUFFER_SLOT_CAPACITY.getAsInt(this);
    }

    public int budgetsBufferCapacity()
    {
        return ENGINE_BUDGETS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int streamsBufferCapacity()
    {
        return ENGINE_STREAMS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int eventsBufferCapacity()
    {
        return ENGINE_EVENTS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int countersBufferCapacity()
    {
        return ENGINE_COUNTERS_BUFFER_CAPACITY.getAsInt(this);
    }

    public int maximumMessagesPerRead()
    {
        return ENGINE_MAXIMUM_MESSAGES_PER_READ.getAsInt(this);
    }

    public int maximumExpirationsPerPoll()
    {
        return ENGINE_MAXIMUM_EXPIRATIONS_PER_POLL.getAsInt(this);
    }

    public int taskParallelism()
    {
        return ENGINE_TASK_PARALLELISM.getAsInt(this);
    }

    public boolean timestamps()
    {
        return ENGINE_TIMESTAMPS.getAsBoolean(this);
    }

    public long maxSpins()
    {
        return ENGINE_BACKOFF_MAX_SPINS.getAsLong(this);
    }

    public long maxYields()
    {
        return ENGINE_BACKOFF_MAX_YIELDS.getAsLong(this);
    }

    public long minParkNanos()
    {
        return ENGINE_BACKOFF_MIN_PARK_NANOS.getAsLong(this);
    }

    public long maxParkNanos()
    {
        return ENGINE_BACKOFF_MAX_PARK_NANOS.getAsLong(this);
    }

    public boolean drainOnClose()
    {
        return ENGINE_DRAIN_ON_CLOSE.getAsBoolean(this);
    }

    public boolean syntheticAbort()
    {
        return ENGINE_SYNTHETIC_ABORT.getAsBoolean(this);
    }

    public long routedDelayMillis()
    {
        return ENGINE_ROUTED_DELAY_MILLIS.getAsLong(this);
    }

    public long childCleanupLingerMillis()
    {
        return ENGINE_CREDITOR_CHILD_CLEANUP_LINGER_MILLIS.getAsLong(this);
    }

    public boolean debug()
    {
        return ENGINE_DEBUG.getAsBoolean(this);
    }

    public boolean verbose()
    {
        return ENGINE_VERBOSE.getAsBoolean(this);
    }

    public boolean verboseSchema()
    {
        return ENGINE_VERBOSE_SCHEMA.getAsBoolean(this);
    }

    public boolean verboseSchemaPlain()
    {
        return ENGINE_VERBOSE_SCHEMA_PLAIN.getAsBoolean(this);
    }

    public boolean verboseComposites()
    {
        return ENGINE_VERBOSE_COMPOSITES.getAsBoolean(this);
    }

    public int workers()
    {
        return ENGINE_WORKERS.getAsInt(this);
    }

    public String cacertsStoreType()
    {
        return ENGINE_CACERTS_STORE_TYPE.get(this);
    }

    public String cacertsStore()
    {
        return ENGINE_CACERTS_STORE.get(this);
    }

    public String cacertsStorePass()
    {
        return ENGINE_CACERTS_STORE_PASS.get(this);
    }

    public Consumer<Throwable> errorReporter()
    {
        return ENGINE_ERROR_REPORTER.get(this)::report;
    }

    public Function<String, InetAddress[]> hostResolver()
    {
        return ENGINE_HOST_RESOLVER.get(this)::resolve;
    }

    private static int defaultBufferPoolCapacity(
        Configuration config)
    {
        return ENGINE_BUFFER_SLOT_CAPACITY.get(config) * ENGINE_WORKER_CAPACITY.getAsInt(config);
    }

    private static int defaultStreamsBufferCapacity(
        Configuration config)
    {
        return ENGINE_BUFFER_SLOT_CAPACITY.get(config) * ENGINE_WORKER_CAPACITY.getAsInt(config);
    }

    private static int defaultEventsBufferCapacity(
        Configuration config)
    {
        return 4 * ENGINE_BUFFER_SLOT_CAPACITY.get(config);
    }

    private static int defaultBudgetsBufferCapacity(
        Configuration config)
    {
        // more consistent with original defaults
        return BudgetsLayout.SIZEOF_BUDGET_ENTRY * 512 * ENGINE_WORKER_CAPACITY.getAsInt(config);
    }

    private static int defaultWorkersCapacity(
        Configuration config)
    {
        OperatingSystemMXBean osBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        final int numberOfCores = osBean.getAvailableProcessors();
        final long totalMemorySize = osBean.getTotalMemorySize();

        final int slotCapacity = ENGINE_BUFFER_SLOT_CAPACITY.get(config);
        final double percentMemory = ENGINE_MEMORY_PERCENTAGE.get(config);

        long maxAllowedForBuffers = (long) (percentMemory * totalMemorySize);

        // Streams + Pool
        long bufferCapacity = slotCapacity + slotCapacity;
        long eventsBufferCapacity = ENGINE_EVENTS_BUFFER_CAPACITY.get(config);
        long budgetBufferCapacity = BudgetsLayout.SIZEOF_BUDGET_ENTRY * 512L;
        long totalBufferCapacity = numberOfCores * (bufferCapacity + budgetBufferCapacity + eventsBufferCapacity);
        int newWorkersCapacity = (int) (maxAllowedForBuffers / totalBufferCapacity);

        newWorkersCapacity = findNextPositivePowerOfTwo(newWorkersCapacity);

        return newWorkersCapacity;
    }

    private static URL configURL(
        Configuration config,
        String url)
    {
        URL configURL = null;
        try
        {
            URI uri = URI.create(url);
            if (uri.getScheme() == null)
            {
                uri = URI.create(String.format("file:%s", url));
            }
            configURL = uri.toURL();
        }
        catch (MalformedURLException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        return configURL;
    }

    private static Path cacheDirectory(
        Configuration config,
        String cacheDirectory)
    {
        return Paths.get(ENGINE_DIRECTORY.get(config)).resolve(cacheDirectory);
    }

    @FunctionalInterface
    private interface HostResolver
    {
        InetAddress[] resolve(
            String name);
    }

    @FunctionalInterface
    private interface ErrorReporter
    {
        void report(
            Throwable ex);
    }

    private static String defaultName(
        Configuration config)
    {
        return System.getProperty(ZILLA_NAME_PROPERTY, "zilla");
    }

    private static String defaultDirectory(
        Configuration config)
    {
        return System.getProperty(ZILLA_DIRECTORY_PROPERTY, ".");
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

    private static URI decodeConfigURI(
        Configuration config,
        String value)
    {
        return value.startsWith("file:")
            ? new File(value.substring("file:".length())).toURI()
            : URI.create(value);
    }

    private static URI defaultConfigURI(
        Configuration config)
    {
        URL url = ENGINE_CONFIG_URL.get(config);
        return decodeConfigURI(config, url.toString());
    }

    private static String cacertsStoreTypeDefault(
        Configuration config)
    {
        return System.getProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
    }

    private static String cacertsStoreDefault(
        Configuration config)
    {
        return System.getProperty("javax.net.ssl.trustStore");
    }

    private static ErrorReporter defaultErrorReporter(
        Configuration config)
    {
        boolean exceptions = ENGINE_VERBOSE_EXCEPTIONS.get(config);

        return exceptions
            ? e -> e.printStackTrace(System.err)
            : e -> System.err.println(e.getMessage());
    }

    private static ErrorReporter decodeErrorReporter(
        Configuration config,
        String value)
    {
        ErrorReporter reporter = null;

        try
        {
            MethodType signature = MethodType.methodType(Void.class, Throwable.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            reporter = ex ->
            {
                try
                {
                    method.invoke(ex);
                }
                catch (Throwable t)
                {
                    LangUtil.rethrowUnchecked(t);
                }
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return reporter;
    }
}
