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
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.rules.RuleChain.outerRule;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class EngineContextIT
{
    private static final String ENGINE_DIRECTORY = "target/zilla-itests";

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory(ENGINE_DIRECTORY)
        .configure(ENGINE_WORKERS, 3)
        .configurationRoot("io/aklivity/zilla/specs/engine/config")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(timeout);

    @Test
    @Configuration("server.yaml")
    public void shouldFetchCounterValue()
    {
        // GIVEN
        LongConsumer writer0 = engine.counterWriter("test", "net0", "test.counter", 0);
        LongConsumer writer1 = engine.counterWriter("test", "net0", "test.counter", 1);
        LongConsumer writer2 = engine.counterWriter("test", "net0", "test.counter", 2);
        writer0.accept(42L);
        writer1.accept(21L);
        writer2.accept(14L);

        // WHEN
        LongSupplier counter = engine.context().counter("test", "net0", "test.counter");

        // THEN
        // the aggregated counter value across the 3 cores should be 42 + 21 + 14 = 77
        assertThat(counter.getAsLong(), equalTo(77L));
    }

    @Test
    @Configuration("server.yaml")
    public void shouldCountWorkers()
    {
        assertThat(engine.context().workers(), equalTo(3));
    }

    @Test
    @Configuration("server.yaml")
    public void shouldFetchGaugeValuePerWorker()
    {
        // GIVEN
        engine.gaugeWriter("test", "net0", "test.gauge", 0).accept(42L);
        engine.gaugeWriter("test", "net0", "test.gauge", 1).accept(21L);
        engine.gaugeWriter("test", "net0", "test.gauge", 2).accept(14L);

        // WHEN
        LongSupplier worker0 = engine.context().gauge("test", "net0", "test.gauge", 0);
        LongSupplier worker1 = engine.context().gauge("test", "net0", "test.gauge", 1);
        LongSupplier worker2 = engine.context().gauge("test", "net0", "test.gauge", 2);

        // THEN
        assertThat(worker0.getAsLong(), equalTo(42L));
        assertThat(worker1.getAsLong(), equalTo(21L));
        assertThat(worker2.getAsLong(), equalTo(14L));
        assertThat(engine.context().gauge("test", "net0", "test.gauge").getAsLong(), equalTo(77L));
    }

    @Test
    @Configuration("server.yaml")
    public void shouldReflectGaugeChangesPerWorker()
    {
        // GIVEN
        LongConsumer writer = engine.gaugeWriter("test", "net0", "test.gauge", 1);
        LongSupplier worker1 = engine.context().gauge("test", "net0", "test.gauge", 1);

        // WHEN
        writer.accept(5L);
        writer.accept(-2L);

        // THEN
        assertThat(worker1.getAsLong(), equalTo(3L));
        assertThat(engine.context().gauge("test", "net0", "test.gauge", 0).getAsLong(), equalTo(0L));
    }

    @Test
    @Configuration("server.yaml")
    public void shouldNotFetchGaugeValueForUnknownWorker()
    {
        assertThat(engine.context().gauge("test", "net0", "test.gauge", -1), nullValue());
        assertThat(engine.context().gauge("test", "net0", "test.gauge", 3), nullValue());
    }

    @Test
    @Configuration("server.yaml")
    public void shouldFetchCounterValuePerWorker()
    {
        // GIVEN
        engine.counterWriter("test", "net0", "test.counter", 0).accept(42L);
        engine.counterWriter("test", "net0", "test.counter", 1).accept(21L);
        engine.counterWriter("test", "net0", "test.counter", 2).accept(14L);

        // WHEN
        LongSupplier worker0 = engine.context().counter("test", "net0", "test.counter", 0);
        LongSupplier worker1 = engine.context().counter("test", "net0", "test.counter", 1);
        LongSupplier worker2 = engine.context().counter("test", "net0", "test.counter", 2);

        // THEN
        assertThat(worker0.getAsLong(), equalTo(42L));
        assertThat(worker1.getAsLong(), equalTo(21L));
        assertThat(worker2.getAsLong(), equalTo(14L));
        assertThat(engine.context().counter("test", "net0", "test.counter").getAsLong(), equalTo(77L));
    }

    @Test
    @Configuration("server.yaml")
    public void shouldNotFetchCounterValueForUnknownWorker()
    {
        assertThat(engine.context().counter("test", "net0", "test.counter", -1), nullValue());
        assertThat(engine.context().counter("test", "net0", "test.counter", 3), nullValue());
    }

    @Test
    @Configuration("server.yaml")
    public void shouldReadListenerCountersPerWorkerWithoutListeners()
    {
        for (int worker = 0; worker < engine.context().workers(); worker++)
        {
            assertThat(engine.context().counter(null, null, "engine.workers.listeners.bound", worker).getAsLong(), equalTo(0L));
            assertThat(engine.context().counter(null, null, "engine.workers.listeners.unbound", worker).getAsLong(), equalTo(0L));
        }
    }

    @Test
    @Configuration("server.yaml")
    public void shouldReadUsageRebalancedCounterPerWorkerWithoutRebalancing()
    {
        for (int worker = 0; worker < engine.context().workers(); worker++)
        {
            assertThat(engine.context().counter(null, null, "engine.workers.usage.rebalanced", worker).getAsLong(), equalTo(0L));
        }
    }
}
