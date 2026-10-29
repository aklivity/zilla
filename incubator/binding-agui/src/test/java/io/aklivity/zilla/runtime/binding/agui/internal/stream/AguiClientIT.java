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
package io.aklivity.zilla.runtime.binding.agui.internal.stream;

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

public class AguiClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/agui/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/agui/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.text.message/client",
        "${net}/run.text.message/server"})
    public void shouldRunTextMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.text.deltas/client",
        "${net}/run.text.chunks/server"})
    public void shouldRunTextDeltas() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.tool.call/client",
        "${net}/run.tool.call/server"})
    public void shouldRunToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.state/client",
        "${net}/run.state/server"})
    public void shouldRunState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.messages.snapshot/client",
        "${net}/run.messages.snapshot/server"})
    public void shouldRunMessagesSnapshot() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.error/client",
        "${net}/run.error/server"})
    public void shouldRunError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.abort/client",
        "${net}/run.abort/server"})
    public void shouldRunAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.reasoning/client",
        "${net}/run.reasoning/server"})
    public void shouldRunReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.reasoning.group/client",
        "${net}/run.reasoning.group/server"})
    public void shouldRunReasoningGroup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.reasoning.encrypted/client",
        "${net}/run.reasoning.encrypted/server"})
    public void shouldRunReasoningEncrypted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.request.100k/client",
        "${net}/run.request.100k/server"})
    public void shouldRunRequest100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.request.ids.last/client",
        "${net}/run.request.ids.last/server"})
    public void shouldRunRequestIdsLast() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.text.100k/client",
        "${net}/run.text.100k/server"})
    public void shouldRunText100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.state.snapshot.100k/client",
        "${net}/run.state.snapshot.100k/server"})
    public void shouldRunStateSnapshot100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.text.meta/client",
        "${net}/run.text.meta/server"})
    public void shouldRunTextMeta() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.outcome.success/client",
        "${net}/run.outcome.success/server"})
    public void shouldRunOutcomeSuccess() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.outcome.interrupt/client",
        "${net}/run.outcome.interrupt/server"})
    public void shouldRunOutcomeInterrupt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.outcome.cancelled/client",
        "${net}/run.outcome.cancelled/server"})
    public void shouldRunOutcomeCancelled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.resume/client",
        "${net}/run.resume/server"})
    public void shouldRunResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.activity/client",
        "${net}/run.activity/server"})
    public void shouldRunActivity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.step/client",
        "${net}/run.step/server"})
    public void shouldRunStep() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.subagent/client",
        "${net}/run.subagent/server"})
    public void shouldRunSubagent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.subagent.suspended/client",
        "${net}/run.subagent.suspended/server"})
    public void shouldRunSubagentSuspended() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.subagent.suspended.interrupts/client",
        "${net}/run.subagent.suspended.interrupts/server"})
    public void shouldRunSubagentSuspendedInterrupts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.raw.custom/client",
        "${net}/run.raw.custom/server"})
    public void shouldRunRawCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.tool.result.parts/client",
        "${net}/run.tool.result.parts/server"})
    public void shouldRunToolResultParts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.guarded.yaml")
    @ScriptProperty("authorization 1L")
    @Specification({
        "${app}/run.authorized/client",
        "${net}/run.authorized/server"})
    public void shouldRunAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.rejected/client",
        "${net}/response.rejected/server"})
    public void shouldRejectRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.unauthorized/client",
        "${net}/response.unauthorized/server"})
    public void shouldRejectUnauthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.unavailable/client",
        "${net}/response.unavailable/server"})
    public void shouldRejectUnavailable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.event.invalid/client",
        "${net}/response.event.malformed/server"})
    public void shouldFailMalformedEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.event.invalid/client",
        "${net}/response.event.unknown/server"})
    public void shouldFailUnknownEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.event.invalid/client",
        "${net}/response.truncated/server"})
    public void shouldFailTruncatedResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.sequence.invalid/client",
        "${net}/response.sequence.invalid/server"})
    public void shouldFailInvalidSequence() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.sequence.invalid/client",
        "${net}/response.unfinished/server"})
    public void shouldFailUnfinishedRun() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/run.request.abort/client",
        "${net}/run.request.abort/server"})
    public void shouldAbortRequest() throws Exception
    {
        k3po.finish();
    }
}
