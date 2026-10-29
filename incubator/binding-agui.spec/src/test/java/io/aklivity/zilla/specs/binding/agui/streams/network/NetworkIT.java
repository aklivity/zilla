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
package io.aklivity.zilla.specs.binding.agui.streams.network;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/agui/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/run.text.message/client",
        "${net}/run.text.message/server"})
    public void shouldRunTextMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.text.chunks/client",
        "${net}/run.text.chunks/server"})
    public void shouldRunTextChunks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.tool.call/client",
        "${net}/run.tool.call/server"})
    public void shouldRunToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.state/client",
        "${net}/run.state/server"})
    public void shouldRunState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.messages.snapshot/client",
        "${net}/run.messages.snapshot/server"})
    public void shouldRunMessagesSnapshot() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.error/client",
        "${net}/run.error/server"})
    public void shouldRunError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.abort/client",
        "${net}/run.abort/server"})
    public void shouldAbortRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.reasoning/client",
        "${net}/run.reasoning/server"})
    public void shouldRunReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.reasoning.group/client",
        "${net}/run.reasoning.group/server"})
    public void shouldRunReasoningGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.reasoning.encrypted/client",
        "${net}/run.reasoning.encrypted/server"})
    public void shouldRunReasoningEncrypted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.request.ids.last/client",
        "${net}/run.request.ids.last/server"})
    public void shouldRunRequestWithIdsLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.request.100k/client",
        "${net}/run.request.100k/server"})
    public void shouldRunRequest100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.state.snapshot.100k/client",
        "${net}/run.state.snapshot.100k/server"})
    public void shouldRunStateSnapshot100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.authorized/client",
        "${net}/run.authorized/server"})
    public void shouldRunAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.unauthorized/client",
        "${net}/request.unauthorized/server"})
    public void shouldRejectUnauthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.unauthorized.invalid/client",
        "${net}/request.unauthorized.invalid/server"})
    public void shouldRejectUnauthorizedInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.malformed/client",
        "${net}/request.malformed/server"})
    public void shouldRejectMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.run.missing/client",
        "${net}/request.run.missing/server"})
    public void shouldRejectRunMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.text.100k/client",
        "${net}/run.text.100k/server"})
    public void shouldRunText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.text.meta/client",
        "${net}/run.text.meta/server"})
    public void shouldRunTextMeta() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.outcome.success/client",
        "${net}/run.outcome.success/server"})
    public void shouldRunOutcomeSuccess() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.outcome.interrupt/client",
        "${net}/run.outcome.interrupt/server"})
    public void shouldRunOutcomeInterrupt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.outcome.cancelled/client",
        "${net}/run.outcome.cancelled/server"})
    public void shouldRunOutcomeCancelled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.resume/client",
        "${net}/run.resume/server"})
    public void shouldRunResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.activity/client",
        "${net}/run.activity/server"})
    public void shouldRunActivity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.step/client",
        "${net}/run.step/server"})
    public void shouldRunStep() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.subagent/client",
        "${net}/run.subagent/server"})
    public void shouldRunSubagent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.subagent.suspended/client",
        "${net}/run.subagent.suspended/server"})
    public void shouldRunSubagentSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.subagent.suspended.interrupts/client",
        "${net}/run.subagent.suspended.interrupts/server"})
    public void shouldRunSubagentSuspendedInterrupts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.raw.custom/client",
        "${net}/run.raw.custom/server"})
    public void shouldRunRawCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.rejected/client",
        "${net}/run.rejected/server"})
    public void shouldRejectRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.unavailable/client",
        "${net}/run.unavailable/server"})
    public void shouldRejectRunUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/run.tool.result.parts/client",
        "${net}/run.tool.result.parts/server"})
    public void shouldRunToolResultParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.method.not.allowed/client",
        "${net}/request.method.not.allowed/server"})
    public void shouldRejectMethodNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.unsupported.media/client",
        "${net}/request.unsupported.media/server"})
    public void shouldRejectUnsupportedMedia() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.not.acceptable/client",
        "${net}/request.not.acceptable/server"})
    public void shouldRejectNotAcceptable() throws Exception
    {
        k3po.finish();
    }
}
