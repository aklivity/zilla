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

public class KafkaIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mcp/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${kafka}/produce/client",
        "${kafka}/produce/server"})
    public void shouldProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/produce.rejected/client",
        "${kafka}/produce.rejected/server"})
    public void shouldRejectProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/produce.rejected.invalid.record/client",
        "${kafka}/produce.rejected.invalid.record/server"})
    public void shouldRejectProduceWithInvalidRecord() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/consume/client",
        "${kafka}/consume/server"})
    public void shouldConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/consume.limit/client",
        "${kafka}/consume.limit/server"})
    public void shouldStopConsumeAtLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/consume.timeout/client",
        "${kafka}/consume.timeout/server"})
    public void shouldTimeoutConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/consume.without.limit/client",
        "${kafka}/consume.without.limit/server"})
    public void shouldConsumeWithoutLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/produce.args.fragmented/client",
        "${kafka}/produce.args.fragmented/server"})
    public void shouldProduceArgsFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/consume.result.fragmented/client",
        "${kafka}/consume.result.fragmented/server"})
    public void shouldConsumeResultFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/produce.topic.glob/client",
        "${kafka}/produce.topic.glob/server"})
    public void shouldProduceWhenTopicMatchesGlob() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/create.topics/client",
        "${kafka}/create.topics/server"})
    public void shouldCreateTopics() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/create.topics.negotiated/client",
        "${kafka}/create.topics.negotiated/server"})
    public void shouldCreateTopicsNegotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.topics/client",
        "${kafka}/delete.topics/server"})
    public void shouldDeleteTopics() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.topics.negotiated/client",
        "${kafka}/delete.topics.negotiated/server"})
    public void shouldDeleteTopicsNegotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/list.consumer.groups/client",
        "${kafka}/list.consumer.groups/server"})
    public void shouldListConsumerGroups() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/describe.consumer.group/client",
        "${kafka}/describe.consumer.group/server"})
    public void shouldDescribeConsumerGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/find.coordinator.for.reset/client",
        "${kafka}/find.coordinator.for.reset/server"})
    public void shouldFindCoordinatorForReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/describe.groups.for.reset/client",
        "${kafka}/describe.groups.for.reset/server"})
    public void shouldDescribeGroupsForReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/find.coordinator.error/client",
        "${kafka}/find.coordinator.error/server"})
    public void shouldFindCoordinatorError() throws Exception
    {
        k3po.finish();
    }
}
