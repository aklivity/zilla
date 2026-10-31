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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

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

public class A2aProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application")
        .addScriptRoot("cache", "io/aklivity/zilla/specs/binding/a2a/streams/cache");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("app1")
        .external("app2")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/send.message/client",
        "${app}/send.message/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/send.message.extensions/client",
        "${app}/send.message.extensions/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageExtensions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/send.message.task/client",
        "${app}/send.message.task/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessageTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/send.message.100k/client",
        "${app}/send.message.100k/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSendMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/stream.message/client",
        "${app}/stream.message/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldStreamMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/stream.message.100k/client",
        "${app}/stream.message.100k/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldStreamMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/stream.message.aborted/client",
        "${app}/stream.message.aborted/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAbortStreamMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.task/client",
        "${app}/get.task/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldGetTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.task.not.found/client",
        "${app}/get.task.not.found/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectGetTaskNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/list.tasks/client",
        "${app}/list.tasks/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListTasks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/cancel.task/client",
        "${app}/cancel.task/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCancelTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/cancel.task.not.cancelable/client",
        "${app}/cancel.task.not.cancelable/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectCancelTaskNotCancelable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/subscribe.task/client",
        "${app}/subscribe.task/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldSubscribeTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/subscribe.task.terminal.unsupported/client",
        "${app}/subscribe.task.terminal.unsupported/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectSubscribeTaskTerminal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/push.config.create/client",
        "${app}/push.config.create/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldCreatePushConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/push.config.get/client",
        "${app}/push.config.get/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldGetPushConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/push.config.list/client",
        "${app}/push.config.list/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldListPushConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/push.config.delete/client",
        "${app}/push.config.delete/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldDeletePushConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/extension.required/client",
        "${app}/extension.required/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectExtensionRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/version.unsupported/client",
        "${app}/version.unsupported/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectVersionUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/extended.agent.card/client",
        "${app}/extended.agent.card/server"})
    @ScriptProperty({"serverAddress \"zilla://streams/app1\"", "authorization 1L"})
    public void shouldExtendedAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.routes.yaml")
    @Specification({
        "${app}/send.message.slug/client",
        "${app}/send.message.slug/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRouteSendMessageByAgent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.routes.yaml")
    @Specification({
        "${app}/agent.not.found/client"})
    public void shouldRejectAgentNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.routes.yaml")
    @Specification({
        "${app}/agent.card.not.found/client"})
    public void shouldRejectAgentCardNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.routes.yaml")
    @Specification({
        "${app}/invalid.params/client"})
    public void shouldRejectAgentRequiredWithoutDefaultRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.routes.guarded.yaml")
    @Specification({
        "${app}/forbidden.bearer/client"})
    public void shouldRejectForbiddenAgent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.routes.guarded.yaml")
    @Specification({
        "${app}/agent.card.forbidden/client"})
    public void shouldRejectForbiddenAgentCard() throws Exception
    {
        k3po.finish();
    }
}
