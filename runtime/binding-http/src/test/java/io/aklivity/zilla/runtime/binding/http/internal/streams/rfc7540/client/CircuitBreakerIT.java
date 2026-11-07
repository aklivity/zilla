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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.client;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_STREAM_INITIAL_WINDOW_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

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

public class CircuitBreakerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/circuit.breaker")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/circuit.breaker");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(HTTP_CONCURRENT_STREAMS, 100)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    @Specification({
        "${app}/open.circuit.after.stream.reset/client",
        "${net}/open.circuit.after.stream.reset/server" })
    public void shouldOpenCircuitAfterStreamReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "1")
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    @Specification({
        "${app}/open.circuit.after.goaway/client",
        "${net}/open.circuit.after.goaway/server" })
    public void shouldOpenCircuitAfterGoaway() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    @Specification({
        "${app}/open.circuit.on.retry.after.status/client",
        "${net}/open.circuit.on.retry.after.status/server" })
    public void shouldOpenCircuitOnRetryAfterStatus() throws Exception
    {
        k3po.finish();
    }
}
