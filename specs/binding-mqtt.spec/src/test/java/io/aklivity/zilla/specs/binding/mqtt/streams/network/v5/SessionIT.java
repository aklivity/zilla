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
package io.aklivity.zilla.specs.binding.mqtt.streams.network.v5;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class SessionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/5.0");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);


    @Test
    @Specification({
        "${net}/session.connect.with.session.expiry/client",
        "${net}/session.connect.with.session.expiry/server"})
    public void shouldConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.connect.override.session.expiry/client",
        "${net}/session.connect.override.session.expiry/server"})
    public void shouldConnectServerOverridesSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-5], [MQTT-3.1.2-23]
    @Test
    @Specification({
        "${net}/session.abort.reconnect.non.clean.start/client",
        "${net}/session.abort.reconnect.non.clean.start/server"})
    public void shouldReconnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.client.takeover/client",
        "${net}/session.client.takeover/server"})
    public void shouldTakeOverSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.subscribe/client",
        "${net}/session.subscribe/server"})
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.will.message.disconnect.with.will.message/client",
        "${net}/session.will.message.disconnect.with.will.message/server"})
    public void shouldConnectWithWillMessageThenDisconnectWithWillMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.exists.clean.start/client",
        "${net}/session.exists.clean.start/server"})
    public void shouldRemoveSessionAtCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.will.message.no.ping.within.keep.alive/client",
        "${net}/session.will.message.no.ping.within.keep.alive/server"})
    public void shouldConnectWithWillMessageThenNoPingWithinKeepAlive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.will.message.normal.disconnect/client",
        "${net}/session.will.message.normal.disconnect/server"})
    public void shouldConnectWithWillMessageThenNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-15]
    @Test
    @Specification({
        "${net}/session.will.message.retain/client",
        "${net}/session.will.message.retain/server"})
    public void shouldConnectWithWillMessageWithRetain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.unsubscribe.after.subscribe/client",
        "${net}/session.unsubscribe.after.subscribe/server"})
    public void shouldUnsubscribeAndUpdateSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.subscribe.via.session.state/client",
        "${net}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.unsubscribe.after.subscribe.deferred/client",
        "${net}/session.unsubscribe.after.subscribe.deferred/server"})
    public void shouldUnsubscribeAfterSubscribeDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.server.redirect.before.connack/client",
        "${net}/session.server.redirect.before.connack/server"})
    public void shouldRedirectBeforeConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.server.redirect.after.connack/client",
        "${net}/session.server.redirect.after.connack/server"})
    public void shouldRedirectAfterConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.subscribe.multiple.isolated/client",
        "${net}/session.subscribe.multiple.isolated/server"})
    public void shouldSubscribeMultipleSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/session.subscribe.publish.routing/client",
        "${net}/session.subscribe.publish.routing/server"})
    public void shouldSubscribeAndPublishToNonDefaultRoute() throws Exception
    {
        k3po.finish();
    }
}
