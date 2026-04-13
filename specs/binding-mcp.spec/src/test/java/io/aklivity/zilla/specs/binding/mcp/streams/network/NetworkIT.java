/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.specs.binding.mcp.streams.network;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/mcp/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/lifecycle.initialize/client",
        "${net}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.disconnect/client",
        "${net}/lifecycle.disconnect/server"})
    public void shouldDisconnectLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/lifecycle.ping/client",
        "${net}/lifecycle.ping/server"})
    public void shouldPingLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/notify.canceled/client",
        "${net}/notify.canceled/server"})
    public void shouldNotifyCanceled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/capability.tools/client",
        "${net}/capability.tools/server"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/capability.progress/client",
        "${net}/capability.progress/server"})
    public void shouldReportProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/capability.prompts/client",
        "${net}/capability.prompts/server"})
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/capability.resources/client",
        "${net}/capability.resources/server"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/capability.completion/client",
        "${net}/capability.completion/server"})
    public void shouldComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/capability.logging/client",
        "${net}/capability.logging/server"})
    public void shouldSetLoggingLevel() throws Exception
    {
        k3po.finish();
    }
}
