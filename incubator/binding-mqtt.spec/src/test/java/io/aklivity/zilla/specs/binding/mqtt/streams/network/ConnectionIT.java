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
package io.aklivity.zilla.specs.binding.mqtt.streams.network;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network");

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
        "${net}/disconnect/client",
        "${net}/disconnect/server"})
    public void shouldConnectThenDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.invalid.protocol.version/client",
        "${net}/connect.invalid.protocol.version/server"})
    public void shouldRejectInvalidProtocolVersion() throws Exception
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
        "${net}/connect.invalid.authentication.method/client",
        "${net}/connect.invalid.authentication.method/server"})
    public void shouldRejectBadAuthenticationMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect.invalid.fixed.header.flags/client",
        "${net}/disconnect.invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedDisconnectPacket() throws Exception
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
        "${net}/connect.reject.topic.alias.maximum.repeated/client",
        "${net}/connect.reject.topic.alias.maximum.repeated/server"})
    public void shouldRejectConnectWhenTopicAliasMaximumRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.successful.fragmented/client",
        "${net}/connect.successful.fragmented/server"})
    public void shouldProcessFragmentedConnectPacket() throws Exception
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
        "${net}/disconnect.after.keep.alive.timeout/client",
        "${net}/disconnect.after.keep.alive.timeout/server"})
    public void shouldDisconnectClientAfterKeepAliveTimeout() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-21], [MQTT-3.2.2-21]
    // TODO: use subscribe.topic.filter.single.exact/server in the application side
    @Test
    @Specification({
        "${net}/connect.server.overrides.keep.alive/client",
        "${net}/connect.server.overrides.keep.alive/server"})
    public void shouldUseServerOverriddenKeepAlive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ping.keep.alive/client",
        "${net}/ping.keep.alive/server"})
    public void shouldKeepAliveWithPingreq() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.timeout.before.connect/client",
        "${net}/connect.timeout.before.connect/server"})
    public void shouldTimeoutBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.maximum.qos.0/client",
        "${net}/connect.maximum.qos.0/server"})
    public void shouldConnectWithMaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.retain.unavailable/client",
        "${net}/connect.retain.unavailable/server"})
    public void shouldConnectWithRetainUnavailable() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-13]
    @Test
    @Specification({
        "${net}/connect.reject.will.retain.retain.unavailable/client",
        "${net}/connect.reject.will.retain.retain.unavailable/server"})
    public void shouldRejectConnectWithWillRetainRetainUnavailable() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${net}/connect.reject.username.not.authorized/client",
        "${net}/connect.reject.username.not.authorized/server"})
    public void shouldRejectConnectWithUsername() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.reject.password.not.authorized/client",
        "${net}/connect.reject.password.not.authorized/server"})
    public void shouldRejectConnectWithPassword() throws Exception
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
        "${net}/connect.will.reject.will.payload.not.present/client",
        "${net}/connect.will.reject.will.payload.not.present/server"})
    public void shouldRejectWillPayloadNotPresent() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-9]
    @Test
    @Specification({
        "${net}/connect.will.reject.will.properties.not.present/client",
        "${net}/connect.will.reject.will.properties.not.present/server"})
    public void shouldRejectWillPropertiesNotPresent() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-9]
    @Test
    @Specification({
        "${net}/connect.will.reject.will.topic.not.present/client",
        "${net}/connect.will.reject.will.topic.not.present/server"})
    public void shouldRejectWillTopicNotPresent() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-16]
    @Test
    @Specification({
        "${net}/connect.reject.username.no.user.flag/client",
        "${net}/connect.reject.username.no.user.flag/server"})
    public void shouldRejectUsernameWhenMissingUserFlag() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-18]
    @Test
    @Specification({
        "${net}/connect.reject.password.no.password.flag/client",
        "${net}/connect.reject.password.no.password.flag/server"})
    public void shouldRejectPasswordWhenMissingPasswordFlag() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.1.2-17]
    @Test
    @Specification({
        "${net}/connect.reject.username.flag.no.username/client",
        "${net}/connect.reject.username.flag.no.username/server"})
    public void shouldRejectUserFlagWhenMissingUsername() throws Exception
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

    // [MQTT-3.1.2-24]
    @Test
    @Specification({
        "${net}/connect.max.packet.size.server.ignores.exceeding.publish.packet/client",
        "${net}/connect.max.packet.size.server.ignores.exceeding.publish.packet/server"})
    public void shouldNotReceivePublishPacketExceedingMaxPacketLimit() throws Exception
    {
        k3po.finish();
    }
}
