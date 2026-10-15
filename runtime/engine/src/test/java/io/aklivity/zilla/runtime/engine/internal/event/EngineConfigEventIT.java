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
package io.aklivity.zilla.runtime.engine.internal.event;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.filesystem.http.HttpFilesystemEnvironment.POLL_INTERVAL_PROPERTY_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.concurrent.CountDownLatch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;
import io.aklivity.zilla.runtime.engine.test.internal.exporter.TestExporter;

public class EngineConfigEventIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/runtime/engine/internal")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(k3po).around(engine).around(timeout);

    @Test
    @Configure(name = POLL_INTERVAL_PROPERTY_NAME, value = "PT0S")
    @Configuration("http://localhost:8080/zilla.yaml")
    @Specification({
        "${app}/reconfigure.modify.via.http.applied/server"
    })
    public void shouldReportConfigAppliedWithEtag() throws Exception
    {
        TestExporter.eventsLatch = new CountDownLatch(1);
        k3po.start();
        TestExporter.eventsLatch.await();
        k3po.finish();
    }

    @Test
    @Configure(name = POLL_INTERVAL_PROPERTY_NAME, value = "PT0S")
    @Configuration("http://localhost:8080/zilla.yaml")
    @Specification({
        "${app}/reconfigure.modify.via.http.rejected/server"
    })
    public void shouldReportConfigRejectedWithEtag() throws Exception
    {
        TestExporter.eventsLatch = new CountDownLatch(1);
        k3po.start();
        TestExporter.eventsLatch.await();
        k3po.finish();
    }
}
