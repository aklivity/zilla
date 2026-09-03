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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.ENGINE_DETACH_ON_CLOSE_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.ENGINE_SYNTHETIC_ABORT_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_ALT_SVC_ENABLED_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_ELICITATION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_INACTIVITY_TIMEOUT_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SERVER_NAME_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SERVER_VERSION_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.binding.mcp.internal.McpConfigurationTest.MCP_SSE_KEEPALIVE_INTERVAL_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;
import static org.junit.rules.RuleChain.outerRule;

import java.util.concurrent.CountDownLatch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;
import io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuardHandler;

public class McpServerIT
{
    private static final String ENGINE_WORKERS_NAME = "zilla.engine.workers";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mcp/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config")
        .configure(MCP_SESSION_ID_NAME, "%s::sessionId".formatted(McpServerIT.class.getName()))
        .configure(MCP_ELICITATION_ID_NAME, "%s::elicitationId".formatted(McpServerIT.class.getName()))
        .configure(MCP_SERVER_NAME_NAME, "zilla")
        .configure(MCP_SERVER_VERSION_NAME, "1.0")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.elicitation.url/client",
        "${app}/lifecycle.initialize.elicitation.url/server"})
    public void shouldInitializeLifecycleWithElicitationUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.elicitation.form/client",
        "${app}/lifecycle.initialize.elicitation.form/server"})
    public void shouldInitializeLifecycleWithElicitationForm() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.negotiate/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldNegotiateLifecycleInitializeVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.version.unsupported/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycleWithUnsupportedVersionFallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.reject.bearer/client",
        "${app}/lifecycle.initialize.reject.bearer/server"})
    public void shouldRejectLifecycleInitializeWithBearerChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/lifecycle.initialize.anonymous/client",
        "${app}/lifecycle.initialize.anonymous/server"})
    public void shouldInitializeLifecycleAnonymouslyWithMissingBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/lifecycle.initialize.reject.bearer.invalid/client"})
    public void shouldRejectLifecycleInitializeWithInvalidBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @Specification({
        "${net}/lifecycle.initialize.guarded/client",
        "${app}/lifecycle.initialize.guarded/server"})
    public void shouldInitializeLifecycleWithGuardedBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.max.sessions.yaml")
    @Specification({
        "${net}/lifecycle.initialize.guarded.reauthorize.after.close/client",
        "${app}/lifecycle.initialize.guarded.reauthorize.after.close/server"})
    public void shouldReauthorizeGuardedBearerAfterClose() throws Exception
    {
        CountDownLatch deauthorized = new CountDownLatch(1);
        TestGuardHandler.onDeauthorized = deauthorized::countDown;
        try
        {
            k3po.start();
            assertTrue(deauthorized.await(5, SECONDS));
            k3po.notifyBarrier("SESSION_RELEASED");
            k3po.finish();
        }
        finally
        {
            TestGuardHandler.onDeauthorized = null;
        }
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.reject.bearer.resource.metadata/client",
        "${app}/lifecycle.initialize.reject.bearer.resource.metadata/server"})
    public void shouldRejectLifecycleInitializeWithBearerChallengeResourceMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.reject.bearer/client",
        "${app}/tools.call.reject.bearer/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldRejectToolsCallWithBearerChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.reject.error/client",
        "${app}/tools.call.reject.error/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldRejectToolsCallWithError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.alt.svc/client",
        "${app}/lifecycle.initialize.alt.svc/server"})
    @Configure(name = MCP_ALT_SVC_ENABLED_NAME, value = "true")
    public void shouldInitializeLifecycleAltSvc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.ping/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldPingLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown/client",
        "${app}/lifecycle.shutdown/server"})
    public void shouldShutdownLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown.requests/client",
        "${app}/lifecycle.shutdown.requests/server"})
    @ScriptProperty("affinity \"0000003f\"")
    @Configure(name = ENGINE_SYNTHETIC_ABORT_NAME, value = "false")
    @Configure(name = ENGINE_DETACH_ON_CLOSE_NAME, value = "false")
    public void shouldShutdownLifecycleRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.timeout/client",
        "${app}/lifecycle.timeout/server"})
    @Configure(name = MCP_INACTIVITY_TIMEOUT_NAME, value = "PT1S")
    public void shouldTimeoutLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.id.last/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycleWithIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list.id.last/client",
        "${app}/tools.list/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldListToolsWithIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.ping.id.last/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldPingLifecycleWithIdLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.params.before.method/client",
        "${app}/reject.request.params.before.method/server"})
    public void shouldRejectRequestParamsBeforeMethod() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.params.array/client",
        "${app}/reject.request.params.array/server"})
    public void shouldRejectRequestParamsWithArray() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.tools.call.without.content.length/client",
        "${app}/reject.tools.call.without.content.length/server"})
    public void shouldRejectToolsCallWithoutContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.10k/client",
        "${app}/tools.call.10k/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWith10kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.100k/client",
        "${app}/tools.call.100k/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWith100kParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.10k/client",
        "${app}/resources.read.10k/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldReadResourceWith10kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.100k/client",
        "${app}/resources.read.100k/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldReadResourceWith100kContents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call/client",
        "${app}/tools.call/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.timeout/client",
        "${app}/tools.call.timeout/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWithTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call/client",
        "${app}/tools.call.resumable/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWithUpstreamResumableFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.elicit.completed/client",
        "${app}/tools.call.elicit.completed/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolElicitCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.elicit.after.result/client",
        "${app}/tools.call.elicit.after.result/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolElicitAfterResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.elicit.deferred/client",
        "${app}/tools.call.elicit.deferred/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolElicitDeferred() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.elicit.completed.context/client",
        "${app}/tools.call.elicit.completed.context/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolElicitCompletedWithContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/lifecycle.elicit.toolkit/client",
        "${app}/lifecycle.elicit.toolkit/server"})
    public void shouldRouteLifecycleElicitToolkitCallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/lifecycle.elicit.completed/client",
        "${app}/lifecycle.elicit.completed/server"})
    public void shouldCompleteLifecycleElicit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/lifecycle.elicit.toolkit.replay/client",
        "${app}/lifecycle.elicit.toolkit.replay/server"})
    public void shouldRejectReplayedLifecycleElicitToolkitCallback() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.elicit.passthrough/client",
        "${app}/tools.call.elicit.passthrough/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolElicitPassthrough() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.elicit.declined/client",
        "${app}/tools.call.elicit.declined/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolElicitDeclined() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.timeout.yaml")
    @Specification({
        "${net}/tools.call.elicit.timeout/client",
        "${app}/tools.call.elicit.timeout/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolElicitTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.elicit.reject/client",
        "${app}/tools.call.elicit.reject/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldRejectToolsCallElicitUrlRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list/client",
        "${app}/tools.list/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list.security.schemes/client",
        "${app}/tools.list.security.schemes/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldListToolsExcludingSecuritySchemes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list.aborted/client",
        "${app}/tools.list.aborted/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldAbortToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.aborted/client",
        "${app}/tools.call.aborted/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldAbortCallTool() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.list.aborted/client",
        "${app}/prompts.list.aborted/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldAbortListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get.aborted/client",
        "${app}/prompts.get.aborted/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldAbortGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.list.aborted/client",
        "${app}/resources.list.aborted/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldAbortListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.templates.list.aborted/client",
        "${app}/resources.templates.list.aborted/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldAbortListResourcesTemplates() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.aborted/client",
        "${app}/resources.read.aborted/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldAbortReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.list.canceled/client",
        "${app}/tools.list.canceled/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldListToolsThenCancel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/notifications.cancelled.unknown/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldAcceptCancelUnknownRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/notifications.cancelled.missing.request.id/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldAcceptCancelMissingRequestId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.list/client",
        "${app}/prompts.list/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.list/client",
        "${app}/resources.list/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.templates.list/client",
        "${app}/resources.templates.list/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldListResourcesTemplates() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get/client",
        "${app}/prompts.get/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read/client",
        "${app}/resources.read/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldReadResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.subscribe/client",
        "${app}/resources.subscribe/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldSubscribeToResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.unsubscribe/client",
        "${app}/resources.unsubscribe/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldUnsubscribeFromResource() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.open/client",
        "${app}/lifecycle.events.open/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldOpenLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.suspend.events/client",
        "${app}/lifecycle.suspend.events/server"})
    public void shouldSuspendLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @ScriptProperty("authorization 1L")
    @Specification({
        "${net}/lifecycle.events.resume/client",
        "${app}/lifecycle.events.resume/server"})
    public void shouldResumeLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guarded.yaml")
    @ScriptProperty("authorization 1L")
    @Specification({
        "${net}/lifecycle.events.resume/client",
        "${app}/lifecycle.events.resume/server"})
    public void shouldDeauthorizeGuardSessionOnResumeClose() throws Exception
    {
        // three guarded HTTP requests share this scenario (POST initialize, POST
        // notifications/initialized, GET/SSE reconnect), each minting its own guard
        // session on open and releasing it on close -- the GET/SSE reconnect leg is
        // the one McpEventStream itself is responsible for releasing
        CountDownLatch deauthorized = new CountDownLatch(3);
        TestGuardHandler.onDeauthorized = deauthorized::countDown;
        try
        {
            k3po.finish();
            assertTrue(deauthorized.await(5, SECONDS));
        }
        finally
        {
            TestGuardHandler.onDeauthorized = null;
        }
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.resume.reject.bearer/client",
        "${app}/lifecycle.events.resume.reject.bearer/server"})
    public void shouldRejectLifecycleEventsResumeWithBearerChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.resume.reject.bearer.resource.metadata/client",
        "${app}/lifecycle.events.resume.reject.bearer.resource.metadata/server"})
    public void shouldRejectLifecycleEventsResumeWithBearerChallengeResourceMetadata() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.session.unknown/client"})
    public void shouldRejectLifecycleEventsSessionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.session.missing/client"})
    public void shouldRejectLifecycleEventsSessionMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown.session.unknown/client"})
    public void shouldRejectLifecycleShutdownSessionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown.session.missing/client"})
    public void shouldRejectLifecycleShutdownSessionMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.method.not.allowed/client"})
    public void shouldRejectMethodNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.accept.unsupported/client"})
    public void shouldRejectAcceptUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.method.unknown/client",
        "${app}/reject.request.method.unknown/server"})
    public void shouldRejectRequestMethodUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.session.unknown/client"})
    public void shouldRejectRequestSessionUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.request.session.missing/client"})
    public void shouldRejectRequestSessionMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.initialize.reject.capabilities.invalid/client"})
    public void shouldRejectLifecycleInitializeCapabilitiesInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.keepalive/client",
        "${app}/lifecycle.events.keepalive/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT0.5S")
    public void shouldKeepaliveLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.events.evict/client",
        "${app}/lifecycle.events.evict/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldEvictLifecycleEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.shutdown.events/client",
        "${app}/lifecycle.shutdown.events/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleShutdownEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.timeout.events/client",
        "${app}/lifecycle.timeout.events/server"})
    @Configure(name = MCP_INACTIVITY_TIMEOUT_NAME, value = "PT1S")
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleTimeoutEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.timeout.events.resources.updated/client",
        "${app}/lifecycle.timeout.events.resources.updated/server"})
    @Configure(name = MCP_INACTIVITY_TIMEOUT_NAME, value = "PT3S")
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT2S")
    public void shouldLifecycleTimeoutEventsResourcesUpdated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.notify.tools.list.changed/client",
        "${app}/lifecycle.notify.tools.list.changed/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldNotifyToolsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.notify.prompts.list.changed/client",
        "${app}/lifecycle.notify.prompts.list.changed/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldNotifyPromptsListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.notify.resources.list.changed/client",
        "${app}/lifecycle.notify.resources.list.changed/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldNotifyResourcesListChanged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.notify.resources.updated/client",
        "${app}/lifecycle.notify.resources.updated/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldNotifyResourcesUpdated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress/client",
        "${app}/tools.call.with.progress/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress.suspend/client",
        "${app}/tools.call.with.progress.suspend/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWithProgressSuspend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress.suspended/client",
        "${app}/tools.call.with.progress.suspended/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWithProgressSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.with.progress.resume/client",
        "${app}/tools.call.with.progress.resume/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWithProgressResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.suspend.events/client",
        "${app}/lifecycle.suspend.events/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleSuspendEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.suspended.events/client",
        "${app}/lifecycle.suspended.events/server"})
    @Configure(name = MCP_SSE_KEEPALIVE_INTERVAL_NAME, value = "PT30S")
    public void shouldLifecycleSuspendedEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.10k.with.progress/client",
        "${app}/tools.call.10k.with.progress/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWith10kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/tools.call.100k.with.progress/client",
        "${app}/tools.call.100k.with.progress/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldCallToolWith100kParamsWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.10k.with.progress/client",
        "${app}/resources.read.10k.with.progress/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldReadResourceWith10kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/resources.read.100k.with.progress/client",
        "${app}/resources.read.100k.with.progress/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldReadResourceWith100kContentWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get.10k.with.progress/client",
        "${app}/prompts.get.10k.with.progress/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldGetPromptWith10kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/prompts.get.100k.with.progress/client",
        "${app}/prompts.get.100k.with.progress/server"})
    @ScriptProperty("affinity \"0000003f\"")
    public void shouldGetPromptWith100kMessageWithProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/reject.auth.callback.unknown.elicitation/client"})
    public void shouldRejectAuthCallbackUnknownElicitation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/lifecycle.redirect.session/client"})
    @Configure(name = ENGINE_WORKERS_NAME, value = "2")
    public void shouldRedirectLifecycleForRemoteSession() throws Exception
    {
        k3po.finish();
    }

    public static String sessionId(
        long affinity)
    {
        assert affinity == 0L;
        return "5ca1ab1e-c0de-4a11-b0a7-000100000000";
    }

    public static String elicitationId()
    {
        return "elicit-1";
    }
}
