/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.otlp.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_CLOCK_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class EventIT
{
    private static final String ENGINE_DIRECTORY = "target/zilla-itests";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/exporter/otlp/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory(ENGINE_DIRECTORY)
        .configure(ENGINE_WORKERS, 1)
        .configure(ENGINE_CLOCK_NAME,
            "io.aklivity.zilla.runtime.exporter.otlp.internal.EventIT::supplyClock")
        .configurationRoot("io/aklivity/zilla/specs/exporter/otlp/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("event.yaml")
    @Specification({
        "${net}/handshake/client",
        "${net}/handshake/server",
        "${app}/event/server"
    })
    @ScriptProperty("serverAddress \"zilla://streams/app0\"")
    public void shouldPostEventLogToOtlpCollector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("secure/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${net}/handshake/server",
        "${app}/event.with.authorization/server"
    })
    @ScriptProperty("serverAddress \"zilla://streams/app0\"")
    public void shouldPostEventLogWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("secure/mtls/zilla.yaml")
    @Specification({
        "${net}/handshake/client",
        "${net}/handshake/server",
        "${app}/event.with.mtls.authorization/server"
    })
    @ScriptProperty("serverAddress \"zilla://streams/app0\"")
    public void shouldPostEventLogWithMtls() throws Exception
    {
        k3po.finish();
    }

    public static Clock supplyClock()
    {
        AtomicInteger iteration = new AtomicInteger(0);

        return new Clock()
        {
            @Override
            public ZoneId getZone()
            {
                return null;
            }

            @Override
            public Clock withZone(
                ZoneId zone)
            {
                return null;
            }

            @Override
            public Instant instant()
            {
                return null;
            }

            @Override
            public long millis()
            {
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                return 2547527985000L + iteration.getAndIncrement() * 1000L;
            }
        };
    }
}
