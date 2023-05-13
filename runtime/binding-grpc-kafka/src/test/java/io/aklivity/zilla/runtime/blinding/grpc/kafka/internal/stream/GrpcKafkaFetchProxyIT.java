/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.blinding.grpc.kafka.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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

public class GrpcKafkaFetchProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("grpc", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/grpc/fetch")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/kafka/fetch");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/grpc/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("fetch.proxy.rpc.yaml")
    @Specification({
        "${grpc}/unreliable.unary.rpc/client",
        "${kafka}/unreliable.unary.rpc/server"})
    public void shouldStreamUnreliableMessageWithUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("fetch.proxy.rpc.yaml")
    @Specification({
        "${grpc}/reliable.unary.rpc/client",
        "${kafka}/reliable.unary.rpc/server"})
    public void shouldStreamReliableMessageWithUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("fetch.proxy.rpc.yaml")
    @Specification({
        "${grpc}/reject.request.body.unary.rpc/client",
        "${kafka}/reject.request.body.unary.rpc/server"})
    public void shouldRejectRequestBodyUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("fetch.proxy.rpc.yaml")
    @Specification({
        "${grpc}/client.sent.write.abort.unary.rpc/client",
        "${kafka}/client.sent.write.abort.unary.rpc/server"})
    public void shouldNotExchangeMessageOnClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("fetch.proxy.rpc.yaml")
    @Specification({
        "${grpc}/client.sent.read.abort.unary.rpc/client",
        "${kafka}/client.sent.read.abort.unary.rpc/server"})
    public void shouldNotExchangeMessageOnClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }
}
