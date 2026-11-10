/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mqtt.streams.upstream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class UpstreamIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("upstream", "io/aklivity/zilla/specs/binding/mqtt/streams/upstream");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${upstream}/connect.shared.subscriptions.not.supported/client",
        "${upstream}/connect.shared.subscriptions.not.supported/server"})
    public void shouldConnectSharedSubscriptionsNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos1/client",
        "${upstream}/subscribe.receive.message.qos1/server"})
    public void shouldSubscribeReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos1.ack.with.reasoncode/client",
        "${upstream}/subscribe.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos2/client",
        "${upstream}/subscribe.receive.message.qos2/server"})
    public void shouldSubscribeReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos2.ack.with.reasoncode/client",
        "${upstream}/subscribe.receive.message.qos2.ack.with.reasoncode/server"})
    public void shouldSubscribeReceiveMessageQos2AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos2.duplicate.before.ack/client",
        "${upstream}/subscribe.receive.message.qos2.duplicate.before.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateBeforeAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos2.duplicate.after.ack/client",
        "${upstream}/subscribe.receive.message.qos2.duplicate.after.ack/server"})
    public void shouldSubscribeReceiveMessageQos2DuplicateAfterAck() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/client",
        "${upstream}/subscribe.receive.message.qos2.reuse.packet.id.after.complete/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.receive.message.qos2.reuse.packet.id.after.error/client",
        "${upstream}/subscribe.receive.message.qos2.reuse.packet.id.after.error/server"})
    public void shouldSubscribeReceiveMessageQos2ReusePacketIdAfterError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.shared.and.non.shared/client",
        "${upstream}/subscribe.shared.and.non.shared/server"})
    public void shouldSubscribeSharedAndNonShared() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.shared.receive.message.qos1/client",
        "${upstream}/subscribe.shared.receive.message.qos1/server"})
    public void shouldSubscribeSharedReceiveMessageQos1() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/client",
        "${upstream}/subscribe.shared.receive.message.qos1.ack.with.reasoncode/server"})
    public void shouldSubscribeSharedReceiveMessageQos1AckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.shared.receive.message.qos2/client",
        "${upstream}/subscribe.shared.receive.message.qos2/server"})
    public void shouldSubscribeSharedReceiveMessageQos2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.shared.subscription/client",
        "${upstream}/subscribe.shared.subscription/server"})
    public void shouldSubscribeSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/unsubscribe.shared.subscription/client",
        "${upstream}/unsubscribe.shared.subscription/server"})
    public void shouldUnsubscribeSharedSubscription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${upstream}/subscribe.shared.and.non.shared.receive.messages/client",
        "${upstream}/subscribe.shared.and.non.shared.receive.messages/server"})
    public void shouldSubscribeSharedAndNonSharedReceiveMessages() throws Exception
    {
        k3po.finish();
    }
}
