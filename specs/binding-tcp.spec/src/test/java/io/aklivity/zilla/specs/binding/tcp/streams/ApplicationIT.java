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
package io.aklivity.zilla.specs.binding.tcp.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

/**
 * RFC-793
 */
public class ApplicationIT
{

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tcp/streams/application/rfc793");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/client.and.server.sent.data.multiple.frames/client",
        "${app}/client.and.server.sent.data.multiple.frames/server" })
    public void shouldSendAndReceiveData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.and.server.sent.data.with.padding/client",
        "${app}/client.and.server.sent.data.with.padding/server" })
    public void shouldSendAndReceiveDataWithPadding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.close/client",
        "${app}/client.close/server" })
    public void shouldInitiateClientClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.received.abort.sent.end/client",
        "${app}/client.received.abort.sent.end/server" })
    public void clientShouldReceiveResetAndAbortAndNoAdditionalResetWhensendEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.received.reset.and.abort/client",
        "${app}/client.received.reset.and.abort/server" })
    public void clientShouldReceiveResetAndAbortAfterIOExceptionFromRead() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.abort/client",
        "${app}/client.sent.abort/server" })
    public void shouldProcessAbortFromClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.abort.and.reset/client",
        "${app}/client.sent.abort.and.reset/server" })
    public void shouldProcessAbortAndResetFromClient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data/client",
        "${app}/client.sent.data/server" })
    public void shouldReceiveClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data.reconfigure/client",
        "${app}/client.sent.data.reconfigure/server" })
    public void shouldReceiveClientSentDataOnNewPortAfterReconfigure() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data.multiple.frames/client",
        "${app}/client.sent.data.multiple.frames/server" })
    public void shouldReceiveClientSentDataInMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data.multiple.streams/client",
        "${app}/client.sent.data.multiple.streams/server" })
    public void shouldReceiveClientSentDataOnMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data.multiple.streams.second.was.reset/client",
        "${app}/client.sent.data.multiple.streams.second.was.reset/server" })
    public void shouldReceiveClientSentDataWithMultipleStreamsSecondWasReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data.received.abort.and.reset/client",
        "${app}/client.sent.data.received.abort.and.reset/server" })
    public void shouldSendAbortAndResetToClientAfterIOExceptionFromWrite() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data.received.reset/client",
        "${app}/client.sent.data.received.reset/server" })
    public void shouldSendResetToClientAppWhenItExceedsWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.data.then.end/client",
        "${app}/client.sent.data.then.end/server" })
    public void shouldReceiveClientSentDataAndEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.end.then.received.data/client",
        "${app}/client.sent.end.then.received.data/server" })
    public void clientShouldReceiveDataAfterEndingOutput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.reset/client",
        "${app}/client.sent.reset/server" })
    public void clientShouldResetConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.reset.and.end/client",
        "${app}/client.sent.reset.and.end/server" })
    public void clientShouldResetConnectionThenEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/concurrent.connections/client",
        "${app}/concurrent.connections/server" })
    public void shouldConveyBidirectionalDataOnConcurrentConnections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/max.connections/client",
        "${app}/max.connections/server" })
    public void maxConnections() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.established/client",
        "${app}/connection.established/server" })
    public void shouldEstablishConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.established.ipv6/client",
        "${app}/connection.established.ipv6/server" })
    public void shouldEstablishConnectionIPv6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/connection.failed/client",
        "${app}/connection.failed/server" })
    public void shouldFailConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.close/client",
        "${app}/server.close/server" })
    public void shouldInitiateServerClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.received.abort.sent.end/client",
        "${app}/server.received.abort.sent.end/server" })
    public void serverShouldReceiveResetAndAbortAndNoAdditionalResetWhensendEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.received.reset.and.abort/client",
        "${app}/server.received.reset.and.abort/server" })
    public void serverShouldReceiveResetAndAbortAfterIOExceptionFromRead() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.abort/client",
        "${app}/server.sent.abort/server" })
    public void serverShouldAbortConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.abort.and.reset/client",
        "${app}/server.sent.abort.and.reset/server" })
    public void serverShouldAbortAndResetConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.data/client",
        "${app}/server.sent.data/server" })
    public void shouldReceiveServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.data.multiple.frames/client",
        "${app}/server.sent.data.multiple.frames/server" })
    public void shouldReceiveServerSentDataMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.data.multiple.streams/client",
        "${app}/server.sent.data.multiple.streams/server" })
    public void shouldReceiveServerSentDataOnMultipleStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.data.multiple.streams.second.was.reset/client",
        "${app}/server.sent.data.multiple.streams.second.was.reset/server" })
    public void shouldReceiveServerSentDataWithMultipleStreamsSecondWasReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.data.received.reset.and.abort/client",
        "${app}/server.sent.data.received.reset.and.abort/server" })
    public void shouldSendResetToServerAppWhenItExceedsWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.data.received.reset.and.abort/client",
        "${app}/server.sent.data.received.reset.and.abort/server" })
    public void shouldSendResetAndAbortToServerAfterIOExceptionFromWrite() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.data.then.end/client",
        "${app}/server.sent.data.then.end/server" })
    public void shouldReceiveServerSentDataAndEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.end.then.received.data/client",
        "${app}/server.sent.end.then.received.data/server" })
    public void serverShouldReceiveDataAfterEndingOutput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.reset/client",
        "${app}/server.sent.reset/server" })
    public void serverShouldResetConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/server.sent.reset.and.end/client",
        "${app}/server.sent.reset.and.end/server" })
    public void serverShouldResetConnectionThenEnd() throws Exception
    {
        k3po.finish();
    }
}
