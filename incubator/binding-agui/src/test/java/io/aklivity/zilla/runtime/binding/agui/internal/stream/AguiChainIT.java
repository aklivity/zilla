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
package io.aklivity.zilla.runtime.binding.agui.internal.stream;

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

public class AguiChainIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("chain", "io/aklivity/zilla/specs/binding/agui/streams/chain");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/agui/config")
        .external("net1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.text.message/client",
        "${chain}/run.text.message/server"})
    public void shouldRunTextMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.text.chunks/client",
        "${chain}/run.text.chunks/server"})
    public void shouldRunTextChunks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.tool.call/client",
        "${chain}/run.tool.call/server"})
    public void shouldRunToolCall() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.state/client",
        "${chain}/run.state/server"})
    public void shouldRunState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.step/client",
        "${chain}/run.step/server"})
    public void shouldRunStep() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.outcome.success/client",
        "${chain}/run.outcome.success/server"})
    public void shouldRunOutcomeSuccess() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.error/client",
        "${chain}/run.error/server"})
    public void shouldRunError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("chain.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/net1\"")
    @Specification({
        "${chain}/run.text.100k/client",
        "${chain}/run.text.100k/server"})
    public void shouldRunText100k() throws Exception
    {
        k3po.finish();
    }
}
