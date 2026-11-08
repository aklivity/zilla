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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_SYNTHETIC_ABORT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class PresidioClassifierCloseIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/classifier/presidio/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/classifier/presidio/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(20, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/classifier/presidio/config")
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configure(ENGINE_SYNTHETIC_ABORT, false)
        .external("app0")
        .clean();

    private final CountDownLatch engineClosed = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer analyzer;

    private final ExternalResource completeAfterClose = new ExternalResource()
    {
        @Override
        protected void before() throws IOException
        {
            analyzer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 3000), 0);
            analyzer.setExecutor(Executors.newCachedThreadPool());
            analyzer.createContext("/analyze", PresidioClassifierCloseIT.this::hold);
            analyzer.start();
        }

        @Override
        protected void after()
        {
            engineClosed.countDown();
            ForkJoinPool.commonPool().awaitQuiescence(5L, SECONDS);
            analyzer.stop(0);
        }
    };

    @Rule
    public final TestRule chain = outerRule(completeAfterClose).around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("reject.no.timeout.yaml")
    @Specification({
        "${net}/client.sent.text.accepted/client",
        "${app}/client.sent.text.pending/server"
    })
    public void shouldSurviveAnalyzeCompletingAfterEngineClose() throws Exception
    {
        k3po.start();

        while (requests.get() < 1)
        {
            Thread.onSpinWait();
        }

        final ForkJoinPool pool = ForkJoinPool.commonPool();
        for (int i = 0; i < pool.getParallelism() + 2; i++)
        {
            pool.execute(this::awaitEngineClosed);
        }
    }

    private void hold(
        HttpExchange exchange) throws IOException
    {
        exchange.getRequestBody().readAllBytes();
        requests.incrementAndGet();
        awaitEngineClosed();
        exchange.close();
    }

    private void awaitEngineClosed()
    {
        try
        {
            engineClosed.await(10L, SECONDS);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}
