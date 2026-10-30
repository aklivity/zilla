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

public class A2aCardIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/card.failure/client",
        "${app}/card.failure/server"})
    public void shouldCardFailure() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/card.input.modes/client",
        "${app}/card.input.modes/server"})
    public void shouldCardInputModes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/card.input.modes.wildcard/client",
        "${app}/card.input.modes.wildcard/server"})
    public void shouldCardInputModesWildcard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/card.streaming/client",
        "${app}/card.streaming/server"})
    public void shouldCardStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/card.streaming.absent/client",
        "${app}/card.streaming.absent/server"})
    public void shouldCardStreamingAbsent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/card.streaming.disabled/client",
        "${app}/card.streaming.disabled/server"})
    public void shouldCardStreamingDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/card.unsupported.media/client",
        "${app}/card.unsupported.media/server"})
    public void shouldCardUnsupportedMedia() throws Exception
    {
        k3po.finish();
    }
}
