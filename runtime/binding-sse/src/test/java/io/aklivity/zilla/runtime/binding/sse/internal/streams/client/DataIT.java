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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
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

public class DataIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/sse/streams/application/data")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/data");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/sse/config")
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/empty/client",
        "${net}/empty/response" })
    public void shouldReceiveEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/non.empty/client",
        "${net}/non.empty/response" })
    public void shouldReceiveNonEmptyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/multiple/client",
        "${net}/multiple/response" })
    public void shouldReceiveMultipleMessages() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/fragmented.10k/client",
        "${net}/fragmented.10k/response" })
    public void shouldReceiveFragmentedMessage10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/fragmented.100k/client",
        "${net}/fragmented.100k/response" })
    public void shouldReceiveFragmentedMessage100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/name.only/client",
        "${net}/name.only/response" })
    public void shouldReceiveNameOnlyMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.yaml")
    @Specification({
        "${app}/multi.line/client",
        "${net}/multi.line/response" })
    public void shouldReceiveMultiLineMessage() throws Exception
    {
        k3po.finish();
    }
}
