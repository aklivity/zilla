/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.store.memory.internal.bench;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.store.Store;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreFactorySpi;

/**
 * Prices the {@link StoreHandler} operations of the {@code memory} store for a per-client session state
 * workload: one entry per client under {@code client-<n>:state}, holding a String value of 100, 500 or
 * 2048 bytes, in a pre-populated store of 100,000 or 1,000,000 keys.
 * <p>
 * The handler is the real one, supplied by the store's own factory. The {@link EngineContext} hands it
 * a dispatcher that queues tasks the way an engine worker does, and every operation spins that dispatcher
 * on the benchmark thread until its completion callback has run, so the asynchronous completion is inside
 * the measurement. Each key is built per call from its client number, the way a caller holding a client id
 * does, and keys are visited in a scattered order that walks the whole population.
 * <ul>
 * <li>{@code overhead} builds a key and round-trips one task through the dispatcher without touching the
 * store, the floor shared by every operation below.</li>
 * <li>{@code getHit} and {@code getMiss} read a present and an absent key.</li>
 * <li>{@code putNoTtl} and {@code putTtl} overwrite a present key without and with a 30 second ttl.</li>
 * <li>{@code replace} compares-and-sets the whole value, alternating between two values on successive
 * passes over the keys so the expected value always matches.</li>
 * <li>{@code delete} and {@code getAndDelete} remove present keys; each invocation removes a batch of keys
 * that are put back before the invocation starts, outside the measurement.</li>
 * <li>{@code lockUnlock} and {@code lockRenewUnlock} acquire, optionally renew, and release the lock on a
 * client's state key.</li>
 * <li>{@code qos2Exchange} models the state traffic of one QoS 2 exchange on a key not yet present: put
 * (packet id in flight), replace (state advance), delete (cleared).</li>
 * </ul>
 * Each benchmark invocation is one operation, except {@code lockUnlock} (two), {@code lockRenewUnlock}
 * (three) and {@code qos2Exchange} (three), which report per sequence. Read {@code gc.alloc.rate.norm}
 * (B/op) from the {@code gc} profiler.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@Fork(value = 2, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
@Warmup(iterations = 5, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 8, time = 1, timeUnit = SECONDS)
@OutputTimeUnit(MICROSECONDS)
public class MemoryStoreBM
{
    private static final int BATCH = 20_000;
    private static final int STRIDE = 7_919;
    private static final Duration TTL = Duration.ofSeconds(30);

    @Benchmark
    public String overhead(
        Fixture fixture)
    {
        final String key = fixture.key(fixture.next());

        fixture.dispatch();

        return key;
    }

    @Benchmark
    public String getHit(
        Fixture fixture)
    {
        fixture.store.get(fixture.key(fixture.next()), fixture.onGet);

        return fixture.await();
    }

    @Benchmark
    public String getMiss(
        Fixture fixture)
    {
        fixture.store.get(fixture.key(fixture.population + fixture.next()), fixture.onGet);

        return fixture.await();
    }

    @Benchmark
    public String putNoTtl(
        Fixture fixture)
    {
        fixture.store.put(fixture.key(fixture.next()), fixture.valueA, null, fixture.onValue);

        return fixture.await();
    }

    @Benchmark
    public String putTtl(
        Fixture fixture)
    {
        fixture.store.put(fixture.key(fixture.next()), fixture.valueA, TTL, fixture.onValue);

        return fixture.await();
    }

    @Benchmark
    public boolean replace(
        Fixture fixture)
    {
        final int index = fixture.next();
        final boolean even = (fixture.pass & 1) == 0;
        final String expected = even ? fixture.expectedA : fixture.expectedB;
        final String value = even ? fixture.valueB : fixture.valueA;

        fixture.store.replace(fixture.key(index), expected, value, null, fixture.onReplace);
        fixture.await();

        return fixture.replaced;
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public String delete(
        Removal fixture)
    {
        for (int step = 0; step < BATCH; step++)
        {
            fixture.store.delete(fixture.key(fixture.next()), fixture.onValue);
            fixture.await();
        }

        return fixture.result;
    }

    @Benchmark
    @OperationsPerInvocation(BATCH)
    public String getAndDelete(
        Removal fixture)
    {
        for (int step = 0; step < BATCH; step++)
        {
            fixture.store.getAndDelete(fixture.key(fixture.next()), fixture.onValue);
            fixture.await();
        }

        return fixture.result;
    }

    @Benchmark
    public String lockUnlock(
        Fixture fixture)
    {
        final String key = fixture.key(fixture.next());

        fixture.store.lock(key, TTL, fixture.onLock);
        fixture.await();
        fixture.store.unlock(key, fixture.token, fixture.onValue);
        fixture.await();

        return fixture.result;
    }

    @Benchmark
    public String lockRenewUnlock(
        Fixture fixture)
    {
        final String key = fixture.key(fixture.next());

        fixture.store.lock(key, TTL, fixture.onLock);
        fixture.await();
        fixture.store.renew(key, fixture.token, TTL, fixture.onValue);
        fixture.await();
        fixture.store.unlock(key, fixture.token, fixture.onValue);
        fixture.await();

        return fixture.result;
    }

    @Benchmark
    public boolean qos2Exchange(
        Fixture fixture)
    {
        final String key = fixture.key(fixture.population + fixture.next());

        fixture.store.put(key, fixture.valueA, null, fixture.onValue);
        fixture.await();
        fixture.store.replace(key, fixture.expectedA, fixture.valueB, null, fixture.onReplace);
        fixture.await();
        fixture.store.delete(key, fixture.onValue);
        fixture.await();

        return fixture.replaced;
    }

    @State(Scope.Benchmark)
    public static class Fixture
    {
        @Param({"100", "500", "2048"})
        public int valueSize;

        @Param({"100000", "1000000"})
        public int population;

        final BiConsumer<String, String> onGet = this::completeGet;
        final Consumer<String> onValue = this::completeValue;
        final Consumer<Boolean> onReplace = this::completeReplace;
        final BiConsumer<String, String> onLock = this::completeLock;

        StoreHandler store;
        String valueA;
        String valueB;
        String expectedA;
        String expectedB;
        String result;
        String token;
        boolean replaced;
        int pass;

        private final Deque<Runnable> tasks = new ConcurrentLinkedDeque<>();
        private final Runnable completion = this::completeTask;

        int index;

        @Setup(Level.Trial)
        public void init()
        {
            final EngineContext context = (EngineContext) Proxy.newProxyInstance(
                EngineContext.class.getClassLoader(),
                new Class<?>[] {EngineContext.class},
                (proxy, method, args) -> invoke(method, args));

            final Store memory = new MemoryStoreFactorySpi().create(new Configuration());
            final StoreConfig config = new StoreConfig("bench", "store0", "memory", null)
            {
            };
            store = memory.supply(context).attach(config);

            valueA = text(valueSize, 1L);
            valueB = text(valueSize, 2L);
            expectedA = new String(valueA);
            expectedB = new String(valueB);

            for (int client = 0; client < population; client++)
            {
                store.put(key(client), valueA, null, onValue);
                drain();
            }
        }

        String key(
            int client)
        {
            return "client-" + client + ":state";
        }

        int next()
        {
            index += STRIDE;
            if (index >= population)
            {
                index -= population;
                pass++;
            }

            return index;
        }

        void dispatch()
        {
            enqueue(completion);
            await();
        }

        String await()
        {
            final int ran = drain();
            if (ran == 0)
            {
                throw new IllegalStateException("completion not delivered");
            }

            return result;
        }

        int drain()
        {
            int ran = 0;
            Runnable task;
            while ((task = tasks.poll()) != null)
            {
                task.run();
                ran++;
            }

            return ran;
        }

        private void enqueue(
            Runnable task)
        {
            tasks.offer(task);
        }

        private Object invoke(
            Method method,
            Object[] args)
        {
            final Object returned;
            switch (method.getName())
            {
            case "dispatch":
                enqueue((Runnable) args[0]);
                returned = null;
                break;
            case "clock":
                returned = Clock.systemUTC();
                break;
            case "signaler":
                returned = Proxy.newProxyInstance(
                    Signaler.class.getClassLoader(),
                    new Class<?>[] {Signaler.class},
                    (proxy, signal, signalArgs) -> Signaler.NO_CANCEL_ID);
                break;
            default:
                throw new UnsupportedOperationException(method.getName());
            }

            return returned;
        }

        private void completeTask()
        {
            result = null;
        }

        private void completeGet(
            String key,
            String value)
        {
            result = value;
        }

        private void completeValue(
            String value)
        {
            result = value;
        }

        private void completeReplace(
            Boolean value)
        {
            replaced = value;
        }

        private void completeLock(
            String key,
            String value)
        {
            token = value;
            result = value;
        }

        private static String text(
            int length,
            long seed)
        {
            final byte[] random = new byte[length];
            new Random(seed).nextBytes(random);
            return Base64.getEncoder().withoutPadding().encodeToString(random).substring(0, length);
        }
    }

    @State(Scope.Benchmark)
    public static class Removal extends Fixture
    {
        @Setup(Level.Invocation)
        public void restore()
        {
            final int resume = index;
            final int resumePass = pass;

            for (int step = 0; step < BATCH; step++)
            {
                store.put(key(next()), valueA, null, onValue);
                drain();
            }

            index = resume;
            pass = resumePass;
        }
    }

    public static void main(
        String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
            .include(MemoryStoreBM.class.getSimpleName())
            .addProfiler("gc")
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
