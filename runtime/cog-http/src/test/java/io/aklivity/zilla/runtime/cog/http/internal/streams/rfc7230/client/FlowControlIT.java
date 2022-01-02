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
package io.aklivity.zilla.runtime.cog.http.internal.streams.rfc7230.client;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class FlowControlIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/http/streams/network/rfc7230")
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/http/streams/application/rfc7230");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/cog/http/config/v1.1")
        .external("net#0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/architecture/request.and.response/client",
        "${net}/architecture/request.and.response/server" })
    @ScriptProperty("serverInitialWindow \"3\"")
    public void shouldFlowControlRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/message.format/request.with.content.length/client",
        "${net}/message.format/request.with.content.length/server"})
    @ScriptProperty("serverInitialWindow \"9\"")
    public void shouldFlowControlRequestWithContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.management/upgrade.request.and.response.with.data/client",
        "${net}/connection.management/upgrade.request.and.response.with.data/server"})
    @ScriptProperty({"clientInitialWindow \"11\"",
                     "serverInitialWindow \"9\""})
    public void shouldFlowControlDataAfterUpgrade() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/message.format/response.with.headers/client",
        "${net}/flow.control/response.fragmented/server"})
    public void shouldProcessFragmentedResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/message.format/response.with.content.length/client",
        "${net}/flow.control/response.fragmented.with.content.length/server"})
    public void shouldProcessFragmentedResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/message.format/response.with.content.length/client",
        "${net}/message.format/response.with.content.length/server"})
    @ScriptProperty("clientInitialWindow \"9\"")
    public void shouldFlowControlResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/message.format/response.with.content.length/client",
        "${net}/flow.control/response.fragmented.with.content.length/server"})
    @ScriptProperty("clientInitialWindow \"9\"")
    public void shouldFlowControlFragmentedResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/request.with.padding/client",
        "${net}/flow.control/request.with.padding/server" })
    public void shouldProcessRequestWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/response.with.padding/client",
        "${net}/flow.control/response.with.padding/server" })
    public void shouldProcessResponseWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/transfer.codings/response.transfer.encoding.chunked/client",
        "${net}/transfer.codings/response.transfer.encoding.chunked/server" })
    @ScriptProperty("clientInitialWindow \"9\"")
    public void shouldFlowControlResponseWithChunkedTransferEncoding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/message.format/response.with.content.length//client",
        "${net}/flow.control/response.with.content.length.and.transport.close/server" })
    public void shouldDeferEndProcessingUntilResponseProcessed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.management/multiple.requests.serialized/client",
        "${net}/connection.management/multiple.requests.same.connection/server" })
    @ScriptProperty("serverInitialWindow 16")
    public void shouldProcessFragmentedRequests() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WRITE_RESPONSE_ONE");
        k3po.notifyBarrier("WRITE_RESPONSE_TWO");
        k3po.notifyBarrier("WRITE_RESPONSE_THREE");
        k3po.finish();
    }
}
