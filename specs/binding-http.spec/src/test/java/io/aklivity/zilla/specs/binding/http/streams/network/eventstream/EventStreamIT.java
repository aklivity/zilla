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
        "${net}/response.sse.framing.event/client",
        "${net}/response.sse.framing.event/server" })
    public void shouldReceiveResponseEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.non.empty/client",
        "${net}/response.sse.framing.data.non.empty/server" })
    public void shouldReceiveResponseDataNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.empty/client",
        "${net}/response.sse.framing.data.empty/server" })
    public void shouldReceiveResponseDataEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.multi.line/client",
        "${net}/response.sse.framing.data.multi.line/server" })
    public void shouldReceiveResponseDataMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.multiple/client",
        "${net}/response.sse.framing.data.multiple/server" })
    public void shouldReceiveResponseDataMultiple() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.fragmented.10k/client",
        "${net}/response.sse.framing.data.fragmented.10k/server" })
    public void shouldReceiveResponseDataFragmented10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.fragmented.100k/client",
        "${net}/response.sse.framing.data.fragmented.100k/server" })
    public void shouldReceiveResponseDataFragmented100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.initial.whitespace/client",
        "${net}/response.sse.framing.data.initial.whitespace/server" })
    public void shouldReceiveResponseDataInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.name.only/client",
        "${net}/response.sse.framing.data.name.only/server" })
    public void shouldReceiveResponseDataNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.invalid.utf8/client",
        "${net}/response.sse.framing.data.invalid.utf8/server" })
    public void shouldReceiveResponseDataInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.bom.empty/client",
        "${net}/response.sse.framing.bom.empty/server" })
    public void shouldReceiveResponseBomEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.bom.non.empty/client",
        "${net}/response.sse.framing.bom.non.empty/server" })
    public void shouldReceiveResponseBomNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.end.of.line.line.feed/client",
        "${net}/response.sse.framing.end.of.line.line.feed/server" })
    public void shouldReceiveResponseEndOfLineLineFeed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.end.of.line.carriage.return/client",
        "${net}/response.sse.framing.end.of.line.carriage.return/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.end.of.line.carriage.return.line.feed/client",
        "${net}/response.sse.framing.end.of.line.carriage.return.line.feed/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturnLineFeed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.end.of.line.carriage.return.line.feed.fragmented/client",
        "${net}/response.sse.framing.end.of.line.carriage.return.line.feed.fragmented/server" })
    public void shouldReceiveResponseEndOfLineCarriageReturnLineFeedFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.id.non.empty/client",
        "${net}/response.sse.framing.id.non.empty/server" })
    public void shouldReceiveResponseIdNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.id.empty/client",
        "${net}/response.sse.framing.id.empty/server" })
    public void shouldReceiveResponseIdEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.id.initial.whitespace/client",
        "${net}/response.sse.framing.id.initial.whitespace/server" })
    public void shouldReceiveResponseIdInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.id.invalid.utf8/client",
        "${net}/response.sse.framing.id.invalid.utf8/server" })
    public void shouldReceiveResponseIdInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.non.empty/client",
        "${net}/response.sse.framing.type.non.empty/server" })
    public void shouldReceiveResponseTypeNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.empty/client",
        "${net}/response.sse.framing.type.empty/server" })
    public void shouldReceiveResponseTypeEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.fragmented/client",
        "${net}/response.sse.framing.type.fragmented/server" })
    public void shouldReceiveResponseTypeFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.initial.whitespace/client",
        "${net}/response.sse.framing.type.initial.whitespace/server" })
    public void shouldReceiveResponseTypeInitialWhitespace() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.name.only/client",
        "${net}/response.sse.framing.type.name.only/server" })
    public void shouldReceiveResponseTypeNameOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.invalid.utf8/client",
        "${net}/response.sse.framing.type.invalid.utf8/server" })
    public void shouldReceiveResponseTypeInvalidUtf8() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.non.empty.interleaved/client",
        "${net}/response.sse.framing.type.non.empty.interleaved/server" })
    public void shouldReceiveResponseTypeNonEmptyInterleaved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.type.non.empty.trailing/client",
        "${net}/response.sse.framing.type.non.empty.trailing/server" })
    public void shouldReceiveResponseTypeNonEmptyTrailing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse/client",
        "${net}/response.sse/server" })
    public void shouldReceiveResponseRaw() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.id.only/client",
        "${net}/response.sse.framing.id.only/server" })
    public void shouldReceiveResponseIdOnly() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.comment.empty/client",
        "${net}/response.sse.framing.comment.empty/server" })
    public void shouldReceiveResponseCommentEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.comment.non.empty/client",
        "${net}/response.sse.framing.comment.non.empty/server" })
    public void shouldReceiveResponseCommentNonEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.comment.multi.line/client",
        "${net}/response.sse.framing.comment.multi.line/server" })
    public void shouldReceiveResponseCommentMultiLine() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.comment.initial/client",
        "${net}/response.sse.framing.comment.initial/server" })
    public void shouldReceiveResponseCommentInitial() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.comment.idle/client",
        "${net}/response.sse.framing.comment.idle/server" })
    public void shouldReceiveResponseCommentIdle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.challenge/client",
        "${net}/response.sse.framing.challenge/server" })
    public void shouldChallengeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.valid/client",
        "${net}/response.sse.framing.data.valid/server" })
    public void shouldReceiveResponseDataValid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.invalid.reset/client",
        "${net}/response.sse.framing.data.invalid.reset/server" })
    public void shouldReceiveResponseDataInvalidReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.invalid.aborted/client",
        "${net}/response.sse.framing.data.invalid.aborted/server" })
    public void shouldReceiveResponseDataInvalidAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.comment.initial.budget/client",
        "${net}/response.sse.framing.comment.initial.budget/server" })
    public void shouldReceiveResponseCommentInitialBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.budget/client",
        "${net}/response.sse.framing.data.budget/server" })
    public void shouldReceiveResponseDataBudget() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.sse.framing.data.null.budget/client",
        "${net}/response.sse.framing.data.null.budget/server" })
    public void shouldReceiveResponseDataNullBudget() throws Exception
    {
        k3po.finish();
    }
}
