/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mqtt.streams.application;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);


    @Test
    @Specification({
        "${app}/session.connect.with.session.expiry/client",
        "${app}/session.connect.with.session.expiry/server"})
    public void shouldConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.abrupt.disconnect.session.expires/client",
        "${app}/session.will.message.abrupt.disconnect.session.expires/server"})
    public void shouldPublishWillMessageAfterAbruptClientDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.abrupt.disconnect.session.expires/client",
        "${app}/session.abrupt.disconnect.session.expires/server"})
    public void shouldExpireSessionAfterAbruptDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.exists.clean.start/client",
        "${app}/session.exists.clean.start/server"})
    public void shouldRemoveSessionAtCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.will.message.normal.disconnect/client",
        "${app}/session.will.message.normal.disconnect/server"})
    public void shouldNotPublishWillMessageAfterNormalClientDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe/client",
        "${app}/session.subscribe/server"})
    public void shouldSubscribeSaveSubscriptionsInSession() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-15]
    @Test
    @Specification({
        "${app}/session.will.message.retain/client",
        "${app}/session.will.message.retain/server"})
    public void shouldConnectWithWillMessageWithRetain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.close.connection.reconnect.non.clean.start/client",
        "${app}/session.close.connection.reconnect.non.clean.start/server"})
    public void shouldReconnectNonCleanStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.client.takeover/client",
        "${app}/session.client.takeover/server"})
    public void shouldTakeOverSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.unsubscribe.after.subscribe/client",
        "${app}/session.unsubscribe.after.subscribe/server"})
    public void shouldUnsubscribeAndUpdateSessionState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.to.will.topic/client",
        "${app}/session.subscribe.to.will.topic/server"})
    public void shouldSubscribeToWillTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/session.subscribe.via.session.state/client",
        "${app}/session.subscribe.via.session.state/server"})
    public void shouldReceiveMessageSubscribedViaSessionState() throws Exception
    {
        k3po.finish();
    }


    //TODO: use session.subscribe at actual test
    @Test
    @Specification({
        "${app}/session.unsubscribe.via.session.state/client",
        "${app}/session.unsubscribe.via.session.state/server"})
    public void shouldUnsubscribeViaSessionState() throws Exception
    {
        k3po.finish();
    }
}
