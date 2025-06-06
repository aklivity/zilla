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
package io.aklivity.zilla.specs.binding.tls.stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("proxy", "io/aklivity/zilla/specs/binding/tls/streams/proxy");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${proxy}/client/client.hello.without.ext/client",
        "${proxy}/client/client.hello.without.ext/server"})
    public void shouldSendClientHelloWithoutExt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${proxy}/client/client.hello.with.sni/client",
        "${proxy}/client/client.hello.with.sni/server"})
    public void shouldSendClientHelloWithServerName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${proxy}/server/client.hello.without.ext/client",
        "${proxy}/server/client.hello.without.ext/server"})
    public void shouldReceiveClientHelloWithoutExt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${proxy}/server/client.hello.with.sni/client",
        "${proxy}/server/client.hello.with.sni/server"})
    public void shouldReceiveClientHelloWithServerName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${proxy}/client/reject.client.hello.with.sni/client",
        "${proxy}/client/reject.client.hello.with.sni/server"})
    public void shouldRejectClientHelloWithServerName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${proxy}/client/reject.port.not.routed/client",
        "${proxy}/client/reject.port.not.routed/server"})
    public void shouldRejectClientPortNotRouted() throws Exception
    {
        k3po.finish();
    }
}
