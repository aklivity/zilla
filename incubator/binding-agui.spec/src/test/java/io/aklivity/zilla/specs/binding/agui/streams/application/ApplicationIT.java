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
}
