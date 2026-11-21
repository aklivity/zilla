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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.stream;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfigurationTest.AGUI_A2A_HANDLE_ID_NAME;
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

public class AguiA2aStoreIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("agui", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/agui")
        .addScriptRoot("agent", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(AGUI_A2A_HANDLE_ID_NAME, "%s::handleId".formatted(AguiA2aStoreIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/runtime/binding/agui/a2a/internal/stream")
        .external("a2a0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    public static String handleId()
    {
        return "h-1";
    }

    @Test
    @Configuration("proxy.store.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/restored.context/client",
        "${agent}/restored.context/server"})
    public void shouldRestoreContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.store.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/restored.interrupt.resume/client",
        "${agent}/restored.interrupt.resume/server"})
    public void shouldRestoreInterruptResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.store.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/restored.interrupt.resume.consumed/client",
        "${agent}/restored.interrupt.resume.consumed/server"})
    public void shouldRestoreInterruptResumeOnce() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.store.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/restored.tool.result/client",
        "${agent}/restored.tool.result/server"})
    public void shouldRestoreToolResult() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.store.empty.yaml")
    @Specification({
        "${agui}/interrupt.resume.unknown/client"})
    public void shouldRejectUnknownInterruptResume() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.store.empty.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/owner.context.remembered/client",
        "${agent}/owner.context.remembered/server"})
    public void shouldWriteThroughContext() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.store.empty.yaml")
    @ScriptProperty("serverAddress \"zilla://streams/a2a0\"")
    @Specification({
        "${agui}/interrupt.resume/client",
        "${agent}/interrupt.resume/server"})
    public void shouldWriteThroughInterrupt() throws Exception
    {
        k3po.finish();
    }
}
