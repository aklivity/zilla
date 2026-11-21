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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class A2aClientIT
{
    private static final String HOST_RESOLVER =
        "io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aPushFixtures::resolveHost";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/a2a/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message/client",
        "${net}/discover.send.message/server"})
    public void shouldSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message.extensions/client",
        "${net}/discover.send.message.extensions/server"})
    public void shouldSendMessageExtensions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message.task/client",
        "${net}/discover.send.message.task/server"})
    public void shouldSendMessageTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message.task.failed/client",
        "${net}/discover.send.message.task.failed/server"})
    public void shouldSendMessageTaskFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message.task.input.required/client",
        "${net}/discover.send.message.task.input.required/server"})
    public void shouldSendMessageTaskInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message.100k/client",
        "${net}/discover.send.message.100k/server"})
    public void shouldSendMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message.slug/client",
        "${net}/discover.send.message.slug/server"})
    public void shouldSendMessageSlug() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message/client",
        "${net}/discover.stream.message/server"})
    public void shouldStreamMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message.aborted/client",
        "${net}/discover.stream.message.aborted/server"})
    public void shouldStreamMessageAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message.auth.required/client",
        "${net}/discover.stream.message.auth.required/server"})
    public void shouldStreamMessageAuthRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message.canceled/client",
        "${net}/discover.stream.message.canceled/server"})
    public void shouldStreamMessageCanceled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message.failed/client",
        "${net}/discover.stream.message.failed/server"})
    public void shouldStreamMessageFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message.input.required/client",
        "${net}/discover.stream.message.input.required/server"})
    public void shouldStreamMessageInputRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message.rejected/client",
        "${net}/discover.stream.message.rejected/server"})
    public void shouldStreamMessageRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/stream.message.status.message/client",
        "${net}/discover.stream.message.status.message/server"})
    public void shouldStreamMessageStatusMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/get.task/client",
        "${net}/discover.get.task/server"})
    public void shouldGetTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/get.task.not.found/client",
        "${net}/discover.get.task.not.found/server"})
    public void shouldGetTaskNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/cancel.task/client",
        "${net}/discover.cancel.task/server"})
    public void shouldCancelTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/cancel.task.not.cancelable/client",
        "${net}/discover.cancel.task.not.cancelable/server"})
    public void shouldCancelTaskNotCancelable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/subscribe.task/client",
        "${net}/discover.subscribe.task/server"})
    public void shouldSubscribeTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/subscribe.task.terminal.unsupported/client",
        "${net}/discover.subscribe.task.terminal.unsupported/server"})
    public void shouldSubscribeTaskTerminalUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/list.tasks/client",
        "${net}/discover.list.tasks/server"})
    public void shouldListTasks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/push.config.create/client",
        "${net}/discover.push.config.create/server"})
    public void shouldPushConfigCreate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/push.config.get/client",
        "${net}/discover.push.config.get/server"})
    public void shouldPushConfigGet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/push.config.list/client",
        "${net}/discover.push.config.list/server"})
    public void shouldPushConfigList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/push.config.delete/client",
        "${net}/discover.push.config.delete/server"})
    public void shouldPushConfigDelete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/invalid.params/client",
        "${net}/discover.invalid.params/server"})
    public void shouldInvalidParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/extension.required/client",
        "${net}/discover.extension.required/server"})
    public void shouldExtensionRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/unauthorized.bearer/client",
        "${net}/discover.unauthorized.bearer/server"})
    public void shouldUnauthorizedBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/forbidden.bearer/client",
        "${net}/discover.forbidden.bearer/server"})
    public void shouldForbiddenBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/agent.not.found/client",
        "${net}/discover.agent.not.found/server"})
    public void shouldAgentNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/agent.card/client",
        "${net}/discover.agent.card/server"})
    public void shouldAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/agent.card.slug/client",
        "${net}/discover.agent.card.slug/server"})
    public void shouldAgentCardSlug() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/agent.card.v0.3/client",
        "${net}/discover.agent.card.legacy/server"})
    public void shouldAgentCardLegacy() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message/client",
        "${net}/discover.send.message.legacy/server"})
    public void shouldSendMessageLegacy() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message/client",
        "${net}/discover.send.message.tenant/server"})
    public void shouldSendMessageTenant() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/get.task/client",
        "${net}/discover.get.task.tenant/server"})
    public void shouldGetTaskTenant() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/send.message.interface.unsupported/client",
        "${net}/discover.interface.unsupported/server"})
    public void shouldRejectInterfaceUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/version.unsupported/client",
        "${net}/discover.version.unsupported/server"})
    public void shouldRejectVersionUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.yaml")
    @Specification({
        "${app}/version.unsupported/client",
        "${net}/discover.version.locked/server"})
    public void shouldRejectVersionLockedDown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/send.message/client",
        "${net}/discover.send.message.v0.3/server"})
    public void shouldSendMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/stream.message/client",
        "${net}/discover.stream.message.v0.3/server"})
    public void shouldStreamMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/get.task/client",
        "${net}/discover.get.task.v0.3/server"})
    public void shouldGetTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/cancel.task/client",
        "${net}/discover.cancel.task.v0.3/server"})
    public void shouldCancelTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/subscribe.task/client",
        "${net}/discover.subscribe.task.v0.3/server"})
    public void shouldSubscribeTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/push.config.create/client",
        "${net}/discover.push.config.create.v0.3/server"})
    public void shouldPushConfigCreateV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/push.config.get/client",
        "${net}/discover.push.config.get.v0.3/server"})
    public void shouldPushConfigGetV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/push.config.list/client",
        "${net}/discover.push.config.list.v0.3/server"})
    public void shouldPushConfigListV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/push.config.delete/client",
        "${net}/discover.push.config.delete.v0.3/server"})
    public void shouldPushConfigDeleteV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.versions.v03.yaml")
    @Specification({
        "${app}/agent.card.v0.3/client",
        "${net}/discover.agent.card.v0.3/server"})
    public void shouldAgentCardV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.credentials.yaml")
    @ScriptProperty("authorization 1L")
    @Specification({
        "${app}/extended.agent.card/client",
        "${net}/discover.extended.agent.card/server"})
    public void shouldExtendedAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.credentials.yaml")
    @ScriptProperty("authorization 1L")
    @Specification({
        "${app}/extended.agent.card.not.configured/client",
        "${net}/discover.extended.agent.card.not.configured/server"})
    public void shouldExtendedAgentCardNotConfigured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.routes.guarded.yaml")
    @Specification({
        "${app}/forbidden.bearer/client",
        "${net}/discover.forbidden.bearer.guarded/server"})
    public void shouldRejectForbiddenGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.routes.guarded.yaml")
    @Specification({
        "${app}/agent.card.guarded/client",
        "${net}/discover.agent.card.guarded/server"})
    public void shouldAgentCardGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver/client",
        "${net}/push.deliver/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    @Configure(name = "zilla.binding.a2a.push.delivery.backoff", value = "PT0.05S")
    public void shouldPushDeliver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver.retry/client",
        "${net}/push.deliver.retry/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    @Configure(name = "zilla.binding.a2a.push.delivery.backoff", value = "PT0.05S")
    public void shouldPushDeliverRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.deliver.yaml")
    @Specification({
        "${app}/push.deliver.unreachable/client",
        "${net}/push.deliver.unreachable/server"})
    @Configure(name = "zilla.engine.host.resolver", value = HOST_RESOLVER)
    @Configure(name = "zilla.binding.a2a.push.delivery.backoff", value = "PT0.05S")
    public void shouldPushDeliverUnreachable() throws Exception
    {
        k3po.finish();
    }
}
