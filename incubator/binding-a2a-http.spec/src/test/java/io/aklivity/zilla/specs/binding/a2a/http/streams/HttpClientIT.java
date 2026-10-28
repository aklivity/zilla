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

public class HttpClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/a2a/http/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${http}/get.item/client",
        "${http}/get.item/server"})
    public void shouldProxyGetItemToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.100k/client",
        "${http}/get.item.100k/server"})
    public void shouldProxyGetItem100kToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.error/client",
        "${http}/get.item.error/server"})
    public void shouldProxyGetItemErrorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.aborted/client",
        "${http}/get.item.aborted/server"})
    public void shouldAbortGetItemFromHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.credentials/client",
        "${http}/get.item.credentials/server"})
    public void shouldProxyGetItemCredentialsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.item/client",
        "${http}/create.item/server"})
    public void shouldProxyCreateItemToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/search.items/client",
        "${http}/search.items/server"})
    public void shouldProxySearchItemsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/stream.sse/client",
        "${http}/stream.sse/server"})
    public void shouldProxyStreamSseToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/send.sse.unsupported/client",
        "${http}/send.sse.unsupported/server"})
    public void shouldResetStreamingResponseToHttp() throws Exception
    {
        k3po.finish();
    }
}
