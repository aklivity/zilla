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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.client;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_CONCURRENT_STREAMS;
import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfigurationTest.HTTP_STREAM_INITIAL_WINDOW_NAME;
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

public class AbortIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/connection.abort")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/connection.abort");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(HTTP_SERVER_CONCURRENT_STREAMS, 100)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v2")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/h2.client.sent.write.abort.on.open.request.response/client",
        "${net}/h2.client.sent.error.on.open.request.response/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void clientSentWriteAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/h2.client.sent.read.abort.on.open.request.response/client",
        "${net}/h2.client.sent.error.on.open.request.response/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void clientSentReadAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/h2.server.sent.write.abort.on.open.request.response/client",
        "${net}/h2.server.sent.write.abort.on.open.request.response/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void serverSentWriteAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/h2.server.sent.read.abort.on.open.request.response/client",
        "${net}/h2.server.sent.read.abort.on.open.request.response/server" })
    @Configure(name = HTTP_STREAM_INITIAL_WINDOW_NAME, value = "65535")
    public void serverSentReadAbortOnOpenRequestResponse() throws Exception
    {
        k3po.finish();
    }
}
