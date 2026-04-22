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
package io.aklivity.zilla.specs.binding.mcp.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/lifecycle.initialize/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.shutdown/client",
        "${app}/lifecycle.shutdown/server"})
    public void shouldShutdownLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.shutdown.requests/client",
        "${app}/lifecycle.shutdown.requests/server"})
    public void shouldShutdownLifecycleRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.timeout/client",
        "${app}/lifecycle.timeout/server"})
    public void shouldTimeoutLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.request.method.before.id/client",
        "${app}/reject.request.method.before.id/server"})
    public void shouldRejectRequestMethodBeforeId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.request.params.before.method/client",
        "${app}/reject.request.params.before.method/server"})
    public void shouldRejectRequestParamsBeforeMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reject.request.params.array/client",
        "${app}/reject.request.params.array/server"})
    public void shouldRejectRequestParamsWithArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.10k/client",
        "${app}/tools.call.10k/server"})
    public void shouldCallToolWith10kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.100k/client",
        "${app}/tools.call.100k/server"})
    public void shouldCallToolWith100kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.10k/client",
        "${app}/resources.read.10k/server"})
    public void shouldReadResourceWith10kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.100k/client",
        "${app}/resources.read.100k/server"})
    public void shouldReadResourceWith100kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call/client",
        "${app}/tools.call/server"})
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list/client",
        "${app}/tools.list/server"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.aborted/client",
        "${app}/tools.list.aborted/server"})
    public void shouldAbortToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.aborted/client",
        "${app}/tools.call.aborted/server"})
    public void shouldAbortCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.list.aborted/client",
        "${app}/prompts.list.aborted/server"})
    public void shouldAbortListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.get.aborted/client",
        "${app}/prompts.get.aborted/server"})
    public void shouldAbortGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.list.aborted/client",
        "${app}/resources.list.aborted/server"})
    public void shouldAbortListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.aborted/client",
        "${app}/resources.read.aborted/server"})
    public void shouldAbortReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.initialize.aborted/client",
        "${app}/lifecycle.initialize.aborted/server"})
    public void shouldAbortInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.canceled/client",
        "${app}/tools.list.canceled/server"})
    public void shouldListToolsThenAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.list/client",
        "${app}/prompts.list/server"})
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.list/client",
        "${app}/resources.list/server"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.get/client",
        "${app}/prompts.get/server"})
    public void shouldGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read/client",
        "${app}/resources.read/server"})
    public void shouldReadResource() throws Exception
    {
        k3po.finish();
    }
}
