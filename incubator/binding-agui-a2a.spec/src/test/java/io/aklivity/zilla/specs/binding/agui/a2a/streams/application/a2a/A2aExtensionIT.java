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

public class A2aExtensionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/extension.active/client",
        "${app}/extension.active/server"})
    public void shouldRunExtensionActive() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.echo.absent/client",
        "${app}/extension.echo.absent/server"})
    public void shouldRunExtensionEchoAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.versions/client",
        "${app}/extension.versions/server"})
    public void shouldRunExtensionVersions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.required.unsupported/client",
        "${app}/extension.required.unsupported/server"})
    public void shouldRunExtensionRequiredUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.cancel/client",
        "${app}/extension.cancel/server"})
    public void shouldRunExtensionCancel() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.state/client",
        "${app}/extension.tag.state/server"})
    public void shouldRunExtensionTagState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.activity/client",
        "${app}/extension.tag.activity/server"})
    public void shouldRunExtensionTagActivity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.reasoning/client",
        "${app}/extension.tag.reasoning/server"})
    public void shouldRunExtensionTagReasoning() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.step/client",
        "${app}/extension.tag.step/server"})
    public void shouldRunExtensionTagStep() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.tool.call/client",
        "${app}/extension.tag.tool.call/server"})
    public void shouldRunExtensionTagToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.custom/client",
        "${app}/extension.tag.custom/server"})
    public void shouldRunExtensionTagCustom() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.disabled/client",
        "${app}/extension.tag.disabled/server"})
    public void shouldRunExtensionTagDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.echo.absent/client",
        "${app}/extension.tag.echo.absent/server"})
    public void shouldRunExtensionTagEchoAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.dropped/client",
        "${app}/extension.tag.dropped/server"})
    public void shouldRunExtensionTagDropped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tag.malformed/client",
        "${app}/extension.tag.malformed/server"})
    public void shouldRunExtensionTagMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.messages.reset/client",
        "${app}/extension.messages.reset/server"})
    public void shouldRunExtensionMessagesReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.request.state/client",
        "${app}/extension.request.state/server"})
    public void shouldRunExtensionRequestState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.request.state.only/client",
        "${app}/extension.request.state.only/server"})
    public void shouldRunExtensionRequestStateOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tools.declared/client",
        "${app}/extension.tools.declared/server"})
    public void shouldRunExtensionToolsDeclared() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tools.disabled/client",
        "${app}/extension.tools.disabled/server"})
    public void shouldRunExtensionToolsDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tools.result/client",
        "${app}/extension.tools.result/server"})
    public void shouldRunExtensionToolsResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.tools.handle.unknown/client",
        "${app}/extension.tools.handle.unknown/server"})
    public void shouldRunExtensionToolsHandleUnknown() throws Exception
    {
        k3po.finish();
    }
}
