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
package io.aklivity.zilla.specs.binding.agui.a2a.streams.application.agui;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class AguiInterruptIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/agui");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/interrupt.auth.required/client",
        "${app}/interrupt.auth.required/server"})
    public void shouldInterruptAuthRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/interrupt.input.required/client",
        "${app}/interrupt.input.required/server"})
    public void shouldInterruptInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/interrupt.resume/client",
        "${app}/interrupt.resume/server"})
    public void shouldInterruptResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/interrupt.resume.cancelled/client",
        "${app}/interrupt.resume.cancelled/server"})
    public void shouldInterruptResumeCancelled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/interrupt.resume.empty/client",
        "${app}/interrupt.resume.empty/server"})
    public void shouldInterruptResumeEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/interrupt.resume.foreign/client",
        "${app}/interrupt.resume.foreign/server"})
    public void shouldInterruptResumeForeign() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/interrupt.resume.text/client",
        "${app}/interrupt.resume.text/server"})
    public void shouldInterruptResumeText() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/interrupt.resume.unknown/client",
        "${app}/interrupt.resume.unknown/server"})
    public void shouldInterruptResumeUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/restored.interrupt.resume/client",
        "${app}/restored.interrupt.resume/server"})
    public void shouldRestoreInterruptResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/restored.interrupt.resume.consumed/client",
        "${app}/restored.interrupt.resume.consumed/server"})
    public void shouldRestoreInterruptResumeConsumed() throws Exception
    {
        k3po.finish();
    }
}
