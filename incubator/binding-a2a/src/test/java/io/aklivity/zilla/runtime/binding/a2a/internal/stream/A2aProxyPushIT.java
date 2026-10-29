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

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class A2aProxyPushIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application");

    private final A2aPushProperties properties = new A2aPushProperties(k3po, false);

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("app1")
        .external("app2")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(properties).around(timeout);

    @Test
    @A2aPushToken(A2aPushToken.Variant.CONFIG)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.config.create.slug/client",
        "${app}/push.config.create.sealed/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSealPushConfigCreate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.CONFIG)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.config.get.slug/client",
        "${app}/push.config.get.sealed/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldUnsealPushConfigGet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.CONFIG)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.config.list.slug/client",
        "${app}/push.config.list.sealed/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldUnsealPushConfigList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.message.send/client",
        "${app}/push.message.send.sealed/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSealInlinePushConfigOnSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.config.create.blocked/client"})
    public void shouldRejectPushConfigCreateBlocked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.push.too.long.yaml")
    @Specification({
        "${app}/push.config.create.too.long/client"})
    public void shouldRejectPushConfigCreateTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.VALID)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed/client",
        "${app}/push.deliver/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app2\"")
    public void shouldRelayPushNotify() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.VALID)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.100k/client",
        "${app}/push.deliver.100k/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app2\"")
    public void shouldRelayPushNotify100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.TAMPERED)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyTamperedToken() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.GARBLED)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyGarbledToken() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.EXPIRED)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyExpiredToken() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.WRONG_NONCE)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyWrongNonce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.MISSING_NONCE)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.token.missing/client"})
    public void shouldRejectPushNotifyMissingNonce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.TASK_MISMATCH)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyTaskMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.RETIRED_KEY)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyRetiredKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @A2aPushToken(A2aPushToken.Variant.BLOCKED_TARGET)
    @Configuration("proxy.push.relay.yaml")
    @Specification({
        "${app}/push.notify.sealed.rejected/client"})
    public void shouldRejectPushNotifyBlockedTarget() throws Exception
    {
        k3po.finish();
    }
}
