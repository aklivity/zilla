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

public class A2aOwnerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/owner.context.none/client",
        "${app}/owner.context.none/server"})
    public void shouldOwnerContextNone() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/owner.context.remembered/client",
        "${app}/owner.context.remembered/server"})
    public void shouldOwnerContextRemembered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/owner.context.reset/client",
        "${app}/owner.context.reset/server"})
    public void shouldOwnerContextReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/owner.context.reset.first.turn/client",
        "${app}/owner.context.reset.first.turn/server"})
    public void shouldOwnerContextResetFirstTurn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/owner.context.reset.interrupt/client",
        "${app}/owner.context.reset.interrupt/server"})
    public void shouldOwnerContextResetInterrupt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/owner.context.reset.stateless/client",
        "${app}/owner.context.reset.stateless/server"})
    public void shouldOwnerContextResetStateless() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/owner.redirect/client",
        "${app}/owner.redirect/server"})
    public void shouldOwnerRedirect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/restored.context/client",
        "${app}/restored.context/server"})
    public void shouldRestoreContext() throws Exception
    {
        k3po.finish();
    }
}
