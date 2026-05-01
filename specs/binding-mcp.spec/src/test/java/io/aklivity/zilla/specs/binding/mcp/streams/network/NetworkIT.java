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
package io.aklivity.zilla.specs.binding.mcp.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mcp/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/lifecycle.initialize/client",
        "${net}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.shutdown/client",
        "${net}/lifecycle.shutdown/server"})
    public void shouldShutdownLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.shutdown.requests/client",
        "${net}/lifecycle.shutdown.requests/server"})
    public void shouldShutdownLifecycleRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.timeout/client",
        "${net}/lifecycle.timeout/server"})
    public void shouldTimeoutLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.timeout.rejected/client",
        "${net}/lifecycle.timeout.rejected/server"})
    public void shouldTimeoutLifecycleRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.request.method.before.id/client",
        "${net}/reject.request.method.before.id/server"})
    public void shouldRejectRequestMethodBeforeId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.request.params.before.method/client",
        "${net}/reject.request.params.before.method/server"})
    public void shouldRejectRequestParamsBeforeMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.request.params.array/client",
        "${net}/reject.request.params.array/server"})
    public void shouldRejectRequestParamsWithArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.10k/client",
        "${net}/tools.call.10k/server"})
    public void shouldCallToolWith10kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.100k/client",
        "${net}/tools.call.100k/server"})
    public void shouldCallToolWith100kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.read.10k/client",
        "${net}/resources.read.10k/server"})
    public void shouldReadResourceWith10kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.read.100k/client",
        "${net}/resources.read.100k/server"})
    public void shouldReadResourceWith100kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.ping/client",
        "${net}/lifecycle.ping/server"})
    public void shouldPingLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call/client",
        "${net}/tools.call/server"})
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.list/client",
        "${net}/tools.list/server"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.list.aborted/client",
        "${net}/tools.list.aborted/server"})
    public void shouldAbortToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.aborted/client",
        "${net}/tools.call.aborted/server"})
    public void shouldAbortCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/prompts.list.aborted/client",
        "${net}/prompts.list.aborted/server"})
    public void shouldAbortListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/prompts.get.aborted/client",
        "${net}/prompts.get.aborted/server"})
    public void shouldAbortGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.list.aborted/client",
        "${net}/resources.list.aborted/server"})
    public void shouldAbortListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.read.aborted/client",
        "${net}/resources.read.aborted/server"})
    public void shouldAbortReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.initialize.aborted/client",
        "${net}/lifecycle.initialize.aborted/server"})
    public void shouldAbortInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.list.canceled/client",
        "${net}/tools.list.canceled/server"})
    public void shouldListToolsThenCancel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/prompts.list/client",
        "${net}/prompts.list/server"})
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.list/client",
        "${net}/resources.list/server"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/prompts.get/client",
        "${net}/prompts.get/server"})
    public void shouldGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.read/client",
        "${net}/resources.read/server"})
    public void shouldReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.events.open/client",
        "${net}/lifecycle.events.open/server"})
    public void shouldOpenLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.events.resume/client",
        "${net}/lifecycle.events.resume/server"})
    public void shouldResumeLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.events.unsupported/client",
        "${net}/lifecycle.events.unsupported/server"})
    public void shouldRejectLifecycleEventsUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.events.session.unknown/client",
        "${net}/lifecycle.events.session.unknown/server"})
    public void shouldRejectLifecycleEventsSessionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.events.session.missing/client",
        "${net}/lifecycle.events.session.missing/server"})
    public void shouldRejectLifecycleEventsSessionMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.method.not.allowed/client",
        "${net}/reject.method.not.allowed/server"})
    public void shouldRejectMethodNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.accept.unsupported/client",
        "${net}/reject.accept.unsupported/server"})
    public void shouldRejectAcceptUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.events.keepalive/client",
        "${net}/lifecycle.events.keepalive/server"})
    public void shouldKeepaliveLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.events.evict/client",
        "${net}/lifecycle.events.evict/server"})
    public void shouldEvictLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.shutdown.events/client",
        "${net}/lifecycle.shutdown.events/server"})
    public void shouldLifecycleShutdownEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.timeout.events/client",
        "${net}/lifecycle.timeout.events/server"})
    public void shouldLifecycleTimeoutEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.suspend.events/client",
        "${net}/lifecycle.suspend.events/server"})
    public void shouldLifecycleSuspendEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.suspended.events/client",
        "${net}/lifecycle.suspended.events/server"})
    public void shouldLifecycleSuspendedEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.with.progress.suspend/client",
        "${net}/tools.call.with.progress.suspend/server"})
    public void shouldCallToolWithProgressSuspend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.with.progress.suspended/client",
        "${net}/tools.call.with.progress.suspended/server"})
    public void shouldCallToolWithProgressSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.elicit.create/client",
        "${net}/tools.call.elicit.create/server"})
    public void shouldCallToolElicitCreate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/auth.callback/client",
        "${net}/auth.callback/server"})
    public void shouldReceiveAuthCallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.with.progress.resume/client",
        "${net}/tools.call.with.progress.resume/server"})
    public void shouldCallToolWithProgressResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.10k.with.progress/client",
        "${net}/tools.call.10k.with.progress/server"})
    public void shouldCallToolWith10kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/tools.call.100k.with.progress/client",
        "${net}/tools.call.100k.with.progress/server"})
    public void shouldCallToolWith100kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.read.10k.with.progress/client",
        "${net}/resources.read.10k.with.progress/server"})
    public void shouldReadResourceWith10kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/resources.read.100k.with.progress/client",
        "${net}/resources.read.100k.with.progress/server"})
    public void shouldReadResourceWith100kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/prompts.get.10k.with.progress/client",
        "${net}/prompts.get.10k.with.progress/server"})
    public void shouldGetPromptWith10kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/prompts.get.100k.with.progress/client",
        "${net}/prompts.get.100k.with.progress/server"})
    public void shouldGetPromptWith100kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }
}
