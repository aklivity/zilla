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
        "${app}/agent.card.guarded/client",
        "${app}/agent.card.guarded/server"})
    public void shouldAgentCardGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.slug/client",
        "${app}/agent.card.slug/server"})
    public void shouldAgentCardSlug() throws Exception
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
        "${app}/agent.not.found/client",
        "${app}/agent.not.found/server"})
    public void shouldAgentNotFound() throws Exception
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
        "${app}/forbidden.bearer/client",
        "${app}/forbidden.bearer/server"})
    public void shouldForbiddenBearer() throws Exception
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
        "${app}/push.config.create.blocked/client",
        "${app}/push.config.create.blocked/server"})
    public void shouldPushConfigCreateBlocked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.create.sealed/client",
        "${app}/push.config.create.sealed/server"})
    public void shouldPushConfigCreateSealed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.create.slug/client",
        "${app}/push.config.create.slug/server"})
    public void shouldPushConfigCreateSlug() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.create.too.long/client",
        "${app}/push.config.create.too.long/server"})
    public void shouldPushConfigCreateTooLong() throws Exception
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
        "${app}/push.config.get/client",
        "${app}/push.config.get/server"})
    public void shouldPushConfigGet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.get.sealed/client",
        "${app}/push.config.get.sealed/server"})
    public void shouldPushConfigGetSealed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.get.slug/client",
        "${app}/push.config.get.slug/server"})
    public void shouldPushConfigGetSlug() throws Exception
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
        "${app}/push.config.list.sealed/client",
        "${app}/push.config.list.sealed/server"})
    public void shouldPushConfigListSealed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.list.slug/client",
        "${app}/push.config.list.slug/server"})
    public void shouldPushConfigListSlug() throws Exception
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
        "${app}/push.deliver.100k/client",
        "${app}/push.deliver.100k/server"})
    public void shouldPushDeliver100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.deliver.blocked/client",
        "${app}/push.deliver.blocked/server"})
    public void shouldPushDeliverBlocked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.deliver.blocked.resolved/client",
        "${app}/push.deliver.blocked.resolved/server"})
    public void shouldPushDeliverBlockedResolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.deliver.ordered/client",
        "${app}/push.deliver.ordered/server"})
    public void shouldPushDeliverOrdered() throws Exception
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
        "${app}/push.deliver.slots/client",
        "${app}/push.deliver.slots/server"})
    public void shouldPushDeliverSlots() throws Exception
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
        "${app}/push.message.send/client",
        "${app}/push.message.send/server"})
    public void shouldPushMessageSend() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.message.send.sealed/client",
        "${app}/push.message.send.sealed/server"})
    public void shouldPushMessageSendSealed() throws Exception
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
        "${app}/push.notify.sealed/client",
        "${app}/push.notify.sealed/server"})
    public void shouldPushNotifySealed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.notify.sealed.100k/client",
        "${app}/push.notify.sealed.100k/server"})
    public void shouldPushNotifySealed100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.notify.sealed.rejected/client",
        "${app}/push.notify.sealed.rejected/server"})
    public void shouldPushNotifySealedRejected() throws Exception
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
        "${app}/send.message.interface.unsupported/client",
        "${app}/send.message.interface.unsupported/server"})
    public void shouldSendMessageInterfaceUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.message.slug/client",
        "${app}/send.message.slug/server"})
    public void shouldSendMessageSlug() throws Exception
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

    @Test
    @Specification({
        "${app}/agent.card.not.found/client",
        "${app}/agent.card.not.found/server"})
    public void shouldRejectAgentCardNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.forbidden/client",
        "${app}/agent.card.forbidden/server"})
    public void shouldRejectAgentCardForbidden() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.skills/client",
        "${app}/agent.card.skills/server"})
    public void shouldAgentCardSkills() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.skills.filtered/client",
        "${app}/agent.card.skills.filtered/server"})
    public void shouldAgentCardSkillsFiltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.skills.restricted/client",
        "${app}/agent.card.skills.restricted/server"})
    public void shouldRejectAgentCardSkillsRestricted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/agent.card.extended.twice/client",
        "${app}/agent.card.extended.twice/server"})
    public void shouldExtendedAgentCardTwice() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.card/client",
        "${app}/discovery.card/server"})
    public void shouldDiscoveryCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.find.name/client",
        "${app}/discovery.find.name/server"})
    public void shouldDiscoveryFindName() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.find.skill/client",
        "${app}/discovery.find.skill/server"})
    public void shouldDiscoveryFindSkill() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.guarded/client",
        "${app}/discovery.guarded/server"})
    public void shouldDiscoveryGuarded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.guarded.filtered/client",
        "${app}/discovery.guarded.filtered/server"})
    public void shouldDiscoveryGuardedFiltered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.limit/client",
        "${app}/discovery.limit/server"})
    public void shouldDiscoveryLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.limit.config/client",
        "${app}/discovery.limit.config/server"})
    public void shouldDiscoveryLimitConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.no.match/client",
        "${app}/discovery.no.match/server"})
    public void shouldDiscoveryNoMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.query.data/client",
        "${app}/discovery.query.data/server"})
    public void shouldDiscoveryQueryData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/discovery.rank/client",
        "${app}/discovery.rank/server"})
    public void shouldDiscoveryRank() throws Exception
    {
        k3po.finish();
    }
}
