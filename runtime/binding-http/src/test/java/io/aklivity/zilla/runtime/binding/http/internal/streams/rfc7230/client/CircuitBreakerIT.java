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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7230.client;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_CLIENT_BREAKER_COOLDOWN_NAME;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_CLIENT_BREAKER_RETRY_AFTER_MAXIMUM_NAME;
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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230/circuit.breaker")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/circuit.breaker");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "3")
    @Specification({
        "${app}/open.circuit.after.consecutive.failures/client",
        "${net}/open.circuit.after.consecutive.failures/server" })
    public void shouldOpenCircuitAfterConsecutiveFailures() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "3")
    @Specification({
        "${app}/reset.counter.on.other.response/client",
        "${net}/reset.counter.on.other.response/server" })
    public void shouldResetCounterOnOtherResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Configure(name = HTTP_CLIENT_BREAKER_COOLDOWN_NAME, value = "PT1S")
    @Specification({
        "${app}/close.circuit.when.probe.succeeds/client",
        "${net}/close.circuit.when.probe.succeeds/server" })
    public void shouldCloseCircuitWhenProbeSucceeds() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("REQUEST_3_COMPLETED");
        Thread.sleep(1100);
        k3po.notifyBarrier("COOLDOWN_ELAPSED");
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Configure(name = HTTP_CLIENT_BREAKER_COOLDOWN_NAME, value = "PT1S")
    @Specification({
        "${app}/reopen.circuit.when.probe.fails/client",
        "${net}/reopen.circuit.when.probe.fails/server" })
    public void shouldReopenCircuitWhenProbeFails() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("REQUEST_3_COMPLETED");
        Thread.sleep(1100);
        k3po.notifyBarrier("COOLDOWN_ELAPSED");
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/open.circuit.on.retry.after.seconds/client",
        "${net}/open.circuit.on.retry.after.seconds/server" })
    public void shouldOpenCircuitOnRetryAfterSeconds() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("REQUEST_2_COMPLETED");
        Thread.sleep(1100);
        k3po.notifyBarrier("RETRY_AFTER_ELAPSED");
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_RETRY_AFTER_MAXIMUM_NAME, value = "PT1S")
    @Specification({
        "${app}/open.circuit.on.retry.after.date/client",
        "${net}/open.circuit.on.retry.after.date/server" })
    public void shouldOpenCircuitOnRetryAfterDate() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("REQUEST_2_COMPLETED");
        Thread.sleep(1100);
        k3po.notifyBarrier("RETRY_AFTER_ELAPSED");
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/ignore.retry.after.on.other.status/client",
        "${net}/ignore.retry.after.on.other.status/server" })
    public void shouldIgnoreRetryAfterOnOtherStatus() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/ignore.retry.after.zero.on.failed.exchange/client",
        "${net}/ignore.retry.after.zero.on.failed.exchange/server" })
    public void shouldIgnoreRetryAfterZeroOnFailedExchange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Specification({
        "${app}/ignore.circuit.id.zero/client",
        "${net}/ignore.circuit.id.zero/server" })
    public void shouldIgnoreCircuitIdZero() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Specification({
        "${app}/isolate.circuits.by.circuit.id/client",
        "${net}/isolate.circuits.by.circuit.id/server" })
    public void shouldIsolateCircuitsByCircuitId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.circuits.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Specification({
        "${app}/isolate.circuits.by.binding/client",
        "${net}/isolate.circuits.by.binding/server" })
    public void shouldIsolateCircuitsByBinding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Specification({
        "${app}/isolate.circuits.by.origin/client",
        "${net}/isolate.circuits.by.origin/server" })
    public void shouldIsolateCircuitsByOrigin() throws Exception
    {
        k3po.finish();
    }
}
