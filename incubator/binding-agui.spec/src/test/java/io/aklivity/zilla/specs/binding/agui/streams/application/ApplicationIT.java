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
package io.aklivity.zilla.specs.binding.agui.streams.application;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

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
        "${app}/run.text.chunks/client",
        "${app}/run.text.chunks/server"})
    public void shouldRunTextChunks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.tool.call/client",
        "${app}/run.tool.call/server"})
    public void shouldRunToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.state/client",
        "${app}/run.state/server"})
    public void shouldRunState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.messages.snapshot/client",
        "${app}/run.messages.snapshot/server"})
    public void shouldRunMessagesSnapshot() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.error/client",
        "${app}/run.error/server"})
    public void shouldRunError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.abort/client",
        "${app}/run.abort/server"})
    public void shouldAbortRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reasoning/client",
        "${app}/run.reasoning/server"})
    public void shouldRunReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reasoning.group/client",
        "${app}/run.reasoning.group/server"})
    public void shouldRunReasoningGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.reasoning.encrypted/client",
        "${app}/run.reasoning.encrypted/server"})
    public void shouldRunReasoningEncrypted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.request.ids.last/client",
        "${app}/run.request.ids.last/server"})
    public void shouldRunRequestWithIdsLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.request.100k/client",
        "${app}/run.request.100k/server"})
    public void shouldRunRequest100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.state.snapshot.100k/client",
        "${app}/run.state.snapshot.100k/server"})
    public void shouldRunStateSnapshot100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.authorized/client",
        "${app}/run.authorized/server"})
    public void shouldRunAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.100k/client",
        "${app}/run.text.100k/server"})
    public void shouldRunText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.meta/client",
        "${app}/run.text.meta/server"})
    public void shouldRunTextMeta() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.success/client",
        "${app}/run.outcome.success/server"})
    public void shouldRunOutcomeSuccess() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.interrupt/client",
        "${app}/run.outcome.interrupt/server"})
    public void shouldRunOutcomeInterrupt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.outcome.cancelled/client",
        "${app}/run.outcome.cancelled/server"})
    public void shouldRunOutcomeCancelled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.resume/client",
        "${app}/run.resume/server"})
    public void shouldRunResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.activity/client",
        "${app}/run.activity/server"})
    public void shouldRunActivity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.step/client",
        "${app}/run.step/server"})
    public void shouldRunStep() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.subagent/client",
        "${app}/run.subagent/server"})
    public void shouldRunSubagent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.subagent.suspended/client",
        "${app}/run.subagent.suspended/server"})
    public void shouldRunSubagentSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.subagent.suspended.interrupts/client",
        "${app}/run.subagent.suspended.interrupts/server"})
    public void shouldRunSubagentSuspendedInterrupts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.raw.custom/client",
        "${app}/run.raw.custom/server"})
    public void shouldRunRawCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.rejected/client",
        "${app}/run.rejected/server"})
    public void shouldRejectRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.tool.result.parts/client",
        "${app}/run.tool.result.parts/server"})
    public void shouldRunToolResultParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.unauthorized/client",
        "${app}/run.unauthorized/server"})
    public void shouldRunUnauthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.unavailable/client",
        "${app}/run.unavailable/server"})
    public void shouldRunUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.event.invalid/client",
        "${app}/run.event.invalid/server"})
    public void shouldRunEventInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.sequence.invalid/client",
        "${app}/run.sequence.invalid/server"})
    public void shouldRunSequenceInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.request.abort/client",
        "${app}/run.request.abort/server"})
    public void shouldRunRequestAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/run.text.deltas/client",
        "${app}/run.text.deltas/server"})
    public void shouldRunTextDeltas() throws Exception
    {
        k3po.finish();
    }
}
