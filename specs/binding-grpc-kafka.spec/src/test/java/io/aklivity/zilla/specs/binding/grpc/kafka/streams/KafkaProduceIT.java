/*
 * Copyright 2021-2024 Aklivity Inc
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

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class KafkaProduceIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/grpc/kafka/streams/kafka/produce");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${kafka}/bidi.stream.rpc/client",
        "${kafka}/bidi.stream.rpc/server"})
    public void shouldExchangeMessageInBidiStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/bidi.stream.rpc.write.abort/client",
        "${kafka}/bidi.stream.rpc.write.abort/server"})
    public void shouldNotProduceBidiStreamMessageOnWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/client.stream.rpc/client",
        "${kafka}/client.stream.rpc/server"})
    public void shouldExchangeMessageInClientStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/client.stream.rpc.oneway/client",
        "${kafka}/client.stream.rpc.oneway/server"})
    public void shouldSendMessageInClientStreamOneway() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/client.stream.rpc.write.abort/client",
        "${kafka}/client.stream.rpc.write.abort/server"})
    public void shouldNotProduceClientStreamMessageOnWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.stream.rpc/client",
        "${kafka}/server.stream.rpc/server"})
    public void shouldExchangeMessageInServerStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/server.stream.rpc.read.aborted/client",
        "${kafka}/server.stream.rpc.read.aborted/server"})
    public void shouldNotReceiveServerStreamMessageOnReadAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unary.rpc/client",
        "${kafka}/unary.rpc/server"})
    public void shouldExchangeMessageInUnary() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unary.rpc.oneway/client",
        "${kafka}/unary.rpc.oneway/server"})
    public void shouldSendMessageInUnaryOneway() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unary.rpc.rejected/client",
        "${kafka}/unary.rpc.rejected/server"})
    public void shouldRejectUnaryRpc() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unary.rpc.error/client",
        "${kafka}/unary.rpc.error/server"})
    public void shouldRejectUnaryRpcWithError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unary.rpc.sent.write.abort/client",
        "${kafka}/unary.rpc.sent.write.abort/server"})
    public void shouldNotProduceMessageOnUnaryRrcSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/unary.rpc.message.value.100k/client",
        "${kafka}/unary.rpc.message.value.100k/server"})
    public void shouldExchangeMessageValue100kInUnary() throws Exception
    {
        k3po.finish();
    }

}
