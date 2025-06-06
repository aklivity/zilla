/*
 * Copyright 2021-2024 Aklivity Inc.
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

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class PublishIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/publish.one.message.properties/client",
        "${app}/publish.one.message.properties/server"})
    public void shouldSendOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.subscribe.batched/client",
        "${app}/publish.subscribe.batched/server"})
    public void shouldSendOneMessageSubscribeBatched() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.multiple.messages/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldSendMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.session.takeover/client",
        "${app}/publish.session.takeover/server"})
    public void shouldSendMessageAfterSessionTakeover() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.multiple.clients/client",
        "${app}/publish.multiple.clients/server"})
    public void shouldSendMultipleClients() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.multiple.messages.timeout/client",
        "${app}/publish.multiple.messages.timeout/server"})
    public void shouldPublishMultipleMessagesTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.property/client",
        "${app}/publish.with.user.property/server"})
    public void shouldSendWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.properties.repeated/client",
        "${app}/publish.with.user.properties.repeated/server"})
    public void shouldSendWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.properties.distinct/client",
        "${app}/publish.with.user.properties.distinct/server"})
    public void shouldSendWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.message.with.topic.alias/client",
        "${app}/publish.message.with.topic.alias/server"})
    public void shouldSendMessageWithTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.distinct/client",
        "${app}/publish.messages.with.topic.alias.distinct/server"})
    public void shouldSendMessagesWithTopicAliasDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.repeated/client",
        "${app}/publish.messages.with.topic.alias.repeated/server"})
    public void shouldSendMessagesWithTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.replaced/client",
        "${app}/publish.messages.with.topic.alias.replaced/server"})
    public void shouldSendMessagesWithTopicAliasesReplaced() throws Exception
    {
        k3po.finish();
    }

    // [MQTT-3.3.2-7]
    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.invalid.scope/client",
        "${app}/publish.messages.with.topic.alias.invalid.scope/server"})
    public void shouldSendMessagesWithTopicAliasInvalidScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.retained/client",
        "${app}/publish.retained/server"})
    public void shouldPublishRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.retained.multiple.topic/client",
        "${app}/publish.retained.multiple.topic/server"})
    public void shouldPublishRetainedMessageMultipleTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.empty.retained.message/client",
        "${app}/publish.empty.retained.message/server"})
    public void shouldSendEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.empty.message/client",
        "${app}/publish.empty.message/server"})
    public void shouldSendEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.valid.message/client",
        "${app}/publish.valid.message/server"})
    public void shouldPublishValidMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.valid.user.property/client",
        "${app}/publish.valid.user.property/server"})
    public void shouldPublishValidUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.unroutable/client",
        "${app}/publish.unroutable/server"})
    public void shouldRejectUnroutable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.qos1.dup.after.puback/client",
        "${app}/publish.qos1.dup.after.puback/server"})
    public void shouldPublishQoS1Message() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.qos2.no.dupicate.before.pubrel/client",
        "${app}/publish.qos2.no.dupicate.before.pubrel/server"})
    public void shouldPublishQoS2NoDupBeforePubrel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.qos2.ack.with.reasoncode/client",
        "${app}/publish.qos2.ack.with.reasoncode/server"})
    public void shouldPublishQoS2MessageAckWithReasoncode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.qos2.recovery/client",
        "${app}/publish.qos2.recovery/server"})
    public void shouldReleaseQos2PacketIdDuringRecovery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.mixture.qos/client",
        "${app}/publish.mixture.qos/server"})
    public void shouldPublishMixtureQos() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.10k/client",
        "${app}/publish.10k/server"})
    public void shouldPublish10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.reject.large.message/client",
        "${app}/publish.reject.large.message/server"})
    public void shouldRejectLargeMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.topic.guarded.identity.param/client",
        "${app}/publish.topic.guarded.identity.param/server"})
    public void shouldPublishToTopicWithGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.invalid.topic.guarded.identity.param/client",
        "${app}/publish.invalid.topic.guarded.identity.param/server"})
    public void shouldRejectTopicWithInvalidGuardedIdentityParam() throws Exception
    {
        k3po.finish();
    }
}
