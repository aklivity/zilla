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

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class A2aServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/a2a/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message/client",
        "${app}/send.message/server"})
    public void shouldSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message.100k/client",
        "${app}/send.message.100k/server"})
    public void shouldSendMessageWith100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message.slug/client",
        "${app}/send.message.slug/server"})
    public void shouldSendMessageToAgent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message.task/client",
        "${app}/send.message.task/server"})
    public void shouldSendMessageReturningTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message.task.failed/client",
        "${app}/send.message.task.failed/server"})
    public void shouldSendMessageReturningTaskFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message.task.input.required/client",
        "${app}/send.message.task.input.required/server"})
    public void shouldSendMessageReturningTaskInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message.extensions/client",
        "${app}/send.message.extensions/server"})
    public void shouldSendMessageWithExtensions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/extension.required/client",
        "${app}/extension.required/server"})
    public void shouldRejectMessageRequiringExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/invalid.params/client",
        "${app}/invalid.params/server"})
    public void shouldRejectInvalidParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/agent.not.found/client",
        "${app}/agent.not.found/server"})
    public void shouldRejectUnknownAgent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/unauthorized.bearer/client",
        "${app}/unauthorized.bearer/server"})
    public void shouldRejectUnauthorizedBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/forbidden.bearer/client",
        "${app}/forbidden.bearer/server"})
    public void shouldRejectForbiddenBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/version.unsupported/client"})
    public void shouldRejectUnsupportedVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.versions.v10.yaml")
    @Specification({
        "${net}/version.unsupported/client"})
    public void shouldRejectUnsupportedVersionWhenLockedToV10() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.versions.v03.yaml")
    @Specification({
        "${net}/version.unsupported/client"})
    public void shouldRejectUnsupportedVersionWhenLockedToV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/parse.error/client"})
    public void shouldRejectParseError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/method.not.found/client"})
    public void shouldRejectMethodNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guard.yaml")
    @Specification({
        "${net}/reject.bearer.invalid/client"})
    public void shouldRejectBearerInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/reject.method.not.allowed/client"})
    public void shouldRejectMethodNotAllowed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message/client",
        "${app}/stream.message/server"})
    public void shouldStreamMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.100k/client",
        "${app}/stream.message.100k/server"})
    public void shouldStreamMessageWith100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.aborted/client",
        "${app}/stream.message.aborted/server"})
    public void shouldAbortStreamMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.auth.required/client",
        "${app}/stream.message.auth.required/server"})
    public void shouldStreamMessageAuthRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.canceled/client",
        "${app}/stream.message.canceled/server"})
    public void shouldStreamMessageCanceled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.failed/client",
        "${app}/stream.message.failed/server"})
    public void shouldStreamMessageFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.input.required/client",
        "${app}/stream.message.input.required/server"})
    public void shouldStreamMessageInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.rejected/client",
        "${app}/stream.message.rejected/server"})
    public void shouldStreamMessageRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.status.message/client",
        "${app}/stream.message.status.message/server"})
    public void shouldStreamMessageStatusMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/get.task/client",
        "${app}/get.task/server"})
    public void shouldGetTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/get.task.not.found/client",
        "${app}/get.task.not.found/server"})
    public void shouldRejectGetTaskNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/cancel.task/client",
        "${app}/cancel.task/server"})
    public void shouldCancelTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/cancel.task.not.cancelable/client",
        "${app}/cancel.task.not.cancelable/server"})
    public void shouldRejectCancelTaskNotCancelable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/list.tasks/client",
        "${app}/list.tasks/server"})
    public void shouldListTasks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/subscribe.task/client",
        "${app}/subscribe.task/server"})
    public void shouldSubscribeTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/subscribe.task.terminal.unsupported/client",
        "${app}/subscribe.task.terminal.unsupported/server"})
    public void shouldRejectSubscribeTaskTerminal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.create/client",
        "${app}/push.config.create/server"})
    public void shouldCreatePushConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.get/client",
        "${app}/push.config.get/server"})
    public void shouldGetPushConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.list/client",
        "${app}/push.config.list/server"})
    public void shouldListPushConfigs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.delete/client",
        "${app}/push.config.delete/server"})
    public void shouldDeletePushConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guard.yaml")
    @Specification({
        "${net}/extended.agent.card/client",
        "${app}/extended.agent.card/server"})
    public void shouldGetExtendedAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.guard.yaml")
    @Specification({
        "${net}/extended.agent.card.not.configured/client",
        "${app}/extended.agent.card.not.configured/server"})
    public void shouldRejectExtendedAgentCardNotConfigured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.push.yaml")
    @Specification({
        "${net}/push.notify/client",
        "${app}/push.notify/server"})
    public void shouldNotifyPush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.push.yaml")
    @Specification({
        "${net}/push.notify.rejected/client",
        "${app}/push.notify.rejected/server"})
    public void shouldRejectPushNotify() throws Exception
    {
        k3po.finish();
    }
}
