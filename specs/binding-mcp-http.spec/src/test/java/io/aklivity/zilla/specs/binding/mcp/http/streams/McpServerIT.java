/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.mcp.http.streams;

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
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/http/streams/mcp");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${mcp}/create.pr/client",
        "${mcp}/create.pr/server"})
    public void shouldCallToolCreatePr() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.remap/client",
        "${mcp}/create.pr.remap/server"})
    public void shouldCallToolWithBodyTemplate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/initialize/client",
        "${mcp}/initialize/server"})
    public void shouldInitialize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/lifecycle.data/client",
        "${mcp}/lifecycle.data/server"})
    public void shouldConsumeLifecycleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/lifecycle.client.abort/client",
        "${mcp}/lifecycle.client.abort/server"})
    public void shouldAbortLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/lifecycle.client.reset/client",
        "${mcp}/lifecycle.client.reset/server"})
    public void shouldResetLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.10k/client",
        "${mcp}/create.pr.10k/server"})
    public void shouldCallToolCreatePr10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.100k/client",
        "${mcp}/create.pr.100k/server"})
    public void shouldCallToolCreatePr100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.fragmented/client",
        "${mcp}/create.pr.fragmented/server"})
    public void shouldCallToolCreatePrFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.rich/client",
        "${mcp}/create.pr.rich/server"})
    public void shouldCallToolCreatePrWithStructuredArguments() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/read.order.10k/client",
        "${mcp}/read.order.10k/server"})
    public void shouldReadResourceOrder10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/read.order.100k/client",
        "${mcp}/read.order.100k/server"})
    public void shouldReadResourceOrder100k() throws Exception
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
        "${mcp}/resources.list/client",
        "${mcp}/resources.list/server"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/read.order/client",
        "${mcp}/read.order/server"})
    public void shouldReadResourceOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/search.code/client",
        "${mcp}/search.code/server"})
    public void shouldCallToolSearchCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/search.code.query.array/client",
        "${mcp}/search.code.query.array/server"})
    public void shouldCallToolSearchCodeWithArrayAndBooleanQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/search.items/client",
        "${mcp}/search.items/server"})
    public void shouldCallToolSearchItemsWithoutOptionalQueryParameter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/search.items.with.limit/client",
        "${mcp}/search.items.with.limit/server"})
    public void shouldCallToolSearchItemsWithOptionalQueryParameter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.error/client",
        "${mcp}/create.pr.error/server"})
    public void shouldRejectToolCreatePr() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.aborted/client",
        "${mcp}/create.pr.aborted/server"})
    public void shouldAbortToolCreatePr() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.pr.error.100k/client",
        "${mcp}/create.pr.error.100k/server"})
    public void shouldRejectToolCreatePr100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/search.code.100k/client",
        "${mcp}/search.code.100k/server"})
    public void shouldCallToolSearchCode100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/get.report.large/client",
        "${mcp}/get.report.large/server"})
    public void shouldCallToolGetReportWithLargeSummary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/ping/client",
        "${mcp}/ping/server"})
    public void shouldCallToolPing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/list.tags/client",
        "${mcp}/list.tags/server"})
    public void shouldCallToolListTags() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/count.items/client",
        "${mcp}/count.items/server"})
    public void shouldCallToolCountItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/echo.id.large/client",
        "${mcp}/echo.id.large/server"})
    public void shouldCallToolEchoIdWithLargeArgument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/get.profile/client",
        "${mcp}/get.profile/server"})
    public void shouldCallToolGetProfileWithNoSummary() throws Exception
    {
        k3po.finish();
    }
}
