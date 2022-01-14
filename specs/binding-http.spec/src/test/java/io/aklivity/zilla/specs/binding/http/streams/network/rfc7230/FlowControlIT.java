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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7230;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/flow.control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/multiple.requests.pipelined.fragmented/client",
        "${net}/multiple.requests.pipelined.fragmented/server"})
    public void multipleRequestsPipelinedFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multiple.requests.with.content.length.pipelined.fragmented/client",
        "${net}/multiple.requests.with.content.length.pipelined.fragmented/server"})
    public void multipleRequestsWithContentLengthPipelinedFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.fragmented/client",
        "${net}/request.fragmented/server"})
    public void fragmentedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.fragmented.with.content.length/client",
        "${net}/request.fragmented.with.content.length/server"})
    public void fragmentedRequestWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.content.length.and.transport.close/client",
        "${net}/request.with.content.length.and.transport.close/server"})
    public void shouldDeferEndProcessingUntilRequestProcessed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.chunked.with.extensions.filling.maximum.headers/client",
        "${net}/response.chunked.with.extensions.filling.maximum.headers/server"})
    public void shouldProcessResponseWhenFirstChunkMetadataFillsMaxHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.first.fragment.maximum.headers/client",
        "${net}/response.first.fragment.maximum.headers/server"})
    public void shouldProcessResponseWhenFirstFragmentIsHeadersOfLength64() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.fragmented/client",
        "${net}/response.fragmented/server"})
    public void shouldProcessFragmentedResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.fragmented.with.content.length/client",
        "${net}/response.fragmented.with.content.length/server"})
    public void shouldProcessFragmentedResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.headers.too.long/client.response.reset",
        "${net}/response.headers.too.long/server.response.reset"})
    public void shouldRejectNetworkResponseWithHeadersTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.fragmented.with.padding/client",
        "${net}/response.fragmented.with.padding/server"})
    public void shouldProcessResponseFragmentedByPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.headers.with.padding/client",
        "${net}/response.headers.with.padding/server"})
    public void shouldProcessResponseHeadersFragmentedByPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.with.padding/client",
        "${net}/request.with.padding/server"})
    public void shouldProcessRequestWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.with.padding/client",
        "${net}/response.with.padding/server"})
    public void shouldProcessResponseWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.with.content.exceeding.window/client",
        "${net}/response.with.content.exceeding.window/server"})
    public void shouldHandleResponseWithContentViolatingWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.headers.too.long/client.5xx.response",
        "${net}/response.headers.too.long/server.5xx.response"})
    public void shouldRejectApplicationResponseWithHeadersTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.with.content.length.and.transport.close/client",
        "${net}/response.with.content.length.and.transport.close/server"})
    public void shouldDeferEndProcessingUntilResponseProcessed() throws Exception
    {
        k3po.finish();
    }
}
