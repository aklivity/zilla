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

public class StreamIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mqtt/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/subscribe.then.close/client",
        "${app}/subscribe.then.close/server"})
    public void shouldSubscribeThenClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.then.abort/client",
        "${app}/subscribe.then.abort/server"})
    public void shouldSubscribeThenAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.exact.topic.filter/client",
        "${app}/subscribe.with.exact.topic.filter/server"})
    public void shouldSubscribeWithExactTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.wildcard.topic.filter/client",
        "${app}/subscribe.with.wildcard.topic.filter/server"})
    public void shouldSubscribeWithWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.aggregated.topic.filters.both.exact/client",
        "${app}/subscribe.with.aggregated.topic.filters.both.exact/server"})
    public void shouldSubscribeWithAggregatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.isolated.topic.filters.both.exact/client",
        "${app}/subscribe.with.isolated.topic.filters.both.exact/server"})
    public void shouldSubscribeWithIsolatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.aggregated.topic.filters.both.wildcard/client",
        "${app}/subscribe.with.aggregated.topic.filters.both.wildcard/server"})
    public void shouldSubscribeWithAggregatedTopicFiltersBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.isolated.topic.filters.both.wildcard/client",
        "${app}/subscribe.with.isolated.topic.filters.both.wildcard/server"})
    public void shouldSubscribeWithIsolatedTopicFiltersBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.aggregated.topic.filters.exact.and.wildcard/client",
        "${app}/subscribe.with.aggregated.topic.filters.exact.and.wildcard/server"})
    public void shouldSubscribeWithAggregatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.with.isolated.topic.filters.exact.and.wildcard/client",
        "${app}/subscribe.with.isolated.topic.filters.exact.and.wildcard/server"})
    public void shouldSubscribeWithIsolatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message/client",
        "${app}/subscribe.one.message/server"})
    public void shouldPublishToSubscriberOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.then.publish.no.local/client",
        "${app}/subscribe.then.publish.no.local/server"})
    public void shouldSubscribeThenPublishNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.then.subscribe.no.local/client",
        "${app}/publish.then.subscribe.no.local/server"})
    public void shouldPublishThenSubscribeNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.then.subscribe.one.message/client",
        "${app}/publish.then.subscribe.one.message/server"})
    public void shouldPublishThenSubscribeOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message.with.pattern.topic/client",
        "${app}/subscribe.one.message.with.pattern.topic/server"})
    public void shouldPublishToSubscriberOneMessageWithPatternTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message.with.null.payload/client",
        "${app}/subscribe.one.message.with.null.payload/server"})
    public void shouldPublishWithNullPayloadToSubscriber() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.one.message.then.publish.message/client",
        "${app}/subscribe.one.message.then.publish.message/server"})
    public void shouldSubscribeOneMessageThenPublishMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.message.and.subscribe.correlated.message/client",
        "${app}/publish.message.and.subscribe.correlated.message/server"})
    public void shouldPublishMessageAndSubscribeCorrelatedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.retained/client",
        "${app}/subscribe.retained/server"})
    public void shouldSubscribeRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.one.message/client",
        "${app}/publish.one.message/server"})
    public void shouldPublishOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.multiple.messages/client",
        "${app}/publish.multiple.messages/server"})
    public void shouldPublishMultipleMessagesToServer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.multiple.messages.with.delay/client",
        "${app}/publish.multiple.messages.with.delay/server"})
    public void shouldPublishMultipleMessagesWithDelay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.one.message.then.disconnect/client",
        "${app}/publish.one.message.then.disconnect/server"})
    public void shouldPublishOneMessageThenDisconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.rejected/client",
        "${app}/publish.rejected/server"})
    public void shouldRejectPublish() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.abort/client",
        "${app}/client.sent.abort/server"})
    public void shouldReceiveClientSentAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.property/client",
        "${app}/publish.with.user.property/server"})
    public void shouldPublishWithUserProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.properties.repeated/client",
        "${app}/publish.with.user.properties.repeated/server"})
    public void shouldPublishWithRepeatedUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.with.user.properties.distinct/client",
        "${app}/publish.with.user.properties.distinct/server"})
    public void shouldPublishWithDistinctUserProperties() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.fails.then.publish.message/client",
        "${app}/subscribe.fails.then.publish.message/server"})
    public void shouldFailSubscribeThenPublishMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.message.with.topic.alias/client",
        "${app}/publish.message.with.topic.alias/server"})
    public void shouldPublishMessageWithTopicAlias() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.distinct/client",
        "${app}/publish.messages.with.topic.alias.distinct/server"})
    public void shouldPublishMessagesWithTopicAliasDistinct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.repeated/client",
        "${app}/publish.messages.with.topic.alias.repeated/server"})
    public void shouldPublishMessagesWithTopicAliasRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.messages.with.topic.alias.replaced/client",
        "${app}/publish.messages.with.topic.alias.replaced/server"})
    public void shouldPublishMessagesWithTopicAliasesReplaced() throws Exception
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
        "${app}/publish.empty.retained.message/client",
        "${app}/publish.empty.retained.message/server"})
    public void shouldPublishEmptyRetainedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.empty.message/client",
        "${app}/publish.empty.message/server"})
    public void shouldPublishEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/publish.session.data/client",
        "${app}/publish.session.data/server"})
    public void shouldPublishSessionData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/unpublished.will.message/client",
        "${app}/unpublished.will.message/server"})
    public void shouldIgnoreWillMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.to.will.topic/client",
        "${app}/subscribe.to.will.topic/server"})
    public void shouldFailSubscribeToWillTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.with.will.message.then.publish.one.message/client",
        "${app}/connect.with.will.message.then.publish.one.message/server"})
    public void shouldConnectWithWillMessageThenPublishOneMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connect.with.session.expiry/client",
        "${app}/connect.with.session.expiry/server"})
    public void shouldConnectWithSessionExpiry() throws Exception
    {
        k3po.finish();
    }
}
