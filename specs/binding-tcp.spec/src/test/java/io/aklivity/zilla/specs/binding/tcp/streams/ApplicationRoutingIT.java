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
package io.aklivity.zilla.specs.binding.tcp.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ApplicationRoutingIT
{

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tcp/streams/application/routing");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/client.connect.with.host.extension/client",
        "${app}/client.connect.with.host.extension/server" })
    public void shouldConnectClientWithHostExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.connect.with.ipv4.extension/client",
        "${app}/client.connect.with.ipv4.extension/server" })
    public void shouldConnectClientWithIpv4Extension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.connect.with.ipv6.extension/client",
        "${app}/client.connect.with.ipv6.extension/server" })
    public void shouldConnectClientWithIpv6Extension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.reset.with.no.subnet.match/client",
        "${app}/client.reset.with.no.subnet.match/server" })
    public void shouldResetClientWithNoSubnetMatch() throws Exception
    {
        k3po.finish();
    }
}
