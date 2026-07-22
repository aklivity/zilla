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
        "${mcp}/reject.invalid.args/client",
        "${mcp}/reject.invalid.args/server"})
    public void shouldRejectInvalidArgs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/produce.args.multiframe/client",
        "${mcp}/produce.args.multiframe/server"})
    public void shouldProduceWithArgsSpanningMultipleDataFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/consume.result.multiframe/client",
        "${mcp}/consume.result.multiframe/server"})
    public void shouldChunkConsumeResultAcrossEncodeSlotBoundary() throws Exception
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
}
