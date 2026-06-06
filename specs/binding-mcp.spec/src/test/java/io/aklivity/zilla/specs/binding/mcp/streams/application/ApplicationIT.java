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
        "${app}/lifecycle.initialize.elicitation.url/client",
        "${app}/lifecycle.initialize.elicitation.url/server"})
    public void shouldInitializeLifecycleWithElicitationUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.initialize.elicitation.form/client",
        "${app}/lifecycle.initialize.elicitation.form/server"})
    public void shouldInitializeLifecycleWithElicitationForm() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.initialize.reject.bearer/client",
        "${app}/lifecycle.initialize.reject.bearer/server"})
    public void shouldRejectLifecycleInitializeOnUpstreamBearerChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.initialize.reject.bearer.resource.metadata/client",
        "${app}/lifecycle.initialize.reject.bearer.resource.metadata/server"})
    public void shouldRejectLifecycleInitializeOnUpstreamBearerChallengeResourceMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.reject.bearer/client",
        "${app}/tools.call.reject.bearer/server"})
    public void shouldRejectToolsCallOnUpstreamBearerChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cache.tools.call.reject.bearer/client",
        "${app}/cache.tools.call.reject.bearer/server"})
    public void shouldRejectToolsCallOnUpstreamBearerChallengeFromCache() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.initialize.alt.svc/client",
        "${app}/lifecycle.initialize.alt.svc/server"})
    public void shouldInitializeLifecycleAltSvc() throws Exception
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
        "${app}/lifecycle.timeout.rejected/client",
        "${app}/lifecycle.timeout.rejected/server"})
    public void shouldTimeoutLifecycleRejected() throws Exception
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
        "${app}/tools.call.resumable/client",
        "${app}/tools.call.resumable/server"})
    public void shouldCallToolWithUpstreamResumableFlush() throws Exception
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
        "${app}/tools.call.is.error/client",
        "${app}/tools.call.is.error/server"})
    public void shouldCallToolIsError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.timeout/client",
        "${app}/tools.call.timeout/server"})
    public void shouldCallToolWithTimeout() throws Exception
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

    @Test
    @Specification({
        "${app}/lifecycle.events.open/client",
        "${app}/lifecycle.events.open/server"})
    public void shouldOpenLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.resume/client",
        "${app}/lifecycle.events.resume/server"})
    public void shouldResumeLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.resume.duplicate/client",
        "${app}/lifecycle.events.resume.duplicate/server"})
    public void shouldRejectLifecycleEventsResumeDuplicate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.resume.reject.bearer/client",
        "${app}/lifecycle.events.resume.reject.bearer/server"})
    public void shouldRejectLifecycleEventsResumeOnUpstreamBearerChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.resume.reject.bearer.resource.metadata/client",
        "${app}/lifecycle.events.resume.reject.bearer.resource.metadata/server"})
    public void shouldRejectLifecycleEventsResumeOnUpstreamBearerChallengeResourceMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.unsupported/client",
        "${app}/lifecycle.events.unsupported/server"})
    public void shouldRejectLifecycleEventsUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.shutdown.events/client",
        "${app}/lifecycle.shutdown.events/server"})
    public void shouldLifecycleShutdownEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.timeout.events/client",
        "${app}/lifecycle.timeout.events/server"})
    public void shouldLifecycleTimeoutEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.notify.tools.list.changed/client",
        "${app}/lifecycle.notify.tools.list.changed/server"})
    public void shouldNotifyToolsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.elicit/client",
        "${app}/lifecycle.events.elicit/server"})
    public void shouldRelayRemoteElicitOnLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.elicit.reauthorize/client",
        "${app}/lifecycle.elicit.reauthorize/server"})
    public void shouldReauthorizeElicitCallbackOnLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.resume.aggregate/client",
        "${app}/lifecycle.events.resume.aggregate/server"})
    public void shouldResumeLifecycleEventsAggregate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.resume.partial/client",
        "${app}/lifecycle.events.resume.partial/server"})
    public void shouldResumeLifecycleEventsPartial() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.notify.prompts.list.changed/client",
        "${app}/lifecycle.notify.prompts.list.changed/server"})
    public void shouldNotifyPromptsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.notify.resources.list.changed/client",
        "${app}/lifecycle.notify.resources.list.changed/server"})
    public void shouldNotifyResourcesListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.suspend.events/client",
        "${app}/lifecycle.suspend.events/server"})
    public void shouldLifecycleSuspendEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.suspended.events/client",
        "${app}/lifecycle.suspended.events/server"})
    public void shouldLifecycleSuspendedEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.with.progress/client",
        "${app}/tools.call.with.progress/server"})
    public void shouldCallToolWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.with.progress.suspend/client",
        "${app}/tools.call.with.progress.suspend/server"})
    public void shouldCallToolWithProgressSuspend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.with.progress.suspended/client",
        "${app}/tools.call.with.progress.suspended/server"})
    public void shouldCallToolWithProgressSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.with.progress.resume/client",
        "${app}/tools.call.with.progress.resume/server"})
    public void shouldCallToolWithProgressResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.completed/client",
        "${app}/tools.call.elicit.completed/server"})
    public void shouldCallToolElicitCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.after.result/client",
        "${app}/tools.call.elicit.after.result/server"})
    public void shouldCallToolElicitAfterResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.completed.context/client",
        "${app}/tools.call.elicit.completed.context/server"})
    public void shouldCallToolElicitCompletedWithContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.elicit.toolkit/client",
        "${app}/lifecycle.elicit.toolkit/server"})
    public void shouldRouteLifecycleElicitToolkitCallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.elicit.completed/client",
        "${app}/lifecycle.elicit.completed/server"})
    public void shouldCompleteLifecycleElicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.passthrough/client",
        "${app}/tools.call.elicit.passthrough/server"})
    public void shouldCallToolElicitPassthrough() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.completed.proxied/client",
        "${app}/tools.call.elicit.completed.proxied/server"})
    public void shouldCallToolElicitCompletedProxied() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.declined/client",
        "${app}/tools.call.elicit.declined/server"})
    public void shouldCallToolElicitDeclined() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.declined.proxied/client",
        "${app}/tools.call.elicit.declined.proxied/server"})
    public void shouldCallToolElicitDeclinedProxied() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.timeout/client",
        "${app}/tools.call.elicit.timeout/server"})
    public void shouldCallToolElicitTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.reject/client",
        "${app}/tools.call.elicit.reject/server"})
    public void shouldRejectToolsCallElicitUrlRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.timeout.proxied/client",
        "${app}/tools.call.elicit.timeout.proxied/server"})
    public void shouldCallToolElicitTimeoutProxied() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.completed.guarded/client",
        "${app}/tools.call.elicit.completed.guarded/server"})
    public void shouldCallToolElicitCompletedGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.get.elicit.completed.guarded/client",
        "${app}/prompts.get.elicit.completed.guarded/server"})
    public void shouldGetPromptElicitCompletedGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.elicit.completed.guarded/client",
        "${app}/resources.read.elicit.completed.guarded/server"})
    public void shouldReadResourceElicitCompletedGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.declined.guarded/client",
        "${app}/tools.call.elicit.declined.guarded/server"})
    public void shouldCallToolElicitDeclinedGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.timeout.guarded/client",
        "${app}/tools.call.elicit.timeout.guarded/server"})
    public void shouldCallToolElicitTimeoutGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.elicit.reject.guarded/client",
        "${app}/tools.call.elicit.reject.guarded/server"})
    public void shouldCallToolElicitRejectGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.10k.with.progress/client",
        "${app}/tools.call.10k.with.progress/server"})
    public void shouldCallToolWith10kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.100k.with.progress/client",
        "${app}/tools.call.100k.with.progress/server"})
    public void shouldCallToolWith100kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.10k.with.progress/client",
        "${app}/resources.read.10k.with.progress/server"})
    public void shouldReadResourceWith10kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.100k.with.progress/client",
        "${app}/resources.read.100k.with.progress/server"})
    public void shouldReadResourceWith100kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.get.10k.with.progress/client",
        "${app}/prompts.get.10k.with.progress/server"})
    public void shouldGetPromptWith10kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.get.100k.with.progress/client",
        "${app}/prompts.get.100k.with.progress/server"})
    public void shouldGetPromptWith100kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.server.write.abort/client",
        "${app}/lifecycle.server.write.abort/server"})
    public void shouldLifecycleServerWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.server.write.close/client",
        "${app}/lifecycle.server.write.close/server"})
    public void shouldLifecycleServerWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.server.read.abort/client",
        "${app}/lifecycle.server.read.abort/server"})
    public void shouldLifecycleServerReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.client.write.abort/client",
        "${app}/lifecycle.client.write.abort/server"})
    public void shouldLifecycleClientWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.client.write.close/client",
        "${app}/lifecycle.client.write.close/server"})
    public void shouldLifecycleClientWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.client.read.abort/client",
        "${app}/lifecycle.client.read.abort/server"})
    public void shouldLifecycleClientReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.evict/client",
        "${app}/lifecycle.events.evict/server"})
    public void shouldEvictLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.events.keepalive/client",
        "${app}/lifecycle.events.keepalive/server"})
    public void shouldKeepaliveLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.ping/client",
        "${app}/lifecycle.ping/server"})
    public void shouldPingLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.get.toolkit/client",
        "${app}/prompts.get.toolkit/server"})
    public void shouldGetPromptWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.get.toolkit.prefixed/client",
        "${app}/prompts.get.toolkit.prefixed/server"})
    public void shouldGetPromptWithToolkitPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.list.toolkit/client",
        "${app}/prompts.list.toolkit/server"})
    public void shouldListPromptsWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.list.toolkit.prefixed/client",
        "${app}/prompts.list.toolkit.prefixed/server"})
    public void shouldListPromptsWithToolkitPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.list.toolkit/client",
        "${app}/resources.list.toolkit/server"})
    public void shouldListResourcesWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.list.toolkit.prefixed/client",
        "${app}/resources.list.toolkit.prefixed/server"})
    public void shouldListResourcesWithToolkitPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.toolkit/client",
        "${app}/resources.read.toolkit/server"})
    public void shouldReadResourceWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.read.toolkit.prefixed/client",
        "${app}/resources.read.toolkit.prefixed/server"})
    public void shouldReadResourceWithToolkitPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.toolkit/client",
        "${app}/tools.call.toolkit/server"})
    public void shouldCallToolWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.toolkit.prefixed/client",
        "${app}/tools.call.toolkit.prefixed/server"})
    public void shouldCallToolWithToolkitPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.toolkit.elicit/client",
        "${app}/tools.call.toolkit.elicit/server"})
    public void shouldCallToolWithToolkitElicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.toolkit.elicit.prefixed/client",
        "${app}/tools.call.toolkit.elicit.prefixed/server"})
    public void shouldCallToolWithToolkitElicitPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.toolkit.with.progress/client",
        "${app}/tools.call.toolkit.with.progress/server"})
    public void shouldCallToolWithToolkitWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.call.toolkit.with.progress.prefixed/client",
        "${app}/tools.call.toolkit.with.progress.prefixed/server"})
    public void shouldCallToolWithToolkitWithProgressPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.toolkit/client",
        "${app}/tools.list.toolkit/server"})
    public void shouldListToolsWithToolkit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.toolkit.prefixed/client",
        "${app}/tools.list.toolkit.prefixed/server"})
    public void shouldListToolsWithToolkitPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.list.toolkit.multi/client",
        "${app}/prompts.list.toolkit.multi/server"})
    public void shouldListPromptsWithToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/prompts.list.toolkit.multi.prefixed/client",
        "${app}/prompts.list.toolkit.multi.prefixed/server"})
    public void shouldListPromptsWithToolkitMultiPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.list.toolkit.multi/client",
        "${app}/resources.list.toolkit.multi/server"})
    public void shouldListResourcesWithToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/resources.list.toolkit.multi.prefixed/client",
        "${app}/resources.list.toolkit.multi.prefixed/server"})
    public void shouldListResourcesWithToolkitMultiPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.toolkit.multi/client",
        "${app}/tools.list.toolkit.multi/server"})
    public void shouldListToolsWithToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.toolkit.multi.prefixed/client",
        "${app}/tools.list.toolkit.multi.prefixed/server"})
    public void shouldListToolsWithToolkitMultiPrefixed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.partial.toolkit.multi/client",
        "${app}/tools.list.partial.toolkit.multi/server"})
    public void shouldListToolsWithPartialToolkitMulti() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/tools.list.partial.toolkit.multi.prefixed/client",
        "${app}/tools.list.partial.toolkit.multi.prefixed/server"})
    public void shouldListToolsWithPartialToolkitMultiPrefixed() throws Exception
    {
        k3po.finish();
    }
}
