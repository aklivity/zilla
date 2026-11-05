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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

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

public class LlmCircuitIT
{
    private static final String HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME =
        "zilla.binding.http.client.breaker.failure.threshold";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/llm/streams/application")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/llm/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/llm/config")
        .external("net1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.client.http.yaml")
    @Configure(name = HTTP_CLIENT_BREAKER_FAILURE_THRESHOLD_NAME, value = "2")
    @Specification({
        "${app}/openai.proxy.circuit.failures/client",
        "${http}/openai.proxy.circuit.failures/server"})
    public void shouldFallbackWhenCircuitOpensForModel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.client.http.yaml")
    @Specification({
        "${app}/openai.proxy.circuit.retry.after/client",
        "${http}/openai.proxy.circuit.retry.after/server"})
    public void shouldFallbackWhenRetryAfterOpensCircuitForModel() throws Exception
    {
        k3po.finish();
    }
}
