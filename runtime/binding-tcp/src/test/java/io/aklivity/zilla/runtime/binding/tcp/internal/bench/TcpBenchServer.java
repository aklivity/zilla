/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.bench;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_STREAMS_BUFFER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKER_CAPACITY;
import static io.aklivity.zilla.runtime.engine.namespace.NamespacedId.NO_NAMESPACED_ID;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.LongSupplier;

import org.agrona.CloseHelper;

import io.aklivity.zilla.runtime.binding.tcp.internal.TcpConfiguration;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

/**
 * Runs a {@code tcp} server binding with an {@code echo} exit in a standalone engine, so a load generator
 * on another machine ({@link TcpBenchClient}) can ramp connections against it. While connections are held
 * it reports, once per second, the total held, how evenly the workers hold them and the engine threads' CPU.
 * When the held connections drop back to zero it reports the per-worker distribution at the peak.
 * <p>
 * Arguments: {@code [workers] [backlog] [ports] [workerCapacity] [rebalanceTolerance]}. A worker count of
 * {@code 0} keeps the engine default, and {@code ports} is a single port or a range such as
 * {@code 12345-12348}. A small {@code workerCapacity} fills workers, so connections spill over to workers
 * with capacity.
 */
public final class TcpBenchServer
{
    private static final Path DIRECTORY = Path.of("target/zilla-bench-server");
    private static final int WORKER_CAPACITY = 131_072;
    private static final int REBALANCE_TOLERANCE = 2;
    private static final int STREAMS_BUFFER_CAPACITY = 16 * 1024 * 1024;
    private static final long INTERVAL_MILLIS = 1_000L;
    private static final int IDLE_SECONDS = 5;

