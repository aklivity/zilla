/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.specs.cog.http.streams.application.rfc7230;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class FlowControlIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/http/streams/application/rfc7230/flow.control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/response.first.fragment.maximum.headers/client",
        "${app}/response.first.fragment.maximum.headers/server"})
    public void shouldProcessResponseWhenFirstFragmentIsHeadersOfLength64() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.headers.too.long/client",
        "${app}/request.headers.too.long/server"})
    public void shouldRejectRequestWithHeadersExceedingMaximumLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.chunked.with.extensions.filling.maximum.headers/client",
        "${app}/response.chunked.with.extensions.filling.maximum.headers/server"})
    public void shouldProcessResponseWhenFirstChunkMetadataFillsMaxHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.headers.too.long/client.no.response",
        "${app}/response.headers.too.long/server.no.response"})
    public void shouldRejectNetworkResponseWithHeadersTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.headers.too.long/client.response.reset",
        "${app}/response.headers.too.long/server.response.reset"})
    public void shouldRejectApplicationResponseWithHeadersTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.headers.with.padding/client",
        "${app}/response.headers.with.padding/server"})
    public void shouldProcessResponseHeadersFragmentedByPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.with.content.exceeding.window/client",
        "${app}/response.with.content.exceeding.window/server"})
    public void shouldHandleResponseWithContentViolatingWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.fragmented.with.padding/client",
        "${app}/response.fragmented.with.padding/server"})
    public void shouldProcessResponseFragmentedByPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/response.with.padding/client",
        "${app}/response.with.padding/server"})
    public void shouldProcessResponseWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/request.with.padding/client",
        "${app}/request.with.padding/server"})
    public void shouldProcessRequestWithPadding() throws Exception
    {
        k3po.finish();
    }
}
