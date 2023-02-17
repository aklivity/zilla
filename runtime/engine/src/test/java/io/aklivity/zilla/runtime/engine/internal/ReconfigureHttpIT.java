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
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;


public class ReconfigureHttpIT
{
    public static final String ENGINE_CONFIG_POLL_INTERVAL_SECONDS = "zilla.engine.config.poll.interval.seconds";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/runtime/engine/internal")
        .external("app0")
        .external("app1")
        .external("app2")
        .clean();

    @Rule
    public final TestRule chain = outerRule(k3po).around(engine).around(timeout);

    @Before
    public void setupRegisterLatch() throws Exception
    {
        //Make sure that the initial configuration has finished
        EngineTest.TestEngineExt.registerLatch.await();
        //Register new CountDownLatch
        EngineTest.TestEngineExt.registerLatch = new CountDownLatch(1);
    }

    @Test
    @Configure(name = ENGINE_CONFIG_POLL_INTERVAL_SECONDS, value = "1")
    @Configuration("http://localhost:8080/")
    @Specification({
        "${app}/reconfigure.modify.via.http/server",
        "${net}/reconfigure.modify.via.http/client"
    })
    public void shouldReconfigureWhenModifiedHTTP() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTED");
        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CHANGED");
        k3po.finish();
    }

    @Test
    @Configure(name = ENGINE_CONFIG_POLL_INTERVAL_SECONDS, value = "1")
    @Configuration("http://localhost:8080/")
    @Specification({
        "${app}/reconfigure.create.via.http/server",
        "${net}/reconfigure.create.via.http/client"
    })
    public void shouldReconfigureWhenCreatedHTTP() throws Exception
    {
        k3po.start();
        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CREATED");
        k3po.finish();
    }

    @Test
    @Configuration("http://localhost:8080/")
    @Specification({
        "${app}/reconfigure.delete.via.http/server",
        "${net}/reconfigure.delete.via.http/client"
    })
    public void shouldReconfigureWhenDeletedHTTP() throws Exception
    {
        k3po.start();
        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_DELETED");
        k3po.finish();
    }

    @Test
    @Configure(name = ENGINE_CONFIG_POLL_INTERVAL_SECONDS, value = "1")
    @Configuration("http://localhost:8080/")
    @Specification({
        "${app}/reconfigure.modify.no.etag.via.http/server",
        "${net}/reconfigure.modify.no.etag.via.http/client"
    })
    public void shouldReconfigureWhenModifiedHTTPEtagNotSupported() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CONNECTED");
        EngineTest.TestEngineExt.registerLatch.await();
        k3po.notifyBarrier("CONFIG_CHANGED");
        k3po.finish();
    }

    @Test
    @Configure(name = ENGINE_CONFIG_POLL_INTERVAL_SECONDS, value = "1")
    @Configuration("http://localhost:8080/")
    @Specification({
        "${app}/reconfigure.server.error.via.http/server",
        "${net}/reconfigure.server.error.via.http/client"
    })
    public void shouldNotReconfigureWhen500Returned() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("CHECK_RECONFIGURE");
        Assert.assertEquals("Reconfigure should not happen at server error", 1,
            EngineTest.TestEngineExt.registerLatch.getCount());
        k3po.finish();
    }
}
