/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.http.streams.network.eventstream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class EventStreamIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/event.stream");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/response.sse.event/client",
        "${net}/response.sse.event/server" })
    public void shouldReceiveResponseEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.non.empty/client",
        "${net}/response.sse.data.non.empty/server" })
    public void shouldReceiveResponseDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.empty/client",
        "${net}/response.sse.data.empty/server" })
    public void shouldReceiveResponseDataEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.multi.line/client",
        "${net}/response.sse.data.multi.line/server" })
    public void shouldReceiveResponseDataMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.multiple/client",
        "${net}/response.sse.data.multiple/server" })
    public void shouldReceiveResponseDataMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.fragmented.10k/client",
        "${net}/response.sse.data.fragmented.10k/server" })
    public void shouldReceiveResponseDataFragmented10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.fragmented.100k/client",
        "${net}/response.sse.data.fragmented.100k/server" })
    public void shouldReceiveResponseDataFragmented100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.initial.whitespace/client",
        "${net}/response.sse.data.initial.whitespace/server" })
    public void shouldReceiveResponseDataInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.name.only/client",
        "${net}/response.sse.data.name.only/server" })
    public void shouldReceiveResponseDataNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.invalid.utf8/client",
        "${net}/response.sse.data.invalid.utf8/server" })
    public void shouldReceiveResponseDataInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.invalid.utf8.fragmented/client",
        "${net}/response.sse.data.invalid.utf8.fragmented/server" })
    public void shouldReceiveResponseDataInvalidUtf8Fragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.invalid.utf8.truncated/client",
        "${net}/response.sse.data.invalid.utf8.truncated/server" })
    public void shouldReceiveResponseDataInvalidUtf8Truncated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.multi.byte.fragmented/client",
        "${net}/response.sse.data.multi.byte.fragmented/server" })
    public void shouldReceiveResponseDataMultiByteFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.bom.empty/client",
        "${net}/response.sse.bom.empty/server" })
    public void shouldReceiveResponseBomEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.bom.non.empty/client",
        "${net}/response.sse.bom.non.empty/server" })
    public void shouldReceiveResponseBomNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.end.of.line.line.feed/client",
        "${net}/response.sse.end.of.line.line.feed/server" })
    public void shouldReceiveResponseEndOfLineLineFeed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.end.of.line.carriage.return/client",
        "${net}/response.sse.end.of.line.carriage.return/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.end.of.line.carriage.return.line.feed/client",
        "${net}/response.sse.end.of.line.carriage.return.line.feed/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturnLineFeed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.end.of.line.carriage.return.line.feed.fragmented/client",
        "${net}/response.sse.end.of.line.carriage.return.line.feed.fragmented/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturnLineFeedFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.id.non.empty/client",
        "${net}/response.sse.id.non.empty/server" })
    public void shouldReceiveResponseIdNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.id.empty/client",
        "${net}/response.sse.id.empty/server" })
    public void shouldReceiveResponseIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.id.initial.whitespace/client",
        "${net}/response.sse.id.initial.whitespace/server" })
    public void shouldReceiveResponseIdInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.id.invalid.utf8/client",
        "${net}/response.sse.id.invalid.utf8/server" })
    public void shouldReceiveResponseIdInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.non.empty/client",
        "${net}/response.sse.type.non.empty/server" })
    public void shouldReceiveResponseTypeNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.empty/client",
        "${net}/response.sse.type.empty/server" })
    public void shouldReceiveResponseTypeEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.fragmented/client",
        "${net}/response.sse.type.fragmented/server" })
    public void shouldReceiveResponseTypeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.initial.whitespace/client",
        "${net}/response.sse.type.initial.whitespace/server" })
    public void shouldReceiveResponseTypeInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.name.only/client",
        "${net}/response.sse.type.name.only/server" })
    public void shouldReceiveResponseTypeNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.custom.empty/client",
        "${net}/response.sse.custom.empty/server" })
    public void shouldReceiveResponseCustomEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.custom.initial.whitespace/client",
        "${net}/response.sse.custom.initial.whitespace/server" })
    public void shouldReceiveResponseCustomInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.custom.invalid.utf8/client",
        "${net}/response.sse.custom.invalid.utf8/server" })
    public void shouldReceiveResponseCustomInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.custom.name.only/client",
        "${net}/response.sse.custom.name.only/server" })
    public void shouldReceiveResponseCustomNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.custom.non.empty/client",
        "${net}/response.sse.custom.non.empty/server" })
    public void shouldReceiveResponseCustomNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.id.name.only/client",
        "${net}/response.sse.id.name.only/server" })
    public void shouldReceiveResponseIdNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.invalid.utf8/client",
        "${net}/response.sse.type.invalid.utf8/server" })
    public void shouldReceiveResponseTypeInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.non.empty.interleaved/client",
        "${net}/response.sse.type.non.empty.interleaved/server" })
    public void shouldReceiveResponseTypeNonEmptyInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.type.non.empty.trailing/client",
        "${net}/response.sse.type.non.empty.trailing/server" })
    public void shouldReceiveResponseTypeNonEmptyTrailing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.no.framing/client",
        "${net}/response.sse.no.framing/server" })
    public void shouldReceiveSseResponseWithoutFraming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.id.only/client",
        "${net}/response.sse.id.only/server" })
    public void shouldReceiveResponseIdOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.retry.numeric/client",
        "${net}/response.sse.retry.numeric/server" })
    public void shouldReceiveResponseRetryNumeric() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.retry.non.empty/client",
        "${net}/response.sse.retry.non.empty/server" })
    public void shouldReceiveResponseRetryNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.retry.initial.whitespace/client",
        "${net}/response.sse.retry.initial.whitespace/server" })
    public void shouldReceiveResponseRetryInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.retry.non.numeric/client",
        "${net}/response.sse.retry.non.numeric/server" })
    public void shouldReceiveResponseRetryNonNumeric() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.retry.name.only/client",
        "${net}/response.sse.retry.name.only/server" })
    public void shouldReceiveResponseRetryNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.retry.invalid.utf8/client",
        "${net}/response.sse.retry.invalid.utf8/server" })
    public void shouldReceiveResponseRetryInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.comment.empty/client",
        "${net}/response.sse.comment.empty/server" })
    public void shouldReceiveResponseCommentEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.comment.non.empty/client",
        "${net}/response.sse.comment.non.empty/server" })
    public void shouldReceiveResponseCommentNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.comment.multi.line/client",
        "${net}/response.sse.comment.multi.line/server" })
    public void shouldReceiveResponseCommentMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.comment.initial/client",
        "${net}/response.sse.comment.initial/server" })
    public void shouldReceiveResponseCommentInitial() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.comment.idle/client",
        "${net}/response.sse.comment.idle/server" })
    public void shouldReceiveResponseCommentIdle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.challenge/client",
        "${net}/response.sse.challenge/server" })
    public void shouldChallengeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.valid/client",
        "${net}/response.sse.data.valid/server" })
    public void shouldReceiveResponseDataValid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.invalid.reset/client",
        "${net}/response.sse.data.invalid.reset/server" })
    public void shouldReceiveResponseDataInvalidReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.invalid.aborted/client",
        "${net}/response.sse.data.invalid.aborted/server" })
    public void shouldReceiveResponseDataInvalidAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.comment.initial.budget/client",
        "${net}/response.sse.comment.initial.budget/server" })
    public void shouldReceiveResponseCommentInitialBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.budget/client",
        "${net}/response.sse.data.budget/server" })
    public void shouldReceiveResponseDataBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.data.null.budget/client",
        "${net}/response.sse.data.null.budget/server" })
    public void shouldReceiveResponseDataNullBudget() throws Exception
    {
        k3po.finish();
    }
}
