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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream.server;

import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfiguration.PUBLISH_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.mqtt.internal.MqttConfigurationTest.KEEP_ALIVE_MINIMUM_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class SessionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(PUBLISH_TIMEOUT, 1L)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/mqtt/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.connect.with.session.expiry/client",
        "${app}/session.connect.with.session.expiry/server"})
    public void shouldConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.connect.override.session.expiry/client",
        "${app}/session.connect.override.session.expiry/server"})
    public void shouldConnectServerOverridesSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.subscribe/client",
        "${app}/session.subscribe/server"})
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.subscribe.multiple.isolated/client",
        "${app}/session.subscribe.multiple.isolated/server"})
    public void shouldSubscribeMultipleSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.subscribe.via.session.state/client",
        "${app}/session.subscribe.via.session.state/server"})
    public void shouldSubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.unsubscribe.after.subscribe/client",
        "${app}/session.unsubscribe.after.subscribe/server"})
    public void shouldUnsubscribeSaveNewSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.unsubscribe.after.subscribe.deferred/client",
        "${app}/session.unsubscribe.after.subscribe.deferred/server"})
    public void shouldUnsubscribeAfterSubscribeDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.subscribe/client",
        "${app}/session.unsubscribe.via.session.state/server"})
    public void shouldUnsubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.retain/client",
        "${app}/session.will.message.retain/server"})
    public void shouldStoreWillMessageInSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.connect.payload.fragmented/client",
        "${app}/session.will.message.retain/server"})
    public void shouldStoreWillMessageInSessionStatePayloadFragmented() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.normal.disconnect/client",
        "${app}/session.will.message.normal.disconnect/server"})
    public void shouldCloseSessionNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.disconnect.with.will.message/client",
        "${app}/session.will.message.abort/server"})
    public void shouldCloseSessionDisconnectWithWill() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.will.message.no.ping.within.keep.alive/client",
        "${app}/session.will.message.abort/server"})
    @Configure(name = KEEP_ALIVE_MINIMUM_NAME, value = "1")
    public void shouldCloseSessionWithKeepAliveExpired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.exists.clean.start/client",
        "${app}/session.exists.clean.start/server"})
    public void shouldCloseExistingConnectionCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.abort.reconnect.non.clean.start/client",
        "${app}/session.abort.reconnect.non.clean.start/server"})
    public void shouldClientAbortAndReconnectWithNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.client.takeover/client",
        "${app}/session.client.takeover/server"})
    public void shouldClientTakeOverSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.server.redirect.after.connack/client",
        "${app}/session.server.redirect.after.connack/server"})
    public void shouldRedirectAfterConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/session.server.redirect.before.connack/client",
        "${app}/session.server.redirect.before.connack/server"})
    public void shouldRedirectBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.route.non.default.yaml")
    @Specification({
        "${net}/session.subscribe.publish.routing/client",
        "${app}/session.subscribe.publish.routing/server"})
    public void shouldSubscribeAndPublishToNonDefaultRoute() throws Exception
    {
        k3po.finish();
    }
}
