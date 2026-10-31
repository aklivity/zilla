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
package io.aklivity.zilla.specs.binding.agui.a2a.streams.application.a2a;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class A2aRunIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/run.route.agent.passthrough/client",
        "${app}/run.route.agent.passthrough/server"})
    public void shouldRunRouteAgentPassthrough() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.route.agent.rewrite/client",
        "${app}/run.route.agent.rewrite/server"})
    public void shouldRunRouteAgentRewrite() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.route.agent.null/client",
        "${app}/run.route.agent.null/server"})
    public void shouldRunRouteAgentNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.chunks/client",
        "${app}/run.text.chunks/server"})
    public void shouldRunTextChunks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.replace/client",
        "${app}/run.text.replace/server"})
    public void shouldRunTextReplace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.artifacts/client",
        "${app}/run.text.artifacts/server"})
    public void shouldRunTextArtifacts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.message/client",
        "${app}/run.text.message/server"})
    public void shouldRunTextMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.task.artifacts/client",
        "${app}/run.text.task.artifacts/server"})
    public void shouldRunTextTaskArtifacts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.custom/client",
        "${app}/run.text.custom/server"})
    public void shouldRunTextCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.send.task/client",
        "${app}/run.send.task/server"})
    public void shouldRunSendTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.send.message/client",
        "${app}/run.send.message/server"})
    public void shouldRunSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.completed/client",
        "${app}/run.outcome.completed/server"})
    public void shouldRunOutcomeCompleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.failed/client",
        "${app}/run.outcome.failed/server"})
    public void shouldRunOutcomeFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.failed.default/client",
        "${app}/run.outcome.failed.default/server"})
    public void shouldRunOutcomeFailedDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.rejected/client",
        "${app}/run.outcome.rejected/server"})
    public void shouldRunOutcomeRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.rejected.default/client",
        "${app}/run.outcome.rejected.default/server"})
    public void shouldRunOutcomeRejectedDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.canceled/client",
        "${app}/run.outcome.canceled/server"})
    public void shouldRunOutcomeCanceled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.unspecified/client",
        "${app}/run.outcome.unspecified/server"})
    public void shouldRunOutcomeUnspecified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.incomplete/client",
        "${app}/run.outcome.incomplete/server"})
    public void shouldRunOutcomeIncomplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.aborted/client",
        "${app}/run.outcome.aborted/server"})
    public void shouldRunOutcomeAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.end.error/client",
        "${app}/run.outcome.end.error/server"})
    public void shouldRunOutcomeEndError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.status.completed.message/client",
        "${app}/run.status.completed.message/server"})
    public void shouldRunStatusCompletedMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.status.completed.duplicate/client",
        "${app}/run.status.completed.duplicate/server"})
    public void shouldRunStatusCompletedDuplicate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.status.task.resend/client",
        "${app}/run.status.task.resend/server"})
    public void shouldRunStatusTaskResend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.status.input.required/client",
        "${app}/run.status.input.required/server"})
    public void shouldRunStatusInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.status.working.activity/client",
        "${app}/run.status.working.activity/server"})
    public void shouldRunStatusWorkingActivity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reset.bearer.invalid.token/client",
        "${app}/run.reset.bearer.invalid.token/server"})
    public void shouldRunResetBearerInvalidToken() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reset.bearer.invalid.request/client",
        "${app}/run.reset.bearer.invalid.request/server"})
    public void shouldRunResetBearerInvalidRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reset.bearer.insufficient.scope/client",
        "${app}/run.reset.bearer.insufficient.scope/server"})
    public void shouldRunResetBearerInsufficientScope() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reset.error.not.found/client",
        "${app}/run.reset.error.not.found/server"})
    public void shouldRunResetErrorNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reset.error.invalid.params/client",
        "${app}/run.reset.error.invalid.params/server"})
    public void shouldRunResetErrorInvalidParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reset.error.unavailable/client",
        "${app}/run.reset.error.unavailable/server"})
    public void shouldRunResetErrorUnavailable() throws Exception
    {
        k3po.finish();
    }
}
