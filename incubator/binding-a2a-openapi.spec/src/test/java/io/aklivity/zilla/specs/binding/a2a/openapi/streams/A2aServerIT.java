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
package io.aklivity.zilla.specs.binding.a2a.openapi.streams;

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
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/openapi/streams/a2a");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/read.order/client",
        "${app}/read.order/server"})
    public void shouldReadOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/intent.select/client",
        "${app}/intent.select/server"})
    public void shouldSelectSkillByIntent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.order.10k/client",
        "${app}/read.order.10k/server"})
    public void shouldReadOrder10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.order.aborted/client",
        "${app}/read.order.aborted/server"})
    public void shouldAbortReadOrderWhenUpstreamAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.order.error/client",
        "${app}/read.order.error/server"})
    public void shouldFailReadOrderOnUpstreamError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/read.order.credentials/client",
        "${app}/read.order.credentials/server"})
    public void shouldInjectCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.pr/client",
        "${app}/create.pr/server"})
    public void shouldCreatePr() throws Exception
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
        "${app}/search.items.with.limit/client",
        "${app}/search.items.with.limit/server"})
    public void shouldSearchItemsWithLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/search.code.forbidden/client",
        "${app}/search.code.forbidden/server"})
    public void shouldRejectSearchCodeWhenForbidden() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/skills.card/client",
        "${app}/skills.card/server"})
    public void shouldServeSkillsCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/skills.card.streaming/client",
        "${app}/skills.card.streaming/server"})
    public void shouldServeSkillsCardStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/stream.sse/client",
        "${app}/stream.sse/server"})
    public void shouldStreamEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/input.required/client",
        "${app}/input.required/server"})
    public void shouldRequireInputWhenArgumentsMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/intent.no.match/client",
        "${app}/intent.no.match/server"})
    public void shouldRejectWhenIntentNoMatch() throws Exception
    {
        k3po.finish();
    }
}
