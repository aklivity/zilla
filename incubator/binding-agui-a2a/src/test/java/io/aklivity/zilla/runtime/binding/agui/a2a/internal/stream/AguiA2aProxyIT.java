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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfigurationTest.AGUI_A2A_HANDLE_ID_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

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

public class AguiA2aProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("agui", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/agui")
        .addScriptRoot("agent", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(AGUI_A2A_HANDLE_ID_NAME, "%s::handleId".formatted(AguiA2aProxyIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/runtime/binding/agui/a2a/internal/stream")
        .external("a2a0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    public static String handleId()
    {
        return "h-1";
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.auth.required/client",
        "${agent}/interrupt.auth.required/server"})
    public void shouldInterruptAuthRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.input.required/client",
        "${agent}/interrupt.input.required/server"})
    public void shouldInterruptInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.resume/client",
        "${agent}/interrupt.resume/server"})
    public void shouldInterruptResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.resume.cancelled/client",
        "${agent}/interrupt.resume.cancelled/server"})
    public void shouldInterruptResumeCancelled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.resume.empty/client",
        "${agent}/interrupt.resume.empty/server"})
    public void shouldInterruptResumeEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.resume.foreign/client",
        "${agent}/interrupt.resume.foreign/server"})
    public void shouldInterruptResumeForeign() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.resume.text/client",
        "${agent}/interrupt.resume.text/server"})
    public void shouldInterruptResumeText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${agui}/interrupt.resume.unknown/client"})
    public void shouldInterruptResumeUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.context.none/client",
        "${agent}/owner.context.none/server"})
    public void shouldOwnerContextNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.context.remembered/client",
        "${agent}/owner.context.remembered/server"})
    public void shouldOwnerContextRemembered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.context.reset/client",
        "${agent}/owner.context.reset/server"})
    public void shouldOwnerContextReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.context.reset.first.turn/client",
        "${agent}/owner.context.reset.first.turn/server"})
    public void shouldOwnerContextResetFirstTurn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.context.reset.interrupt/client",
        "${agent}/owner.context.reset.interrupt/server"})
    public void shouldOwnerContextResetInterrupt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.context.reset.stateless/client",
        "${agent}/owner.context.reset.stateless/server"})
    public void shouldOwnerContextResetStateless() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.forwarded.props/client",
        "${agent}/request.forwarded.props/server"})
    public void shouldRequestForwardedProps() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.forwarded.props.value/client",
        "${agent}/request.forwarded.props.value/server"})
    public void shouldRequestForwardedPropsValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.long.history/client",
        "${agent}/request.long.history/server"})
    public void shouldRequestLongHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${agui}/request.no.user.message/client"})
    public void shouldRequestNoUserMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.part.binary/client",
        "${agent}/request.part.binary/server"})
    public void shouldRequestPartBinary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.part.file.dropped/client",
        "${agent}/request.part.file.dropped/server"})
    public void shouldRequestPartFileDropped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.part.url/client",
        "${agent}/request.part.url/server"})
    public void shouldRequestPartUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${agui}/request.reserved.key/client"})
    public void shouldRequestReservedKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.state.context/client",
        "${agent}/request.state.context/server"})
    public void shouldRequestStateContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.state.context.omitted/client",
        "${agent}/request.state.context.omitted/server"})
    public void shouldRequestStateContextOmitted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.state.context.wildcard/client",
        "${agent}/request.state.context.wildcard/server"})
    public void shouldRequestStateContextWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${agui}/request.state.too.large/client"})
    public void shouldRequestStateTooLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.system.developer.dropped/client",
        "${agent}/request.system.developer.dropped/server"})
    public void shouldRequestSystemDeveloperDropped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${agui}/request.too.large/client"})
    public void shouldRequestTooLarge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/request.trailing.user/client",
        "${agent}/request.trailing.user/server"})
    public void shouldRequestTrailingUser() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.aborted/client",
        "${agent}/run.outcome.aborted/server"})
    public void shouldRunOutcomeAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.canceled/client",
        "${agent}/run.outcome.canceled/server"})
    public void shouldRunOutcomeCanceled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.completed/client",
        "${agent}/run.outcome.completed/server"})
    public void shouldRunOutcomeCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.end.error/client",
        "${agent}/run.outcome.end.error/server"})
    public void shouldRunOutcomeEndError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.failed/client",
        "${agent}/run.outcome.failed/server"})
    public void shouldRunOutcomeFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.failed.default/client",
        "${agent}/run.outcome.failed.default/server"})
    public void shouldRunOutcomeFailedDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.incomplete/client",
        "${agent}/run.outcome.incomplete/server"})
    public void shouldRunOutcomeIncomplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.rejected/client",
        "${agent}/run.outcome.rejected/server"})
    public void shouldRunOutcomeRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.rejected.default/client",
        "${agent}/run.outcome.rejected.default/server"})
    public void shouldRunOutcomeRejectedDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.outcome.unspecified/client",
        "${agent}/run.outcome.unspecified/server"})
    public void shouldRunOutcomeUnspecified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.reset.bearer.insufficient.scope/client",
        "${agent}/run.reset.bearer.insufficient.scope/server"})
    public void shouldRunResetBearerInsufficientScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.reset.bearer.invalid.request/client",
        "${agent}/run.reset.bearer.invalid.request/server"})
    public void shouldRunResetBearerInvalidRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.reset.bearer.invalid.token/client",
        "${agent}/run.reset.bearer.invalid.token/server"})
    public void shouldRunResetBearerInvalidToken() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.reset.error.invalid.params/client",
        "${agent}/run.reset.error.invalid.params/server"})
    public void shouldRunResetErrorInvalidParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.reset.error.not.found/client",
        "${agent}/run.reset.error.not.found/server"})
    public void shouldRunResetErrorNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.reset.error.unavailable/client",
        "${agent}/run.reset.error.unavailable/server"})
    public void shouldRunResetErrorUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.route.agent.null/client",
        "${agent}/run.route.agent.null/server"})
    public void shouldRunRouteAgentNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.route.agent.passthrough/client",
        "${agent}/run.route.agent.passthrough/server"})
    public void shouldRunRouteAgentPassthrough() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.route.agent.rewrite.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.route.agent.rewrite/client",
        "${agent}/run.route.agent.rewrite/server"})
    public void shouldRunRouteAgentRewrite() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.send.message/client",
        "${agent}/run.send.message/server"})
    public void shouldRunSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.send.task/client",
        "${agent}/run.send.task/server"})
    public void shouldRunSendTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.status.completed.duplicate/client",
        "${agent}/run.status.completed.duplicate/server"})
    public void shouldRunStatusCompletedDuplicate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.status.completed.message/client",
        "${agent}/run.status.completed.message/server"})
    public void shouldRunStatusCompletedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.status.input.required/client",
        "${agent}/run.status.input.required/server"})
    public void shouldRunStatusInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.status.task.resend/client",
        "${agent}/run.status.task.resend/server"})
    public void shouldRunStatusTaskResend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.status.working.activity/client",
        "${agent}/run.status.working.activity/server"})
    public void shouldRunStatusWorkingActivity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.text.artifacts/client",
        "${agent}/run.text.artifacts/server"})
    public void shouldRunTextArtifacts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.text.chunks/client",
        "${agent}/run.text.chunks/server"})
    public void shouldRunTextChunks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.text.custom/client",
        "${agent}/run.text.custom/server"})
    public void shouldRunTextCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.text.message/client",
        "${agent}/run.text.message/server"})
    public void shouldRunTextMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.text.replace/client",
        "${agent}/run.text.replace/server"})
    public void shouldRunTextReplace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/run.text.task.artifacts/client",
        "${agent}/run.text.task.artifacts/server"})
    public void shouldRunTextTaskArtifacts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/card.failure/client",
        "${agent}/card.failure/server"})
    public void shouldCardFailure() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/card.input.modes/client",
        "${agent}/card.input.modes/server"})
    public void shouldCardInputModes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/card.input.modes.wildcard/client",
        "${agent}/card.input.modes.wildcard/server"})
    public void shouldCardInputModesWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/card.streaming/client",
        "${agent}/card.streaming/server"})
    public void shouldCardStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/card.streaming.absent/client",
        "${agent}/card.streaming.absent/server"})
    public void shouldCardStreamingAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/card.streaming.disabled/client",
        "${agent}/card.streaming.disabled/server"})
    public void shouldCardStreamingDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/card.unsupported.media/client",
        "${agent}/card.unsupported.media/server"})
    public void shouldCardUnsupportedMedia() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/cancel.abort/client",
        "${agent}/cancel.abort/server"})
    public void shouldCancelAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/cancel.abort.no.task/client",
        "${agent}/cancel.abort.no.task/server"})
    public void shouldCancelAbortNoTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/cancel.abort.not.cancelable/client",
        "${agent}/cancel.abort.not.cancelable/server"})
    public void shouldCancelAbortNotCancelable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/cancel.canceled/client",
        "${agent}/cancel.canceled/server"})
    public void shouldCancelCanceled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.active/client",
        "${agent}/extension.active/server"})
    public void shouldRunExtensionActive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.echo.absent/client",
        "${agent}/extension.echo.absent/server"})
    public void shouldRunExtensionEchoAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.versions/client",
        "${agent}/extension.versions/server"})
    public void shouldRunExtensionVersions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.required.unsupported/client",
        "${agent}/extension.required.unsupported/server"})
    public void shouldRunExtensionRequiredUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.cancel/client",
        "${agent}/extension.cancel/server"})
    public void shouldRunExtensionCancel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.state/client",
        "${agent}/extension.tag.state/server"})
    public void shouldRunExtensionTagState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.activity/client",
        "${agent}/extension.tag.activity/server"})
    public void shouldRunExtensionTagActivity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.reasoning/client",
        "${agent}/extension.tag.reasoning/server"})
    public void shouldRunExtensionTagReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.step/client",
        "${agent}/extension.tag.step/server"})
    public void shouldRunExtensionTagStep() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.tool.call/client",
        "${agent}/extension.tag.tool.call/server"})
    public void shouldRunExtensionTagToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.custom/client",
        "${agent}/extension.tag.custom/server"})
    public void shouldRunExtensionTagCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.disabled/client",
        "${agent}/extension.tag.disabled/server"})
    public void shouldRunExtensionTagDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.echo.absent/client",
        "${agent}/extension.tag.echo.absent/server"})
    public void shouldRunExtensionTagEchoAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.dropped/client",
        "${agent}/extension.tag.dropped/server"})
    public void shouldRunExtensionTagDropped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.catch.all.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.tag.malformed/client",
        "${agent}/extension.tag.malformed/server"})
    public void shouldRunExtensionTagMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.messages.reset/client",
        "${agent}/extension.messages.reset/server"})
    public void shouldRunExtensionMessagesReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.request.state/client",
        "${agent}/extension.request.state/server"})
    public void shouldRunExtensionRequestState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/extension.request.state.only/client",
        "${agent}/extension.request.state.only/server"})
    public void shouldRunExtensionRequestStateOnly() throws Exception
    {
        k3po.finish();
    }
}
