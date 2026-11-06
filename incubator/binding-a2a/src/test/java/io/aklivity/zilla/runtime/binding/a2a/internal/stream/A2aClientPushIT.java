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

package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

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

public class A2aClientPushIT
{
    private static final String HOST_RESOLVER =
        "io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures::resolveHost";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/a2a/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver.ordered/client",
        "${net}/push.deliver.ordered/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    @Configure(name = "zilla.binding.a2a.push.delivery.backoff", value = "PT0.05S")
    public void shouldDeliverInOrderPerConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver.100k/client",
        "${net}/push.deliver.100k/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    public void shouldDeliver100kBeyondRetainedMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.push.yaml")
    @Specification({
        "${app}/push.deliver.100k/client",
        "${net}/push.deliver.100k/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    public void shouldDeliver100kWithConfiguredMaximum() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver.blocked/client"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    public void shouldRejectDeliveryToBlockedTarget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver.blocked.resolved/client"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    public void shouldRejectDeliveryToTargetResolvingToPrivateAddress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver.slots/client",
        "${net}/push.deliver.slots/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    @Configure(name = "zilla.binding.a2a.push.delivery.backoff", value = "PT0.05S")
    @Configure(name = "zilla.engine.buffer.pool.capacity", value = "65536")
    // a delivery holds its retained body in one slot and its request encoding in another, so two slots serve
    // exactly one delivery at a time and any slot leaked on an exit path starves the next delivery
    public void shouldReleaseDeliverySlotOnEveryExit() throws Exception
    {
        k3po.finish();
    }
}
