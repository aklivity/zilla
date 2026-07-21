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
package io.aklivity.zilla.specs.binding.mcp.openapi.streams;

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
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/openapi/streams/mcp");

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
        "${mcp}/read.order/client",
        "${mcp}/read.order/server"})
    public void shouldReadResourceOrder() throws Exception
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
        "${mcp}/resources.templates.list.with.override/client",
        "${mcp}/resources.templates.list.with.override/server"})
    public void shouldListResourcesTemplatesWithOverriddenDescription() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/resources.templates.list/client",
        "${mcp}/resources.templates.list/server"})
    public void shouldListResourcesTemplates() throws Exception
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
        "${mcp}/create.pr.aborted/client",
        "${mcp}/create.pr.aborted/server"})
    public void shouldAbortToolCreatePr() throws Exception
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
        "${mcp}/search.code.forbidden/client",
        "${mcp}/search.code.forbidden/server"})
    public void shouldRejectToolWhenUnauthorized() throws Exception
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
}
