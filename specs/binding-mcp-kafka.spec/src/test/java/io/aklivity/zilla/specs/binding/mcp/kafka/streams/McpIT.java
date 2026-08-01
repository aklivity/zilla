/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.specs.binding.mcp.kafka.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class McpIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/kafka/streams/mcp");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${mcp}/produce/client",
        "${mcp}/produce/server"})
    public void shouldProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/produce.rejected/client",
        "${mcp}/produce.rejected/server"})
    public void shouldRejectProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/produce.rejected.invalid.record/client",
        "${mcp}/produce.rejected.invalid.record/server"})
    public void shouldRejectProduceWithInvalidRecord() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/consume/client",
        "${mcp}/consume/server"})
    public void shouldConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/consume.limit/client",
        "${mcp}/consume.limit/server"})
    public void shouldStopConsumeAtLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/consume.timeout/client",
        "${mcp}/consume.timeout/server"})
    public void shouldTimeoutConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/consume.without.limit/client",
        "${mcp}/consume.without.limit/server"})
    public void shouldConsumeWithoutLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/reject.invalid.args/client",
        "${mcp}/reject.invalid.args/server"})
    public void shouldRejectInvalidArgs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/produce.args.fragmented/client",
        "${mcp}/produce.args.fragmented/server"})
    public void shouldProduceArgsFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/consume.result.fragmented/client",
        "${mcp}/consume.result.fragmented/server"})
    public void shouldConsumeResultFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/reject.topic.not.allowed/client",
        "${mcp}/reject.topic.not.allowed/server"})
    public void shouldRejectProduceWhenTopicNotInAllowlist() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/reject.not.authorized/client",
        "${mcp}/reject.not.authorized/server"})
    public void shouldRejectToolsCallWhenRouteNotAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/produce.topic.glob/client",
        "${mcp}/produce.topic.glob/server"})
    public void shouldProduceWhenTopicMatchesGlob() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/produce.abort/client",
        "${mcp}/produce.abort/server"})
    public void shouldAbortMidProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/consume.abort/client",
        "${mcp}/consume.abort/server"})
    public void shouldAbortMidConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.list/client",
        "${mcp}/tools.list/server"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.topics/client",
        "${mcp}/create.topics/server"})
    public void shouldCreateTopics() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/delete.topics/client",
        "${mcp}/delete.topics/server"})
    public void shouldDeleteTopics() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.configs/client",
        "${mcp}/describe.configs/server"})
    public void shouldDescribeConfigs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/alter.configs/client",
        "${mcp}/alter.configs/server"})
    public void shouldAlterConfigs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/list.acls/client",
        "${mcp}/list.acls/server"})
    public void shouldListAcls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.acls/client",
        "${mcp}/create.acls/server"})
    public void shouldCreateAcls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/delete.acls/client",
        "${mcp}/delete.acls/server"})
    public void shouldDeleteAcls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/list.topics/client",
        "${mcp}/list.topics/server"})
    public void shouldListTopics() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.topic/client",
        "${mcp}/describe.topic/server"})
    public void shouldDescribeTopic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/cluster.overview/client",
        "${mcp}/cluster.overview/server"})
    public void shouldClusterOverview() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/list.brokers/client",
        "${mcp}/list.brokers/server"})
    public void shouldListBrokers() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.cluster/client",
        "${mcp}/describe.cluster/server"})
    public void shouldDescribeCluster() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/list.consumer.groups/client",
        "${mcp}/list.consumer.groups/server"})
    public void shouldListConsumerGroups() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.consumer.group/client",
        "${mcp}/describe.consumer.group/server"})
    public void shouldDescribeConsumerGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/reset.offsets/client",
        "${mcp}/reset.offsets/server"})
    public void shouldResetOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/reset.offsets.coordinator.not.found/client",
        "${mcp}/reset.offsets.coordinator.not.found/server"})
    public void shouldResetOffsetsCoordinatorNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.consumer.group.lag/client",
        "${mcp}/describe.consumer.group.lag/server"})
    public void shouldDescribeConsumerGroupLag() throws Exception
    {
        k3po.finish();
    }
}
