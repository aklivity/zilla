/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class ClientRoutingIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/tcp/streams/network/routing")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tcp/streams/application/routing");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/tcp/config")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.host.yaml")
    @Specification({
        "${app}/client.connect.with.host.extension/client",
        "${net}/client.connect.with.host.extension/server"
    })
    public void clientConnectHostExtWhenRoutedViaHost() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.subnet.yaml")
    @Specification({
        "${app}/client.connect.with.ipv4.extension/client",
        "${net}/client.connect.with.ipv4.extension/server"
    })
    public void shouldConnectIpv4ExtWhenRoutedViaSubnet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.authority.yaml")
    @Specification({
        "${app}/client.connect.with.ipv4.extension/client",
        "${net}/client.connect.with.ipv4.extension/server"
    })
    public void shouldConnectIpv4ExtWhenRoutedViaAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.host.and.subnet.yaml")
    @Specification({
        "${app}/client.connect.with.ipv4.extension/client",
        "${net}/client.connect.with.ipv4.extension/server"
    })
    public void shouldConnectIpv4ExtWhenRoutedViaSubnetMultipleRoutes() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.subnet.ipv6.yaml")
    @Specification({
        "${app}/client.connect.with.ipv6.extension/client",
        "${net}/client.connect.with.ipv6.extension/server"
    })
    public void shouldConnectIpv6ExtWhenRoutedViaSubnet() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.host.and.subnet.ipv6.yaml")
    @Specification({
        "${app}/client.connect.with.ipv6.extension/client",
        "${net}/client.connect.with.ipv6.extension/server"
    })
    public void shouldConnectIpv6ExtWhenRoutedViaSubnetMultipleRoutes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.subnet.yaml")
    @Specification({
        "${app}/client.connect.with.host.extension/client",
        "${net}/client.connect.with.host.extension/server"
    })
    public void shouldConnectHostExtWhenRoutedViaSubnet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.ports.yaml")
    @Specification({
        "${app}/client.connect.with.port.extension/client",
        "${net}/client.connect.with.port.extension/server"
    })
    public void shouldConnectHostWhenRoutedViaPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.subnet.yaml")
    @Specification({
        "${app}/client.reset.with.no.subnet.match/client"
    })
    public void shouldResetClientWithNoSubnetMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.ports.yaml")
    @Specification({
        "${app}/client.rejected.port.not.routed/client"
    })
    public void shouldRejectClientWhenPortNotRouted() throws Exception
    {
        k3po.finish();
    }
}
