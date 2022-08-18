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

public class EndOfLineIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/sse/streams/application/end.of.line")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/sse/streams/network/end.of.line");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/sse/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.when.json")
    @Specification({
        "${app}/carriage.return/client",
        "${net}/carriage.return/response" })
    public void shouldReceiveMessageWithCarriageReturnEndOfLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.json")
    @Specification({
        "${app}/line.feed/client",
        "${net}/line.feed/response" })
    public void shouldReceiveMessageWithLineFeedEndOfLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.json")
    @Specification({
        "${app}/carriage.return.line.feed/client",
        "${net}/carriage.return.line.feed/response" })
    public void shouldReceiveMessageWithCarriageReturnLineFeedEndOfLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.when.json")
    @Specification({
        "${app}/carriage.return.line.feed.fragmented/client",
        "${net}/carriage.return.line.feed.fragmented/response" })
    public void shouldReceiveMessageWithCarriageReturnLineFeedEndOfLineFragmented() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE_ONE");
        k3po.notifyBarrier("SEND_MESSAGE_TWO");
        k3po.finish();
    }
}
