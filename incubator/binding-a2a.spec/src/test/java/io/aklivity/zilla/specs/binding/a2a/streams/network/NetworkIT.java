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
package io.aklivity.zilla.specs.binding.a2a.streams.network;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/a2a/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/agent.card/client",
        "${net}/agent.card/server"})
    public void shouldAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/agent.card.legacy.agent.json/client",
        "${net}/agent.card.legacy.agent.json/server"})
    public void shouldAgentCardLegacyAgentJson() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/agent.card.slug/client",
        "${net}/agent.card.slug/server"})
    public void shouldAgentCardSlug() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/agent.card.v0.3/client",
        "${net}/agent.card.v0.3/server"})
    public void shouldAgentCardV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/agent.not.found/client",
        "${net}/agent.not.found/server"})
    public void shouldAgentNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cancel.task/client",
        "${net}/cancel.task/server"})
    public void shouldCancelTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cancel.task.not.cancelable/client",
        "${net}/cancel.task.not.cancelable/server"})
    public void shouldCancelTaskNotCancelable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cancel.task.v0.3/client",
        "${net}/cancel.task.v0.3/server"})
    public void shouldCancelTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/extended.agent.card/client",
        "${net}/extended.agent.card/server"})
    public void shouldExtendedAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/extended.agent.card.not.configured/client",
        "${net}/extended.agent.card.not.configured/server"})
    public void shouldExtendedAgentCardNotConfigured() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/extension.required/client",
        "${net}/extension.required/server"})
    public void shouldExtensionRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/forbidden.bearer/client",
        "${net}/forbidden.bearer/server"})
    public void shouldForbiddenBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/get.task/client",
        "${net}/get.task/server"})
    public void shouldGetTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/get.task.not.found/client",
        "${net}/get.task.not.found/server"})
    public void shouldGetTaskNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/get.task.v0.3/client",
        "${net}/get.task.v0.3/server"})
    public void shouldGetTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.params/client",
        "${net}/invalid.params/server"})
    public void shouldInvalidParams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/list.tasks/client",
        "${net}/list.tasks/server"})
    public void shouldListTasks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/method.not.found/client",
        "${net}/method.not.found/server"})
    public void shouldMethodNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/parse.error/client",
        "${net}/parse.error/server"})
    public void shouldParseError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.create/client",
        "${net}/push.config.create/server"})
    public void shouldPushConfigCreate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.create.v0.3/client",
        "${net}/push.config.create.v0.3/server"})
    public void shouldPushConfigCreateV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.delete/client",
        "${net}/push.config.delete/server"})
    public void shouldPushConfigDelete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.delete.v0.3/client",
        "${net}/push.config.delete.v0.3/server"})
    public void shouldPushConfigDeleteV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.get/client",
        "${net}/push.config.get/server"})
    public void shouldPushConfigGet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.get.v0.3/client",
        "${net}/push.config.get.v0.3/server"})
    public void shouldPushConfigGetV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.list/client",
        "${net}/push.config.list/server"})
    public void shouldPushConfigList() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.config.list.v0.3/client",
        "${net}/push.config.list.v0.3/server"})
    public void shouldPushConfigListV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.deliver/client",
        "${net}/push.deliver/server"})
    public void shouldPushDeliver() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.deliver.retry/client",
        "${net}/push.deliver.retry/server"})
    public void shouldPushDeliverRetry() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.deliver.unreachable/client",
        "${net}/push.deliver.unreachable/server"})
    public void shouldPushDeliverUnreachable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.notify/client",
        "${net}/push.notify/server"})
    public void shouldPushNotify() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/push.notify.rejected/client",
        "${net}/push.notify.rejected/server"})
    public void shouldPushNotifyRejected() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/send.message/client",
        "${net}/send.message/server"})
    public void shouldSendMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/send.message.100k/client",
        "${net}/send.message.100k/server"})
    public void shouldSendMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/send.message.extensions/client",
        "${net}/send.message.extensions/server"})
    public void shouldSendMessageExtensions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/send.message.slug/client",
        "${net}/send.message.slug/server"})
    public void shouldSendMessageSlug() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/send.message.task/client",
        "${net}/send.message.task/server"})
    public void shouldSendMessageTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/send.message.v0.3/client",
        "${net}/send.message.v0.3/server"})
    public void shouldSendMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/stream.message/client",
        "${net}/stream.message/server"})
    public void shouldStreamMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/stream.message.100k/client",
        "${net}/stream.message.100k/server"})
    public void shouldStreamMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/stream.message.aborted/client",
        "${net}/stream.message.aborted/server"})
    public void shouldStreamMessageAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/stream.message.v0.3/client",
        "${net}/stream.message.v0.3/server"})
    public void shouldStreamMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.task/client",
        "${net}/subscribe.task/server"})
    public void shouldSubscribeTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.task.terminal.unsupported/client",
        "${net}/subscribe.task.terminal.unsupported/server"})
    public void shouldSubscribeTaskTerminalUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/subscribe.task.v0.3/client",
        "${net}/subscribe.task.v0.3/server"})
    public void shouldSubscribeTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/unauthorized.bearer/client",
        "${net}/unauthorized.bearer/server"})
    public void shouldUnauthorizedBearer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/version.unsupported/client",
        "${net}/version.unsupported/server"})
    public void shouldVersionUnsupported() throws Exception
    {
        k3po.finish();
    }
}
