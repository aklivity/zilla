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
package io.aklivity.zilla.specs.binding.a2a.streams.application;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/agent.card/client",
        "${app}/agent.card/server"})
    public void shouldAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.etag.not.modified/client",
        "${app}/agent.card.etag.not.modified/server"})
    public void shouldAgentCardEtagNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.legacy.agent.json/client",
        "${app}/agent.card.legacy.agent.json/server"})
    public void shouldAgentCardLegacyAgentJson() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.tenant/client",
        "${app}/agent.card.tenant/server"})
    public void shouldAgentCardTenant() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.v0.3/client",
        "${app}/agent.card.v0.3/server"})
    public void shouldAgentCardV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cancel.task/client",
        "${app}/cancel.task/server"})
    public void shouldCancelTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cancel.task.not.cancelable/client",
        "${app}/cancel.task.not.cancelable/server"})
    public void shouldCancelTaskNotCancelable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cancel.task.v0.3/client",
        "${app}/cancel.task.v0.3/server"})
    public void shouldCancelTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extended.agent.card/client",
        "${app}/extended.agent.card/server"})
    public void shouldExtendedAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extended.agent.card.not.configured/client",
        "${app}/extended.agent.card.not.configured/server"})
    public void shouldExtendedAgentCardNotConfigured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extension.required/client",
        "${app}/extension.required/server"})
    public void shouldExtensionRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.task/client",
        "${app}/get.task/server"})
    public void shouldGetTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.task.not.found/client",
        "${app}/get.task.not.found/server"})
    public void shouldGetTaskNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.task.v0.3/client",
        "${app}/get.task.v0.3/server"})
    public void shouldGetTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/invalid.params/client",
        "${app}/invalid.params/server"})
    public void shouldInvalidParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/list.tasks/client",
        "${app}/list.tasks/server"})
    public void shouldListTasks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.create/client",
        "${app}/push.config.create/server"})
    public void shouldPushConfigCreate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.create.v0.3/client",
        "${app}/push.config.create.v0.3/server"})
    public void shouldPushConfigCreateV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.delete/client",
        "${app}/push.config.delete/server"})
    public void shouldPushConfigDelete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.delete.v0.3/client",
        "${app}/push.config.delete.v0.3/server"})
    public void shouldPushConfigDeleteV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.get/client",
        "${app}/push.config.get/server"})
    public void shouldPushConfigGet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.get.v0.3/client",
        "${app}/push.config.get.v0.3/server"})
    public void shouldPushConfigGetV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.list/client",
        "${app}/push.config.list/server"})
    public void shouldPushConfigList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.list.v0.3/client",
        "${app}/push.config.list.v0.3/server"})
    public void shouldPushConfigListV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.deliver/client",
        "${app}/push.deliver/server"})
    public void shouldPushDeliver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.deliver.retry/client",
        "${app}/push.deliver.retry/server"})
    public void shouldPushDeliverRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.deliver.unreachable/client",
        "${app}/push.deliver.unreachable/server"})
    public void shouldPushDeliverUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.notify/client",
        "${app}/push.notify/server"})
    public void shouldPushNotify() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.notify.rejected/client",
        "${app}/push.notify.rejected/server"})
    public void shouldPushNotifyRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message/client",
        "${app}/send.message/server"})
    public void shouldSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message.100k/client",
        "${app}/send.message.100k/server"})
    public void shouldSendMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message.extensions/client",
        "${app}/send.message.extensions/server"})
    public void shouldSendMessageExtensions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message.task/client",
        "${app}/send.message.task/server"})
    public void shouldSendMessageTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message.tenant/client",
        "${app}/send.message.tenant/server"})
    public void shouldSendMessageTenant() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message.tenant.params/client",
        "${app}/send.message.tenant.params/server"})
    public void shouldSendMessageTenantParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message.v0.3/client",
        "${app}/send.message.v0.3/server"})
    public void shouldSendMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/stream.message/client",
        "${app}/stream.message/server"})
    public void shouldStreamMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/stream.message.100k/client",
        "${app}/stream.message.100k/server"})
    public void shouldStreamMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/stream.message.aborted/client",
        "${app}/stream.message.aborted/server"})
    public void shouldStreamMessageAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/stream.message.v0.3/client",
        "${app}/stream.message.v0.3/server"})
    public void shouldStreamMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.task/client",
        "${app}/subscribe.task/server"})
    public void shouldSubscribeTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.task.terminal.unsupported/client",
        "${app}/subscribe.task.terminal.unsupported/server"})
    public void shouldSubscribeTaskTerminalUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/subscribe.task.v0.3/client",
        "${app}/subscribe.task.v0.3/server"})
    public void shouldSubscribeTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/unauthorized.bearer/client",
        "${app}/unauthorized.bearer/server"})
    public void shouldUnauthorizedBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/version.unsupported/client",
        "${app}/version.unsupported/server"})
    public void shouldVersionUnsupported() throws Exception
    {
        k3po.finish();
    }
}
