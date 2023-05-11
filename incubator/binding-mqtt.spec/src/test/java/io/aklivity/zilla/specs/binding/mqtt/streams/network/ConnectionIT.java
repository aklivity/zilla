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
        "${net}/connect/successful/client",
        "${net}/connect/successful/server"})
    public void shouldConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/server.assigned.client.id/client",
        "${net}/connect/server.assigned.client.id/server"})
    public void shouldConnectWithServerAssignedClientId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.missing.client.id/client",
        "${net}/connect/reject.missing.client.id/server"})
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
        "${net}/connect/invalid.protocol.version/client",
        "${net}/connect/invalid.protocol.version/server"})
    public void shouldRejectInvalidProtocolVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/invalid.flags/client",
        "${net}/connect/invalid.flags/server"})
    public void shouldRejectMalformedConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/disconnect/invalid.fixed.header.flags/client",
        "${net}/disconnect/invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedDisconnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.second.connect/client",
        "${net}/connect/reject.second.connect/server"})
    public void shouldRejectSecondConnectPacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.connect.when.topic.alias.maximum.repeated/client",
        "${net}/reject.connect.when.topic.alias.maximum.repeated/server"})
    public void shouldRejectConnectWhenTopicAliasMaximumRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/successful.fragmented/client",
        "${net}/connect/successful.fragmented/server"})
    public void shouldProcessFragmentedConnectPacket() throws Exception
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

    @Test
    @Specification({
        "${net}/keep.alive.with.pingreq/client",
        "${net}/keep.alive.with.pingreq/server"})
    public void shouldKeepAliveWithPingreq() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/timeout.before.connect/client",
        "${net}/timeout.before.connect/server"})
    public void shouldTimeoutBeforeConnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/maximum.qos.0/client",
        "${net}/connect/maximum.qos.0/server"})
    public void shouldConnectWithMaximumQos0() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/retain.unavailable/client",
        "${net}/connect/retain.unavailable/server"})
    public void shouldConnectWithRetainUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/wildcard.subscriptions.unavailable/client",
        "${net}/connect/wildcard.subscriptions.unavailable/server"})
    public void shouldConnectWithWildcardSubscriptionsUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/subscription.identifiers.unavailable/client",
        "${net}/connect/subscription.identifiers.unavailable/server"})
    public void shouldConnectWithSubscriptionIdentifiersUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/shared.subscriptions.unavailable/client",
        "${net}/connect/shared.subscriptions.unavailable/server"})
    public void shouldConnectWithSharedSubscriptionsUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.username/client",
        "${net}/connect/reject.username/server"})
    public void shouldRejectConnectWithUsername() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.password/client",
        "${net}/connect/reject.password/server"})
    public void shouldRejectConnectWithPassword() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.will.flag/client",
        "${net}/connect/reject.will.flag/server"})
    public void shouldRejectWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.invalid.will.qos/client",
        "${net}/connect/reject.invalid.will.qos/server"})
    public void shouldRejectInvalidWillQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.will.qos.1.without.will.flag/client",
        "${net}/connect/reject.will.qos.1.without.will.flag/server"})
    public void shouldRejectWillQos1WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.will.qos.2.without.will.flag/client",
        "${net}/connect/reject.will.qos.2.without.will.flag/server"})
    public void shouldRejectWillQos2WithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/reject.will.retain.without.will.flag/client",
        "${net}/connect/reject.will.retain.without.will.flag/server"})
    public void shouldRejectWillRetainWithoutWillFlag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/will.message.with.abrupt.disconnect/client",
        "${net}/connect/will.message.with.abrupt.disconnect/server"})
    public void shouldConnectWithWillMessageThenAbruptDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect/will.message.with.normal.disconnect/client",
        "${net}/connect/will.message.with.normal.disconnect/server"})
    public void shouldConnectWithWillMessageThenNormalDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.with.will.message.then.publish.one.message/client",
        "${net}/connect.with.will.message.then.publish.one.message/server"})
    public void shouldConnectWithWillMessageThenPublishOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/connect.with.session.expiry/client",
        "${net}/connect.with.session.expiry/server"})
    public void shouldConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }
}
