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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7230.client;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_POOL_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_POOL_CAPACITY_NAME;
import static io.aklivity.zilla.runtime.engine.test.EngineRule.ENGINE_BUFFER_SLOT_CAPACITY_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class FlowControlLimitsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 64)
        .configure(ENGINE_BUFFER_POOL_CAPACITY, 64)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/request.headers.too.long/client"})
    public void shouldNotWriteRequestExceedingMaximumHeadersSize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/response.first.fragment.maximum.headers/client",
        "${net}/flow.control/response.first.fragment.maximum.headers/server"})
    public void shouldAcceptResponseWithFirstFragmentHeadersOfLengthMaxHttpHeadersSize() throws Exception
    {
        k3po.finish();
    }

    @Configure(name = ENGINE_BUFFER_POOL_CAPACITY_NAME, value = "8192")
    @Configure(name = ENGINE_BUFFER_SLOT_CAPACITY_NAME, value = "8192")
    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/response.fragmented.with.padding/client",
        "${net}/flow.control/response.fragmented.with.padding/server" })
    public void shouldProcessResponseFragmentedWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/transfer.codings/response.transfer.encoding.chunked/client",
        "${net}/transfer.codings/response.transfer.encoding.chunked/server" })
    public void shouldHandleChunkedResponseExceedingInitialWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/response.chunked.with.extensions.filling.maximum.headers/client",
        "${net}/flow.control/response.chunked.with.extensions.filling.maximum.headers/server" })
    public void shouldHandleChunkedResponseWithHeadersPlusFirstChunkMetadataEqualsInitialWindow()
            throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/response.headers.too.long/client.no.response",
        "${net}/flow.control/response.headers.too.long/server.response.reset"})
    public void shouldRejectResponseWithHeadersTooLong() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/flow.control/response.with.content.exceeding.window/client",
        "${net}/flow.control/response.with.content.exceeding.window/server"})
    public void shouldAbortClientAcceptReplyWhenResponseContentViolatesWindow() throws Exception
    {
        k3po.finish();
    }
}
