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
package io.aklivity.zilla.runtime.binding.sse.internal.streams.client;

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

public class ErrorIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/sse/streams/application/error")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/error");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(2048)
        .responseBufferCapacity(2048)
        .counterValuesBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/sse/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/server.reset/client",
        "${net}/server.reset/response" })
    public void shouldResetRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/client.abort/client",
        "${net}/client.reset/response" })
    public void shouldAbortRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/server.abort/client",
        "${net}/server.abort/response" })
    public void shouldAbortResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/client.reset/client",
        "${net}/client.reset/response" })
    public void shouldResetResponse() throws Exception
    {
        k3po.finish();
    }
}
