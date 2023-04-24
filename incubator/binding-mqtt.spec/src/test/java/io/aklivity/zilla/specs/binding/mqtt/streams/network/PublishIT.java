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

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/publish.one.message/client",
        "${net}/publish.one.message/server"})
    public void shouldPublishOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.multiple.messages/client",
        "${net}/publish.multiple.messages/server"})
    public void shouldPublishMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.multiple.messages.with.delay/client",
        "${net}/publish.multiple.messages.with.delay/server"})
    public void shouldPublishMultipleMessagesWithDelay() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("PUBLISHED_MESSAGE_TWO");
        k3po.notifyBarrier("PUBLISH_MESSAGE_THREE");
        k3po.finish();
    }

    // [MQTT-2.2.1-2]
    @Test
    @Specification({
        "${net}/publish.reject.qos0.with.packet.id/client",
        "${net}/publish.reject.qos0.with.packet.id/server"})
    public void shouldRejectWithPacketIdAtQos0() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-9], [MQTT-3.2.2-12]
    @Test
    @Specification({
        "${net}/publish.reject.qos1.not.supported/client",
        "${net}/publish.reject.qos1.not.supported/server"})
    public void shouldRejectQos1NotSupported() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-9], [MQTT-3.2.2-12]
    @Test
    @Specification({
        "${net}/publish.reject.qos2.not.supported/client",
        "${net}/publish.reject.qos2.not.supported/server"})
    public void shouldRejectQos2NotSupported() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.qos1.without.packet.id/client",
        "${net}/publish.reject.qos1.without.packet.id/server"})
    public void shouldRejectWithoutPacketIdAtQos1() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.qos2.without.packet.id/client",
        "${net}/publish.reject.qos2.without.packet.id/server"})
    public void shouldRejectWithoutPacketIdAtQos2() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-2.2.1-3]
    @Test
    @Specification({
        "${net}/publish.reject.invalid.payload.format/client",
        "${net}/publish.reject.invalid.payload.format/server"})
    public void shouldRejectPublishInvalidPayloadFormat() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.4-6]
    @Test
    @Specification({
        "${net}/publish.reject.client.sent.subscription.id/client",
        "${net}/publish.reject.client.sent.subscription.id/server"})
    public void shouldRejectPublishClientSentSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.reject.topic.alias.exceeds.maximum/client",
        "${net}/publish.reject.topic.alias.exceeds.maximum/server"})
    public void shouldRejectPublishWhenTopicAliasExceedsThanMaximum() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.2.2-14]
    @Test
    @Specification({
        "${net}/publish.reject.retain.not.supported/client",
        "${net}/publish.reject.retain.not.supported/server"})
    public void shouldRejectRetainedPublishRetainNotSupported() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${net}/publish.reject.topic.alias.repeated/client",
        "${net}/publish.reject.topic.alias.repeated/server"})
    public void shouldRejectPublishWhenTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.topic.not.routed/client",
        "${net}/publish.topic.not.routed/server"})
    public void shouldRejectTopicNotRouted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.with.user.property/client",
        "${net}/publish.with.user.property/server"})
    public void shouldPublishWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.with.user.properties.repeated/client",
        "${net}/publish.with.user.properties.repeated/server"})
    public void shouldPublishWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.with.user.properties.distinct/client",
        "${net}/publish.with.user.properties.distinct/server"})
    public void shouldPublishWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.message.with.topic.alias/client",
        "${net}/publish.message.with.topic.alias/server"})
    public void shouldPublishMessageWithTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.messages.with.topic.alias.distinct/client",
        "${net}/publish.messages.with.topic.alias.distinct/server"})
    public void shouldPublishMessagesWithTopicAliasDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.messages.with.topic.alias.repeated/client",
        "${net}/publish.messages.with.topic.alias.repeated/server"})
    public void shouldPublishMessagesWithTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.messages.with.topic.alias.replaced/client",
        "${net}/publish.messages.with.topic.alias.replaced/server"})
    public void shouldPublishMessagesWithTopicAliasReplaced() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.2-7]
    @Test
    @Specification({
        "${net}/publish.messages.no.carry.over.topic.alias/client",
        "${net}/publish.messages.no.carry.over.topic.alias/server"})
    public void shouldPublishMessagesNoCarryOverTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.retained/client",
        "${net}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.empty.retained.message/client",
        "${net}/publish.empty.retained.message/server"})
    public void shouldPublishEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/publish.empty.message/client",
        "${net}/publish.empty.message/server"})
    public void shouldPublishEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.publish.no.local/client",
        "${net}/subscribe.publish.no.local/server"})
    public void shouldSubscribeThenPublishNoLocal() throws Exception
    {
        k3po.finish();
    }
}
