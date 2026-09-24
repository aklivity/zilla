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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class McpMetricsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/mcp/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/metrics/mcp/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("tools.call.yaml")
    @Specification({
        "${app}/tools.call/client",
        "${app}/tools.call/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordToolsCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("tools.call.attributes.yaml")
    @Specification({
        "${app}/tools.call/client",
        "${app}/tools.call/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordToolsCallAttributes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("tools.call.is.error.yaml")
    @Specification({
        "${app}/tools.call.is.error/client",
        "${app}/tools.call.is.error/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordToolsCallIsError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("tools.call.aborted.yaml")
    @Specification({
        "${app}/tools.call.aborted/client",
        "${app}/tools.call.aborted/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordToolsCallAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("tools.list.yaml")
    @Specification({
        "${app}/tools.list/client",
        "${app}/tools.list/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordToolsList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("prompts.get.yaml")
    @Specification({
        "${app}/prompts.get/client",
        "${app}/prompts.get/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordPromptsGet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("resources.read.yaml")
    @Specification({
        "${app}/resources.read/client",
        "${app}/resources.read/server" })
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRecordResourcesRead() throws Exception
    {
        k3po.finish();
    }
}
