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

public class BridgeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("bridge", "io/aklivity/zilla/specs/binding/tls/streams/bridge");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
            .directory("target/zilla-itests")
            .countersBufferCapacity(8192)
            .configurationRoot("io/aklivity/zilla/specs/binding/tls/config")
            .external("app1")
            .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("bridge.tls1.2.yaml")
    @Specification({
        "${bridge}/handshake/client",
        "${bridge}/handshake/server"})
    public void shouldHandshakeWithTls12() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("bridge.tls1.3.yaml")
    @Specification({
        "${bridge}/handshake/client",
        "${bridge}/handshake/server"})
    public void shouldHandshakeWithTls13() throws Exception
    {
        k3po.finish();
    }
}
