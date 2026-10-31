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

public class AguiExtensionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/agui");

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
}
