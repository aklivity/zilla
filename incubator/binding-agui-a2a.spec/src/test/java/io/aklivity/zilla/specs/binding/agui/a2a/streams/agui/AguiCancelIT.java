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
package io.aklivity.zilla.specs.binding.agui.a2a.streams.agui;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class AguiCancelIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/agui");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/cancel.abort/client",
        "${app}/cancel.abort/server"})
    public void shouldCancelAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cancel.abort.no.task/client",
        "${app}/cancel.abort.no.task/server"})
    public void shouldCancelAbortNoTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cancel.abort.not.cancelable/client",
        "${app}/cancel.abort.not.cancelable/server"})
    public void shouldCancelAbortNotCancelable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cancel.canceled/client",
        "${app}/cancel.canceled/server"})
    public void shouldCancelCanceled() throws Exception
    {
        k3po.finish();
    }
}
