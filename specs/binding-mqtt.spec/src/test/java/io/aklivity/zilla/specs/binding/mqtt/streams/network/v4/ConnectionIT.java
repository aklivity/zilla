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
package io.aklivity.zilla.specs.binding.mqtt.streams.network.v4;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ConnectionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/v4");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/connect.successful/client",
        "${net}/connect.successful/server"})
    public void shouldConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.username.authentication.successful/client",
        "${net}/connect.username.authentication.successful/server"})
    public void shouldAuthenticateUsernameAndConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.username.authentication.failed/client",
        "${net}/connect.username.authentication.failed/server"})
    public void shouldFailUsernameAuthentication() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.password.authentication.successful/client",
        "${net}/connect.password.authentication.successful/server"})
    public void shouldAuthenticatePasswordAndConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.password.authentication.failed/client",
        "${net}/connect.password.authentication.failed/server"})
    public void shouldFailPasswordAuthentication() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.password.flag.no.username.flag/client",
        "${net}/connect.reject.password.flag.no.username.flag/server"})
    public void shouldRejectPasswordFlagNoUsernameFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.server.assigned.client.id/client",
        "${net}/connect.server.assigned.client.id/server"})
    public void shouldConnectWithServerAssignedClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.missing.client.id/client",
        "${net}/connect.reject.missing.client.id/server"})
    public void shouldRejectMissingClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.no.client.id.no.clean.session/client",
        "${net}/connect.reject.no.client.id.no.clean.session/server"})
    public void shouldRejectNoClientIdNoCleanSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect/client",
        "${net}/disconnect/server"})
    public void shouldConnectThenDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.invalid.flags/client",
        "${net}/connect.invalid.flags/server"})
    public void shouldRejectMalformedConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.second.connect/client",
        "${net}/connect.reject.second.connect/server"})
    public void shouldRejectSecondConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.successful.fragmented/client",
        "${net}/connect.successful.fragmented/server"})
    public void shouldConnectFragmented() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.0-1]
    @Test
    @Specification({
        "${net}/connect.reject.other.packet.before.connect/client",
        "${net}/connect.reject.other.packet.before.connect/server"})
    public void shouldRejectOtherPacketBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.close/client",
        "${net}/client.sent.close/server"})
    public void shouldReceiveClientSentClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.abort/client",
        "${net}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.reset/client",
        "${net}/client.sent.reset/server"})
    public void shouldReceiveClientSentReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.invalid.will.qos/client",
        "${net}/connect.will.invalid.will.qos/server"})
    public void shouldRejectInvalidWillQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.will.qos.1.without.will.flag/client",
        "${net}/connect.will.reject.will.qos.1.without.will.flag/server"})
    public void shouldRejectWillQos1WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.will.qos.2.without.will.flag/client",
        "${net}/connect.will.reject.will.qos.2.without.will.flag/server"})
    public void shouldRejectWillQos2WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.will.reject.will.retain.without.will.flag/client",
        "${net}/connect.will.reject.will.retain.without.will.flag/server"})
    public void shouldRejectWillRetainWithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-9]
    @Test
    @Specification({
        "${net}/connect.reject.will.payload.missing/client",
        "${net}/connect.reject.will.payload.missing/server"})
    public void shouldRejectConnectWillPayloadMissing() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-9]
    @Test
    @Specification({
        "${net}/connect.reject.will.topic.missing/client",
        "${net}/connect.reject.will.topic.missing/server"})
    public void shouldRejectConnectWillTopicNotMissing() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-16]
    @Test
    @Specification({
        "${net}/connect.reject.username.flag.missing/client",
        "${net}/connect.reject.username.flag.missing/server"})
    public void shouldRejectUsernameUserFlagMissing() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-19]
    @Test
    @Specification({
        "${net}/connect.reject.password.flag.no.password/client",
        "${net}/connect.reject.password.flag.no.password/server"})
    public void shouldRejectPasswordFlagWhenMissingPassword() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.subscribe.batched/client",
        "${net}/connect.subscribe.batched/server"})
    public void shouldConnectAndSubscribeFalseStart() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.after.subscribe.and.publish/client",
        "${net}/disconnect.after.subscribe.and.publish/server"})
    public void shouldDisconnectAfterSubscribeAndPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.non.successful.connack/client",
        "${net}/connect.non.successful.connack/server"})
    public void shouldResetWithReasonCodeOnNonSuccessfulConnack() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/close.network.after.keep.alive.timeout/client",
        "${net}/close.network.after.keep.alive.timeout/server"})
    public void shouldCloseNetworkKeepAliveTimeouts() throws Exception
    {
        k3po.finish();
    }
}
