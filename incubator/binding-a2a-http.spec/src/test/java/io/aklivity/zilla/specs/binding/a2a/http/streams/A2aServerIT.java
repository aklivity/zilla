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
package io.aklivity.zilla.specs.binding.a2a.http.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class A2aServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/http/streams/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/get.item/client",
        "${app}/get.item/server"})
    public void shouldGetItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.item.100k/client",
        "${app}/get.item.100k/server"})
    public void shouldGetItem100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.item.error/client",
        "${app}/get.item.error/server"})
    public void shouldFailGetItemOnUpstreamError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.item.aborted/client",
        "${app}/get.item.aborted/server"})
    public void shouldAbortGetItemWhenUpstreamAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.item.unresolved/client",
        "${app}/get.item.unresolved/server"})
    public void shouldRejectGetItemWhenExpressionUnresolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.item.forbidden/client",
        "${app}/get.item.forbidden/server"})
    public void shouldRejectGetItemForbidden() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.item.credentials/client",
        "${app}/get.item.credentials/server"})
    public void shouldRejectGetItemOnUnauthorizedUpstream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.item/client",
        "${app}/create.item/server"})
    public void shouldCreateItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/search.items/client",
        "${app}/search.items/server"})
    public void shouldSearchItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/intent.no.match/client",
        "${app}/intent.no.match/server"})
    public void shouldRejectIntentWithNoMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/args.missing.input.required/client",
        "${app}/args.missing.input.required/server"})
    public void shouldRequireInputWhenArgsMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/args.invalid.input.required/client",
        "${app}/args.invalid.input.required/server"})
    public void shouldRequireInputWhenArgsInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/input.required.followup.valid/client",
        "${app}/input.required.followup.valid/server"})
    public void shouldCompleteInputRequiredFollowup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/input.required.followup.unknown.task/client",
        "${app}/input.required.followup.unknown.task/server"})
    public void shouldRejectFollowupForUnknownTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/card.generated/client",
        "${app}/card.generated/server"})
    public void shouldGenerateAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/stream.sse/client",
        "${app}/stream.sse/server"})
    public void shouldStreamServerSentEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/send.sse.unsupported/client",
        "${app}/send.sse.unsupported/server"})
    public void shouldRejectSendMessageOnStreamingRoute() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/get.task.unsupported/client",
        "${app}/get.task.unsupported/server"})
    public void shouldRejectGetTaskAsUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/push.config.create.unsupported/client",
        "${app}/push.config.create.unsupported/server"})
    public void shouldRejectPushConfigCreateAsUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/extended.agent.card.unsupported/client",
        "${app}/extended.agent.card.unsupported/server"})
    public void shouldRejectExtendedAgentCardAsUnsupported() throws Exception
    {
        k3po.finish();
    }
}
