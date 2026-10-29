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

public class HttpClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/a2a/openapi/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${http}/read.order/client",
        "${http}/read.order/server"})
    public void shouldReadOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/intent.select/client",
        "${http}/intent.select/server"})
    public void shouldSelectSkillByIntent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/read.order.10k/client",
        "${http}/read.order.10k/server"})
    public void shouldReadOrder10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/read.order.aborted/client",
        "${http}/read.order.aborted/server"})
    public void shouldAbortReadOrderWhenUpstreamAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/read.order.error/client",
        "${http}/read.order.error/server"})
    public void shouldFailReadOrderOnUpstreamError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/read.order.credentials/client",
        "${http}/read.order.credentials/server"})
    public void shouldInjectCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr/client",
        "${http}/create.pr/server"})
    public void shouldCreatePr() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/search.items/client",
        "${http}/search.items/server"})
    public void shouldSearchItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/search.items.with.limit/client",
        "${http}/search.items.with.limit/server"})
    public void shouldSearchItemsWithLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/stream.sse/client",
        "${http}/stream.sse/server"})
    public void shouldStreamEvents() throws Exception
    {
        k3po.finish();
    }
}
