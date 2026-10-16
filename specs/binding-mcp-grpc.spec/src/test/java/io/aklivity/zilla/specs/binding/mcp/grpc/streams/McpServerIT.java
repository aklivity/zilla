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
package io.aklivity.zilla.specs.binding.mcp.grpc.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class McpServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/grpc/streams/mcp");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

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
        "${mcp}/tools.list.with.tools/client",
        "${mcp}/tools.list.with.tools/server"})
    public void shouldListToolsWithToolOptions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.list.with.method.routes/client",
        "${mcp}/tools.list.with.method.routes/server"})
    public void shouldListToolsWithMethodRoutes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.list.with.option.route/client",
        "${mcp}/tools.list.with.option.route/server"})
    public void shouldListToolsWithOptionRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call/client",
        "${mcp}/tools.call/server"})
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.pinned/client",
        "${mcp}/tools.call.pinned/server"})
    public void shouldCallToolWithPinnedName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.error/client",
        "${mcp}/tools.call.error/server"})
    public void shouldCallToolReturningError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.aborted/client",
        "${mcp}/tools.call.aborted/server"})
    public void shouldAbortToolCallWhenRpcAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.reset/client",
        "${mcp}/tools.call.reset/server"})
    public void shouldAbortToolCallWhenRpcReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.streaming.aborted/client",
        "${mcp}/tools.call.streaming.aborted/server"})
    public void shouldAbortToolCallWhenRpcResponseAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.10k/client",
        "${mcp}/tools.call.10k/server"})
    public void shouldCallTool10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.100k/client",
        "${mcp}/tools.call.100k/server"})
    public void shouldCallTool100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.list.with.summary/client",
        "${mcp}/tools.list.with.summary/server"})
    public void shouldListToolsWithSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.with.summary/client",
        "${mcp}/tools.call.with.summary/server"})
    public void shouldCallToolWithSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.client.aborted/client",
        "${mcp}/tools.call.client.aborted/server"})
    public void shouldRelayToolCallClientAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.client.reset/client",
        "${mcp}/tools.call.client.reset/server"})
    public void shouldRelayToolCallClientReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.unknown/client",
        "${mcp}/tools.call.unknown/server"})
    public void shouldRejectUnknownTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.denied/client",
        "${mcp}/tools.call.denied/server"})
    public void shouldRejectDeniedTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/tools.call.with.credentials/client",
        "${mcp}/tools.call.with.credentials/server"})
    public void shouldCallToolWithCredentials() throws Exception
    {
        k3po.finish();
    }
}
