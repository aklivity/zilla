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

public class McpKafkaProxyIT
{
    private static final String MCP_KAFKA_SESSION_ID_NAME = "zilla.binding.mcp.kafka.session.id";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/kafka/streams/mcp")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/mcp/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 16384)
        .configure(MCP_KAFKA_SESSION_ID_NAME, "%s::sessionId".formatted(McpKafkaProxyIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.produce.yaml")
    @Specification({
        "${mcp}/produce/client",
        "${kafka}/produce/server"})
    public void shouldProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.yaml")
    @Specification({
        "${mcp}/produce.rejected/client",
        "${kafka}/produce.rejected/server"})
    public void shouldRejectProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.yaml")
    @Specification({
        "${mcp}/produce.rejected.invalid.record/client",
        "${kafka}/produce.rejected.invalid.record/server"})
    public void shouldRejectProduceWithInvalidRecord() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.consume.yaml")
    @Specification({
        "${mcp}/consume/client",
        "${kafka}/consume/server"})
    public void shouldConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.consume.yaml")
    @Specification({
        "${mcp}/consume.limit/client",
        "${kafka}/consume.limit/server"})
    public void shouldStopConsumeAtLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.consume.yaml")
    @Specification({
        "${mcp}/consume.timeout/client",
        "${kafka}/consume.timeout/server"})
    public void shouldTimeoutConsume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.yaml")
    @Specification({
        "${mcp}/reject.invalid.args/client"})
    public void shouldRejectInvalidArgs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.yaml")
    @Specification({
        "${mcp}/produce.args.multiframe/client",
        "${kafka}/produce.args.multiframe/server"})
    public void shouldProduceWithArgsSpanningMultipleDataFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.topic.allowlist.yaml")
    @Specification({
        "${mcp}/produce/client",
        "${kafka}/produce/server"})
    public void shouldProduceWhenTopicMatchesAllowlist() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.topic.allowlist.yaml")
    @Specification({
        "${mcp}/reject.topic.not.allowed/client"})
    public void shouldRejectProduceWhenTopicNotInAllowlist() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.guarded.yaml")
    @Specification({
        "${mcp}/reject.not.authorized/client"})
    public void shouldRejectToolsCallWhenRouteNotAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.produce.yaml")
    @Specification({
        "${mcp}/produce.abort/client",
        "${kafka}/produce.abort/server"})
    public void shouldAbortMidProduce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.consume.yaml")
    @Specification({
        "${mcp}/consume.abort/client",
        "${kafka}/consume.abort/server"})
    public void shouldAbortMidConsume() throws Exception
    {
        k3po.finish();
    }

    public static String sessionId()
    {
        return "session-1";
    }
}
