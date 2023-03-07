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

public class SubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/subscribe.missing.topic.filters/client",
        "${net}/subscribe.missing.topic.filters/server"})
    public void shouldRejectSubscribeWithMissingTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.invalid.topic.filter/client",
        "${net}/subscribe.invalid.topic.filter/server"})
    public void shouldRejectSubscribeWithInvalidTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.exact/client",
        "${net}/subscribe.topic.filter.single.exact/server"})
    public void shouldSubscribeToExactTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filter.single.wildcard/client",
        "${net}/subscribe.topic.filter.single.wildcard/server"})
    public void shouldSubscribeToWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.exact/client",
        "${net}/subscribe.topic.filters.aggregated.both.exact/server"})
    public void shouldSubscribeToAggregatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.exact/client",
        "${net}/subscribe.topic.filters.isolated.both.exact/server"})
    public void shouldSubscribeToIsolatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.both.wildcard/client",
        "${net}/subscribe.topic.filters.aggregated.both.wildcard/server"})
    public void shouldSubscribeToAggregatedTopicFiltersBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.both.wildcard/client",
        "${net}/subscribe.topic.filters.isolated.both.wildcard/server"})
    public void shouldSubscribeToIsolatedTopicFiltersBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/client",
        "${net}/subscribe.topic.filters.aggregated.exact.and.wildcard/server"})
    public void shouldSubscribeToAggregatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/client",
        "${net}/subscribe.topic.filters.isolated.exact.and.wildcard/server"})
    public void shouldSubscribeToIsolatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.invalid.fixed.header.flags/client",
        "${net}/subscribe.invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedSubscribePacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.reject.no.local/client",
        "${net}/subscribe.reject.no.local/server"})
    public void shouldRejectNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.receive.correlation.data/client",
        "${net}/subscribe.one.message.receive.correlation.data/server"})
    public void shouldReceiveCorrelationDataAfterSendingSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.with.invalid.subscription.id/client",
        "${net}/subscribe.one.message.with.invalid.subscription.id/server"})
    public void shouldSubscribeOneMessageWithInvalidSubscriptionId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.after.publish.no.local/client",
        "${net}/subscribe.after.publish.no.local/server"})
    public void shouldSubscribeAfterPublishNoLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.one.message.get.local/client",
        "${net}/subscribe.one.message.get.local/server"})
    public void shouldSubscribeGetLocalPublishedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.get.retained.as.published/client",
        "${net}/subscribe.get.retained.as.published/server"})
    public void shouldSubscribeGetRetainedMessageAsPublished() throws Exception
    {
        k3po.finish();
    }
}
