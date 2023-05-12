/*
 * Copyright 2021-2023 Aklivity Inc.
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

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class TransferCodingsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/transfer.codings");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/request.transfer.encoding.chunked/client",
        "${net}/request.transfer.encoding.chunked/server" })
    public void requestTransferEncodingChunked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/multiple.requests.transfer.encoding.chunked/client",
        "${net}/multiple.requests.transfer.encoding.chunked/server" })
    public void multipleRequeststTransferEncodingChunked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/invalid.chunked.request.no.crlf.at.end.of.chunk/client",
        "${net}/invalid.chunked.request.no.crlf.at.end.of.chunk/server" })
    public void invalidRequestTransferEncodingChunked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.transfer.encoding.chunked/client",
        "${net}/response.transfer.encoding.chunked/server" })
    public void responseTransferEncodingChunked() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Ignore("${net}/requires enhancement https://github.com/k3po/k3po/issues/313")
    public void requestTransferEncodingChunkedExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Ignore("${net}/requires enhancement https://github.com/k3po/k3po/issues/313")
    public void responseTransferEncodingChunkedExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.transfer.encoding.chunked.with.trailer/client",
        "${net}/request.transfer.encoding.chunked.with.trailer/server" })
    public void requestTransferEncodingChunkedWithTrailer() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.transfer.encoding.chunked.with.trailer/client",
        "${net}/response.transfer.encoding.chunked.with.trailer/server" })
    public void responseTransferEncodingChunkedWithTrailer() throws Exception
    {
        k3po.finish();
    }
}
