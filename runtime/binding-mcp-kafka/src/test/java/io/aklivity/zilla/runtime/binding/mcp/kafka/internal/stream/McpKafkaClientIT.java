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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class McpKafkaClientIT
{
    private static final String MCP_KAFKA_SESSION_ID_NAME = "zilla.binding.mcp.kafka.session.id";
    private static final String CACHE_CLIENT_EXIT_NAME = "zilla.binding.mcp.kafka.cache.client.exit";
    private static final String CLIENT_EXIT_NAME = "zilla.binding.mcp.kafka.client.exit";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/kafka/streams/mcp")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mcp/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 16384)
        .configure(MCP_KAFKA_SESSION_ID_NAME, "%s::sessionId".formatted(McpKafkaClientIT.class.getName()))
        .configure(CACHE_CLIENT_EXIT_NAME, "test:kafka0")
        .configure(CLIENT_EXIT_NAME, "test:kafka0")
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.produce.yaml")
    @Specification({
        "${mcp}/produce/client",
        "${kafka}/produce/server"})
    public void shouldProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.consume.yaml")
    @Specification({
        "${mcp}/consume/client",
        "${kafka}/consume/server"})
    public void shouldConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.create.topics.yaml")
    @Specification({
        "${mcp}/create.topics/client",
        "${kafka}/create.topics/server"})
    public void shouldCreateTopics() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.create.topics.yaml")
    @Specification({
        "${mcp}/create.topics/client",
        "${kafka}/create.topics.negotiated/server"})
    public void shouldCreateTopicsNegotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.delete.topics.yaml")
    @Specification({
        "${mcp}/delete.topics/client",
        "${kafka}/delete.topics/server"})
    public void shouldDeleteTopics() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.delete.topics.yaml")
    @Specification({
        "${mcp}/delete.topics/client",
        "${kafka}/delete.topics.negotiated/server"})
    public void shouldDeleteTopicsNegotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.list.consumer.groups.yaml")
    @Specification({
        "${mcp}/list.consumer.groups/client",
        "${kafka}/list.consumer.groups/server"})
    public void shouldListConsumerGroups() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.describe.consumer.group.yaml")
    @Specification({
        "${mcp}/describe.consumer.group/client",
        "${kafka}/describe.consumer.group/server"})
    public void shouldDescribeConsumerGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.reset.offsets.yaml")
    @Specification({
        "${mcp}/reset.offsets.coordinator.not.found/client",
        "${kafka}/find.coordinator.error/server"})
    public void shouldResetOffsetsCoordinatorNotFound() throws Exception
    {
        k3po.finish();
    }

    public static String sessionId()
    {
        return "session-1";
    }
}
