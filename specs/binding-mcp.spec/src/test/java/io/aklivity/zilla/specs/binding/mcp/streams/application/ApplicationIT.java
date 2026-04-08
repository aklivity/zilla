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
package io.aklivity.zilla.specs.binding.mcp.streams.application;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/lifecycle.initialize/client",
        "${app}/lifecycle.initialize/server"})
    public void shouldInitializeLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.disconnect/client",
        "${app}/lifecycle.disconnect/server"})
    public void shouldDisconnectLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.ping/client",
        "${app}/lifecycle.ping/server"})
    public void shouldPingLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/lifecycle.cancel/client",
        "${app}/lifecycle.cancel/server"})
    public void shouldCancelLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/capability.tools/client",
        "${app}/capability.tools/server"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/capability.progress/client",
        "${app}/capability.progress/server"})
    public void shouldReportProgress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/capability.prompts/client",
        "${app}/capability.prompts/server"})
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/capability.resources/client",
        "${app}/capability.resources/server"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/capability.completion/client",
        "${app}/capability.completion/server"})
    public void shouldComplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/capability.logging/client",
        "${app}/capability.logging/server"})
    public void shouldSetLoggingLevel() throws Exception
    {
        k3po.finish();
    }
}