    public static void main(
        String[] args) throws Exception
    {
        final int workers = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        final int backlog = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;
        final String ports = args.length > 2 ? args[2] : "12345";
        final int workerCapacity = args.length > 3 ? Integer.parseInt(args[3]) : WORKER_CAPACITY;
        final int rebalanceTolerance = args.length > 4 ? Integer.parseInt(args[4]) : REBALANCE_TOLERANCE;

        final Engine engine = start(workers, backlog, ports, workerCapacity, rebalanceTolerance);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> CloseHelper.quietClose(engine)));

        System.out.printf("server ready workers=%d backlog=%d ports=%s%n",
            workerUsage(engine).length, backlog, ports);

        report(engine);
    }

    private static Engine start(
        int workers,
        int backlog,
        String ports,
        int workerCapacity,
        int rebalanceTolerance) throws Exception
    {
        deleteDirectory(DIRECTORY);
        Files.createDirectories(DIRECTORY);

        final Path config = DIRECTORY.resolve("zilla.yaml");
        Files.writeString(config, """
            ---
            name: test
            bindings:
              net0:
                type: tcp
                kind: server
                options:
                  host: 0.0.0.0
                  port: %s
                exit: echo0
              echo0:
                type: echo
                kind: server
            """.formatted(ports));

        final Properties properties = new Properties();
        properties.setProperty(ENGINE_DIRECTORY.name(), DIRECTORY.resolve("engine").toString());
        properties.setProperty(ENGINE_CONFIG_URL.name(), config.toUri().toURL().toString());
        properties.setProperty(ENGINE_WORKER_CAPACITY.name(), Integer.toString(workerCapacity));
        properties.setProperty(ENGINE_STREAMS_BUFFER_CAPACITY.name(), Integer.toString(STREAMS_BUFFER_CAPACITY));
        properties.setProperty(TcpConfiguration.TCP_BACKLOG.name(), Integer.toString(backlog));
        properties.setProperty(TcpConfiguration.TCP_REBALANCE_TOLERANCE.name(), Integer.toString(rebalanceTolerance));

        if (workers > 0)
        {
            properties.setProperty(ENGINE_WORKERS.name(), Integer.toString(workers));
        }

        final Engine engine = Engine.builder()
            .config(new EngineConfiguration(properties))
            .errorHandler(Throwable::printStackTrace)
            .build();
        engine.start();

        return engine;
    }

    private static void report(
        Engine engine) throws InterruptedException
    {
        final ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        Map<String, Long> cpuBefore = engineThreadCpuMillis(threads);
        Map<String, Long> idleBefore = cpuBefore;
        long[] peak = new long[0];
        long peakTotal = 0L;
        int seconds = 0;

        while (true)
        {
            Thread.sleep(INTERVAL_MILLIS);
            seconds++;

            final long[] usage = workerUsage(engine);
            final long total = Arrays.stream(usage).sum();
            final Map<String, Long> cpuAfter = engineThreadCpuMillis(threads);

            if (total > 0L || peakTotal > 0L)
            {
                System.out.printf("server t=%ds total=%d max/mean=%.3f%s%n",
                    seconds, total, skew(usage, total), cpuReport(cpuBefore, cpuAfter, INTERVAL_MILLIS));
                idleBefore = cpuAfter;
            }
            else if (seconds % IDLE_SECONDS == 0)
            {
                System.out.printf("server idle t=%ds%s%n", seconds,
                    cpuReport(idleBefore, cpuAfter, IDLE_SECONDS * INTERVAL_MILLIS));
                idleBefore = cpuAfter;
            }

            if (total > peakTotal)
            {
                peakTotal = total;
                peak = usage;
            }
            else if (total == 0L && peakTotal > 0L)
            {
                System.out.printf("server peak total=%d max/mean=%.3f connections=%s%n",
                    peakTotal, skew(peak, peakTotal), Arrays.toString(peak));
                peakTotal = 0L;
                peak = new long[0];
            }

            cpuBefore = cpuAfter;
        }
    }

    private static double skew(
        long[] usage,
        long total)
    {
        final double mean = usage.length > 0 ? (double) total / usage.length : 0.0;
        final long max = Arrays.stream(usage).max().orElse(0L);

        return mean > 0.0 ? max / mean : 0.0;
    }

    private static String cpuReport(
        Map<String, Long> before,
        Map<String, Long> after,
        long intervalMillis)
    {
        long boss = 0L;
        long workerSum = 0L;
        long workerMax = 0L;
        int workerCount = 0;

        for (Map.Entry<String, Long> entry : after.entrySet())
        {
            final long delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0L);
            final long percent = delta * 100L / intervalMillis;

            if (entry.getKey().equals("engine/boss"))
            {
                boss = percent;
            }
            else if (entry.getKey().startsWith("engine/worker#"))
            {
                workerSum += percent;
                workerMax = Math.max(workerMax, percent);
                workerCount++;
            }
        }

        return " cpu%%: boss=%d workers avg=%d max=%d"
            .formatted(boss, workerCount > 0 ? workerSum / workerCount : 0L, workerMax);
    }

    private static long[] workerUsage(
        Engine engine)
    {
        long[] usage;

        try
        {
            final Field workersField = Engine.class.getDeclaredField("workers");
            workersField.setAccessible(true);
            final List<?> workerList = (List<?>) workersField.get(engine);
            final int metricId = engine.supplyLabelId("engine.workers.usage");

            usage = new long[workerList.size()];
            for (int index = 0; index < usage.length; index++)
            {
                final Object worker = workerList.get(index);
                final Method supplyGauge = worker.getClass().getMethod("supplyGauge", long.class, int.class, int.class);
                usage[index] = ((LongSupplier) supplyGauge.invoke(worker, NO_NAMESPACED_ID, metricId, 0)).getAsLong();
            }
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IllegalStateException(ex);
        }

        return usage;
    }

    private static Map<String, Long> engineThreadCpuMillis(
        ThreadMXBean threads)
    {
        final Map<String, Long> cpuMillis = new TreeMap<>();

        Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.getName().startsWith("engine/"))
            .forEach(thread -> cpuMillis.put(thread.getName(), threads.getThreadCpuTime(thread.threadId()) / 1_000_000L));

        return cpuMillis;
    }

    private static void deleteDirectory(
        Path directory) throws IOException
    {
        if (Files.exists(directory))
        {
            try (var paths = Files.walk(directory))
            {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private TcpBenchServer()
    {
    }
}
