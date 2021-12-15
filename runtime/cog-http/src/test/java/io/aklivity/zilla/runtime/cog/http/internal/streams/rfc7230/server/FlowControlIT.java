/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http.internal.streams.rfc7230.server;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
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
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/http/streams/network/rfc7230/")
        .addScriptRoot("app", "io/aklivity/zilla/specs/cog/http/streams/application/rfc7230/");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/cog/http/config")
        .external("app#0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/flow.control/multiple.requests.pipelined.fragmented/client",
        "${app}/connection.management/multiple.requests.serialized/server" })
    @Ignore("TODO: support pipelined requests, at a minimum by serializing them")
    public void shouldAcceptMultipleRequestsInSameDataFrameFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/connection.management/multiple.requests.pipelined/client",
        "${app}/connection.management/multiple.requests.serialized/server" })
    @Ignore("TODO: support pipelined requests, at a minimum by serializing them")
    @ScriptProperty("clientInitialWindow \"89\"")
    public void shouldFlowControlMultipleResponses() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/flow.control/request.fragmented/client",
        "${app}/message.format/request.with.headers/server" })
    public void shouldAcceptFragmentedRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/message.format/request.with.content.length/client",
        "${app}/message.format/request.with.content.length/server" })
    @ScriptProperty("serverInitialWindow \"3\"")
    public void shouldSplitRequestDataToRespectTargetWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/flow.control/request.fragmented.with.content.length/client",
        "${app}/message.format/request.with.content.length/server" })
    public void shouldAcceptFragmentedRequestWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/flow.control/request.fragmented.with.content.length/client",
        "${app}/message.format/request.with.content.length/server"})
    @ScriptProperty("serverInitialWindow \"3\"")
    public void shouldFlowControlFragmentedRequestWithContent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/flow.control/request.with.content.length.and.transport.close/client",
        "${app}/message.format/request.with.content.length/server" })
    public void shouldDeferEndProcessingUntilRequestProcessed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/connection.management/upgrade.request.and.response.with.data/client",
        "${app}/connection.management/upgrade.request.and.response.with.data/server"})
    @ScriptProperty({"clientInitialWindow \"11\"",
                     "serverInitialWindow \"9\""})
    public void shouldFlowControlDataAfterUpgrade() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/architecture/request.and.response/client",
        "${app}/architecture/request.and.response/server"})
    @ScriptProperty("clientInitialWindow \"11\"")
    public void shouldFlowControlResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/flow.control/response.headers.with.padding/client",
        "${app}/flow.control/response.headers.with.padding/server"})
    public void shouldProcessResponseHeadersFragmentedByPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/message.format/response.with.content.length/client",
        "${app}/message.format/response.with.content.length/server"})
    @ScriptProperty("clientInitialWindow \"9\"")
    public void shouldFlowControlResponseWithContentLength() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.json")
    @Specification({
        "${net}/flow.control/response.with.padding/client",
        "${app}/flow.control/response.with.padding/server"})
    public void shouldProcessResponseWithPadding() throws Exception
    {
        k3po.finish();
    }
}
