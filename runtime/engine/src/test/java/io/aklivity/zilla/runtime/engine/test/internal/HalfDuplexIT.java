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
package io.aklivity.zilla.runtime.engine.test.internal;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isA;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.ComparisonFailure;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runners.model.TestTimedOutException;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class HalfDuplexIT
{
    private final K3poRule k3po = new K3poRule()
            .setScriptRoot("io/aklivity/zilla/runtime/engine/test/k3po/ext/half.duplex");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final ExpectedException thrown = ExpectedException.none();

    @Rule
    public final TestRule chain = outerRule(thrown).around(k3po).around(timeout);

    @Test
    @Specification({
        "handshake/client",
        "handshake/server"
    })
    public void shouldHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.ext/client",
        "handshake.ext/server"
    })
    public void shouldHandshakeWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.authorized/client",
        "handshake.authorized/server"
    })
    public void shouldHandshakeWithAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.budget.id/client",
        "handshake.budget.id/server"
    })
    public void shouldHandshakeWithBudgetId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.abort/client"
    })
    public void shouldAbortHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.rejected/client",
        "handshake.rejected/server"
    })
    public void shouldRejectHandshake() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.ext.rejected/client",
        "handshake.ext.rejected/server"
    })
    public void shouldRejectHandshakeWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "handshake.unequal.authorization/client",
        "handshake.unequal.authorization/server"
    })
    public void shouldFailHandshakeWithUnequalAuthorization() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.write.advise.flush/client",
        "client.sent.write.advise.flush/server"
    })
    public void shouldReceiveClientSentWriteAdviseFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.write.advise.flush.ext/client",
        "client.sent.write.advise.flush.ext/server"
    })
    public void shouldReceiveClientSentWriteAdviseFlushWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.read.advise.challenge/client",
        "client.sent.read.advise.challenge/server"
    })
    public void shouldReceiveClientSentReadAdviseChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.read.advise.challenge.ext/client",
        "client.sent.read.advise.challenge.ext/server"
    })
    public void shouldReceiveClientSentReadAdviseChallengeWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.data/client",
        "client.sent.data/server"
    })
    public void shouldReceiveClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.data.ext/client",
        "client.sent.data.ext/server"
    })
    public void shouldReceiveClientSentDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Test times out but still passes")
    @Test
    @Specification({
        "client.sent.data.missing.ext/client",
        "client.sent.data.missing.ext/server"
    })
    public void shouldRejectClientSentDataMissingExtension() throws Exception
    {
        thrown.expect(hasProperty("failures", contains(asList(instanceOf(ComparisonFailure.class),
                                                              instanceOf(TestTimedOutException.class)))));
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.write.advise.flush/client",
        "server.sent.write.advise.flush/server"
    })
    public void shouldReceiveServerSentWriteAdviseFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.write.advise.flush.ext/client",
        "server.sent.write.advise.flush.ext/server"
    })
    public void shouldReceiveServerSentWriteAdviseFlushWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.read.advise.challenge/client",
        "server.sent.read.advise.challenge/server"
    })
    public void shouldReceiveServerSentReadAdviseChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.read.advise.challenge.ext/client",
        "server.sent.read.advise.challenge.ext/server"
    })
    public void shouldReceiveServerSentReadAdviseChallengeWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.data/client",
        "server.sent.data/server"
    })
    public void shouldReceiveServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.data.ext/client",
        "server.sent.data.ext/server"
    })
    public void shouldReceiveServerSentDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Test times out but still passes")
    @Test
    @Specification({
        "server.sent.data.missing.ext/client",
        "server.sent.data.missing.ext/server"
    })
    public void shouldRejectServerSentDataWithMissingExtension() throws Exception
    {
        thrown.expect(anyOf(isA(ComparisonFailure.class),
                            hasProperty("failures", hasItem(isA(ComparisonFailure.class)))));
        k3po.finish();
    }

    @Test
    @Specification({
        "client.write.close/client",
        "client.write.close/server"
    })
    public void shouldReceiveClientShutdownOutput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.write.close.ext/client",
        "client.write.close.ext/server"
    })
    public void shouldReceiveClientShutdownOutputWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.close/client",
        "client.close/server"
    })
    public void shouldReceiveClientClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.close/client",
        "server.close/server"
    })
    public void shouldReceiveServerClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.write.close/client",
        "server.write.close/server"
    })
    public void shouldReceiveServerShutdownOutput() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.write.close.ext/client",
        "server.write.close.ext/server"
    })
    public void shouldReceiveServerShutdownOutputWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.write.abort/client",
        "server.sent.write.abort/server"
    })
    public void shouldReceiveServerSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.write.abort/client",
        "client.sent.write.abort/server"
    })
    public void shouldReceiveClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.write.abort.server.replied.write.close/client",
        "client.sent.write.abort.server.replied.write.close/server"
    })
    public void shouldReceiveClientSentWriteAbortAndReplyWriteClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.read.and.write.abort/client",
        "server.sent.read.and.write.abort/server"
    })
    public void shouldReceiveServerSentReadAndWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.write.abort.server.replied.write.abort/client",
        "client.sent.write.abort.server.replied.write.abort/server"
    })
    public void shouldReceiveClientSentWriteAbortAndReplyWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.read.abort/client",
        "server.sent.read.abort/server"
    })
    public void shouldReceiveServerSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.read.abort/client",
        "client.sent.read.abort/server"
    })
    public void shouldReceiveClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.read.abort.before.reply/client",
        "client.sent.read.abort.before.reply/server"
    })
    public void shouldReceiveClientSentReadAbortBeforeReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.read.abort.server.replied.read.abort/client",
        "client.sent.read.abort.server.replied.read.abort/server"
    })
    public void shouldReceiveClientSentReadAbortAndReplyReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.throttle/client",
        "server.sent.throttle/server"
    })
    public void shouldThrottleClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.throttle/client",
        "client.sent.throttle/server"
    })
    public void shouldThrottleServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.throttle.message/client",
        "server.sent.throttle.message/server"
    })
    public void shouldThrottleClientSentDataPerMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.throttle.message/client",
        "client.sent.throttle.message/server"
    })
    public void shouldThrottleServerSentDataPerMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.throttle.initial.only/client",
        "server.sent.throttle.initial.only/server"
    })
    public void shouldThrottleInitialOnlyClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.throttle.initial.only/client",
        "client.sent.throttle.initial.only/server"
    })
    public void shouldThrottleInitialOnlyServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.throttle.initial.only.message.aligned/client",
        "server.sent.throttle.initial.only.message.aligned/server"
    })
    public void shouldThrottleInitialOnlyClientSentDataWhenMessageAligned() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.throttle.initial.only.message.aligned/client",
        "client.sent.throttle.initial.only.message.aligned/server"
    })
    public void shouldThrottleInitialOnlyServerSentDataWhenMessageAligned() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.overflow/client",
        "server.sent.overflow/server"
    })
    public void shouldOverflowClientSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.overflow/client",
        "client.sent.overflow/server"
    })
    public void shouldOverflowServerSentData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.flush.empty.data.with.ext/client",
        "client.flush.empty.data.with.ext/server"
    })
    public void shouldReceiveClientFlushedEmptyDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.write.empty.data.and.server.read.empty.data/client",
        "client.sent.write.empty.data.and.server.read.empty.data/server"
    })
    public void shouldReceiveClientWrittenEmptyDataAndServerReadEmptyData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.flush.null.data.with.ext/client",
        "client.flush.null.data.with.ext/server"
    })
    public void shouldReceiveClientFlushedNullDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.flush.empty.data.with.ext/client",
        "server.flush.empty.data.with.ext/server"
    })
    public void shouldReceiveServerFlushedEmptyDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.flush.null.data.with.ext/client",
        "server.flush.null.data.with.ext/server"
    })
    public void shouldReceiveServerFlushedNullDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.write.empty.data.with.ext/client",
        "server.write.empty.data.with.ext/server"
    })
    public void shouldReceiveServerWrittenEmptyDataWithExtension() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.write.empty.data.and.client.read.empty.data/client",
        "server.sent.write.empty.data.and.client.read.empty.data/server"
    })
    public void shouldReceiveServerWrittenEmptyDataAndClientReadEmptyData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.option.flags.fragmentation/client",
        "client.sent.option.flags.fragmentation/server"
    })
    public void shouldReceiveClientSentOptionFlagsFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.option.flags.no.fragmentation/client",
        "client.sent.option.flags.no.fragmentation/server"
    })
    public void shouldReceiveClientSentOptionFlagsNotFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.option.flags.then.reset.flags/client",
        "client.sent.option.flags.then.reset.flags/server"
    })
    public void shouldReceiveClientSentOptionFlagsThenResetFlags() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "client.sent.option.flags.incomplete/client",
        "client.sent.option.flags.incomplete/server"
    })
    public void shouldReceiveClientSentOptionFlagsIncomplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.option.flags.fragmentation/client",
        "server.sent.option.flags.fragmentation/server"
    })
    public void shouldReceiveServerSentOptionFlagsFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.option.flags.no.fragmentation/client",
        "server.sent.option.flags.no.fragmentation/server"
    })
    public void shouldReceiveServerSentOptionFlagsNotFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.option.flags.then.reset.flags/client",
        "server.sent.option.flags.then.reset.flags/server"
    })
    public void shouldReceiveServerSentOptionFlagsThenResetFlags() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.option.flags.incomplete/client",
        "server.sent.option.flags.incomplete/server"
    })
    public void shouldReceiveServerSentOptionFlagsIncomplete() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "server.sent.option.ack/client",
        "server.sent.option.ack/server"
    })
    public void shouldReceiveServerSentOptionAck() throws Exception
    {
        k3po.finish();
    }
}
