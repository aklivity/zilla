/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.mcp.http.streams;

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
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/http/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${http}/create.pr/client",
        "${http}/create.pr/server"})
    public void shouldProxyCreatePrToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/read.order/client",
        "${http}/read.order/server"})
    public void shouldProxyReadOrderToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.10k/client",
        "${http}/create.pr.10k/server"})
    public void shouldProxyCreatePr10kToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.100k/client",
        "${http}/create.pr.100k/server"})
    public void shouldProxyCreatePr100kToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.fragmented/client",
        "${http}/create.pr.fragmented/server"})
    public void shouldProxyCreatePrFragmentedToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.rich/client",
        "${http}/create.pr.rich/server"})
    public void shouldProxyCreatePrWithStructuredArgumentsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/read.order.10k/client",
        "${http}/read.order.10k/server"})
    public void shouldProxyReadOrder10kToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/read.order.100k/client",
        "${http}/read.order.100k/server"})
    public void shouldProxyReadOrder100kToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/search.code/client",
        "${http}/search.code/server"})
    public void shouldProxySearchCodeToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.error/client",
        "${http}/create.pr.error/server"})
    public void shouldProxyCreatePrErrorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.aborted/client",
        "${http}/create.pr.aborted/server"})
    public void shouldAbortCreatePrToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.credentials/client",
        "${http}/create.pr.credentials/server"})
    public void shouldProxyCreatePrWithCredentialsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.pr.error.100k/client",
        "${http}/create.pr.error.100k/server"})
    public void shouldProxyCreatePrError100kToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/search.code.100k/client",
        "${http}/search.code.100k/server"})
    public void shouldProxySearchCode100kToHttp() throws Exception
    {
        k3po.finish();
    }
}
