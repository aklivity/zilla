/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.grpc.kafka.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class GrpcProduceIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("grpc", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/grpc/produce");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${grpc}/bidi.stream.rpc/client",
        "${grpc}/bidi.stream.rpc/server"})
    public void shouldExchangeMessageInBidiStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/bidi.stream.rpc.write.abort/client",
        "${grpc}/bidi.stream.rpc.write.abort/server"})
    public void shouldNotProduceBidiStreamMessageOnWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/client.stream.rpc/client",
        "${grpc}/client.stream.rpc/server"})
    public void shouldExchangeMessageInClientStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/client.stream.rpc.write.abort/client",
        "${grpc}/client.stream.rpc.write.abort/server"})
    public void shouldNotProduceClientStreamMessageOnWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/server.stream.rpc/client",
        "${grpc}/server.stream.rpc/server"})
    public void shouldExchangeMessageInServerStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/server.stream.rpc.read.aborted/client",
        "${grpc}/server.stream.rpc.read.aborted/server"})
    public void shouldNotReceiveServerStreamMessageOnReadAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/unary.rpc/client",
        "${grpc}/unary.rpc/server"})
    public void shouldExchangeMessageInUnary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/unary.rpc.rejected/client",
        "${grpc}/unary.rpc.rejected/server"})
    public void shouldRejectUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${grpc}/unary.rpc.sent.write.abort/client",
        "${grpc}/unary.rpc.sent.write.abort/server"})
    public void shouldNotProduceMessageOnUnaryRrcSentWriteAbort() throws Exception
    {
        k3po.finish();
    }
}
