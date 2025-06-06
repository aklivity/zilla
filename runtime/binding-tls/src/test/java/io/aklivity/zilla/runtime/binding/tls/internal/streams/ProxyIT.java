/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tls.internal.streams;

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

public class ProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("proxy", "io/aklivity/zilla/specs/binding/tls/streams/proxy");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
            .directory("target/zilla-itests")
            .countersBufferCapacity(8192)
            .configurationRoot("io/aklivity/zilla/specs/binding/tls/config")
            .external("net1")
            .configure(ENGINE_DRAIN_ON_CLOSE, false)
            .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${proxy}/client/client.hello.without.ext/client",
        "${proxy}/server/client.hello.without.ext/server" })
    public void shouldProxyClientHelloWithoutExt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.sni.yaml")
    @Specification({
        "${proxy}/client/client.hello.with.sni/client",
        "${proxy}/server/client.hello.with.sni/server" })
    public void shouldProxyClientHelloWithServerName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.sni.yaml")
    @Specification({
        "${proxy}/client/reject.client.hello.with.sni/client" })
    public void shouldRejectClientHelloWithServerName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.ports.yaml")
    @Specification({
        "${proxy}/client/reject.port.not.routed/client"
    })
    public void shouldRejectWhenPortNotRouted() throws Exception
    {
        k3po.finish();
    }
}
