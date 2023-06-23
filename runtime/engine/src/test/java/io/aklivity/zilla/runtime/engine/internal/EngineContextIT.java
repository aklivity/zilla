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
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
    public void shouldFetchCounterValue() throws Exception
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
}
