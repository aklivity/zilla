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

public class SubscribeIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mqtt/streams/network/subscribe");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/missing.topic.filters/client",
        "${net}/missing.topic.filters/server"})
    public void shouldRejectSubscribeWithMissingTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.topic.filter/client",
        "${net}/invalid.topic.filter/server"})
    public void shouldRejectSubscribeWithInvalidTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/single.topic.filter.exact/client",
        "${net}/single.topic.filter.exact/server"})
    public void shouldSubscribeToExactTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/single.topic.filter.wildcard/client",
        "${net}/single.topic.filter.wildcard/server"})
    public void shouldSubscribeToWildcardTopicFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/aggregated.topic.filters.both.exact/client",
        "${net}/aggregated.topic.filters.both.exact/server"})
    public void shouldSubscribeToAggregatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/isolated.topic.filters.both.exact/client",
        "${net}/isolated.topic.filters.both.exact/server"})
    public void shouldSubscribeToIsolatedTopicFiltersBothExact() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/aggregated.topic.filters.both.wildcard/client",
        "${net}/aggregated.topic.filters.both.wildcard/server"})
    public void shouldSubscribeToAggregatedTopicFiltersBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/isolated.topic.filters.both.wildcard/client",
        "${net}/isolated.topic.filters.both.wildcard/server"})
    public void shouldSubscribeToIsolatedTopicFiltersBothWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/aggregated.topic.filters.exact.and.wildcard/client",
        "${net}/aggregated.topic.filters.exact.and.wildcard/server"})
    public void shouldSubscribeToAggregatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/isolated.topic.filters.exact.and.wildcard/client",
        "${net}/isolated.topic.filters.exact.and.wildcard/server"})
    public void shouldSubscribeToIsolatedExactAndWildcardTopicFilters() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.fixed.header.flags/client",
        "${net}/invalid.fixed.header.flags/server"})
    public void shouldRejectMalformedSubscribePacket() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.no.local/client",
        "${net}/reject.no.local/server"})
    public void shouldRejectNoLocal() throws Exception
    {
        k3po.finish();
    }
}
