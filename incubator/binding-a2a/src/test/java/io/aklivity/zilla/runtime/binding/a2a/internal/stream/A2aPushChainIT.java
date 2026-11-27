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

public class A2aPushChainIT
{
    private static final String HOST_RESOLVER =
        "io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures::resolveHost";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/a2a/streams/network");

    private final A2aPushProperties properties = new A2aPushProperties(k3po, true);

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("net1")
        .external("net2")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(properties).around(timeout);

    @Test
    @A2aPushToken(A2aPushToken.Variant.CONFIG)
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.config.create.slug/client",
        "${net}/chain.push.config.create/server"})
    public void shouldSealPushConfigCreate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.CONFIG)
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.config.create.slug.v0.3/client",
        "${net}/chain.push.config.create/server"})
    public void shouldSealPushConfigCreateFromV03Caller() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.CONFIG)
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.config.get.slug/client",
        "${net}/chain.push.config.get/server"})
    public void shouldUnsealPushConfigGet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.message.send.slug/client",
        "${net}/chain.push.message.send/server"})
    public void shouldSealInlinePushConfigOnSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.config.create.blocked.slug/client"})
    public void shouldRejectPushConfigCreateBlocked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.push.too.long.yaml")
    @Specification({
        "${net}/push.config.create.too.long.slug/client"})
    public void shouldRejectPushConfigCreateTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.VALID)
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.notify.sealed/client",
        "${net}/chain.push.deliver/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    public void shouldRelayPushNotify() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.VALID)
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.notify.sealed.100k/client",
        "${net}/chain.push.deliver.100k/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    public void shouldRelayPushNotify100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.TAMPERED)
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyTamperedToken() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.EXPIRED)
    @Configuration("chain.push.yaml")
    @Specification({
        "${net}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyExpiredToken() throws Exception
    {
        k3po.finish();
    }
}
