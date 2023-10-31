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

public class GrpcKafkaProduceProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("grpc", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/grpc/produce")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/kafka/produce");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/grpc/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/unary.rpc/client",
        "${kafka}/unary.rpc/server"})
    public void shouldExchangeMessageWithUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/unary.rpc.rejected/client",
        "${kafka}/unary.rpc.rejected/server"})
    public void shouldRejectUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/unary.rpc.error/client",
        "${kafka}/unary.rpc.error/server"})
    public void shouldRejectUnaryRpcWithError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/unary.rpc.sent.write.abort/client",
        "${kafka}/unary.rpc.sent.write.abort/server"})
    public void shouldNotProduceMessageOnUnaryRpcSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/client.stream.rpc/client",
        "${kafka}/client.stream.rpc/server"})
    public void shouldExchangeMessageWithClientStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/client.stream.rpc.write.abort/client",
        "${kafka}/client.stream.rpc.write.abort/server"})
    public void shouldNotProduceClientStreamMessageOnWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/server.stream.rpc/client",
        "${kafka}/server.stream.rpc/server"})
    public void shouldExchangeMessageWithServerStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/server.stream.rpc.read.aborted/client",
        "${kafka}/server.stream.rpc.read.aborted/server"})
    public void shouldNotReceiveServerStreamMessageOnReadAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/bidi.stream.rpc/client",
        "${kafka}/bidi.stream.rpc/server"})
    public void shouldExchangeMessageWithBidiStreamRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("produce.proxy.rpc.yaml")
    @Specification({
        "${grpc}/bidi.stream.rpc.write.abort/client",
        "${kafka}/bidi.stream.rpc.write.abort/server"})
    public void shouldNotProduceBidiStreamMessageOnWriteAbort() throws Exception
    {
        k3po.finish();
    }
}
