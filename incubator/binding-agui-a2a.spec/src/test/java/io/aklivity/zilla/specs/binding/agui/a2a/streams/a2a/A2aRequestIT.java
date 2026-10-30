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
package io.aklivity.zilla.specs.binding.agui.a2a.streams.a2a;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class A2aRequestIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/request.forwarded.props/client",
        "${app}/request.forwarded.props/server"})
    public void shouldRequestForwardedProps() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.forwarded.props.value/client",
        "${app}/request.forwarded.props.value/server"})
    public void shouldRequestForwardedPropsValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.long.history/client",
        "${app}/request.long.history/server"})
    public void shouldRequestLongHistory() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.part.binary/client",
        "${app}/request.part.binary/server"})
    public void shouldRequestPartBinary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.part.file.dropped/client",
        "${app}/request.part.file.dropped/server"})
    public void shouldRequestPartFileDropped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.part.url/client",
        "${app}/request.part.url/server"})
    public void shouldRequestPartUrl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.state.context/client",
        "${app}/request.state.context/server"})
    public void shouldRequestStateContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.system.developer.dropped/client",
        "${app}/request.system.developer.dropped/server"})
    public void shouldRequestSystemDeveloperDropped() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.trailing.user/client",
        "${app}/request.trailing.user/server"})
    public void shouldRequestTrailingUser() throws Exception
    {
        k3po.finish();
    }
}
