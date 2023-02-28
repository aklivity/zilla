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
package io.aklivity.zilla.specs.binding.http.kafka.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class KafkaIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/http/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${kafka}/delete.item/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.no.reply/client",
        "${kafka}/delete.item.no.reply/server"})
    public void shouldDeleteItemNoReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.rejected.no.reply/client",
        "${kafka}/delete.item.rejected.no.reply/server"})
    public void shouldRejectDeleteItemNoReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.rejected/client",
        "${kafka}/delete.item.rejected/server"})
    public void shouldRejectDeleteItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.delayed/client",
        "${kafka}/delete.item.delayed/server"})
    public void shouldDeleteItemDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.wait.delayed/client",
        "${kafka}/delete.item.wait.delayed/server"})
    public void shouldDeleteItemWaitDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.if.match/client",
        "${kafka}/delete.item.if.match/server"})
    public void shouldDeleteItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.if.match.failed/client",
        "${kafka}/delete.item.if.match.failed/server"})
    public void shouldNotDeleteItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.repeated/client",
        "${kafka}/delete.item.repeated/server"})
    public void shouldDeleteItemRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.read.abort/client",
        "${kafka}/delete.item.read.abort/server"})
    public void shouldNotDeleteItemWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.write.abort/client",
        "${kafka}/delete.item.write.abort/server"})
    public void shouldNotDeleteItemAsyncWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.async.read.abort/client",
        "${kafka}/delete.item.async.read.abort/server"})
    public void shouldNotDeleteItemAsyncWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.async.write.flush/client",
        "${kafka}/delete.item.async.write.flush/server"})
    public void shouldDeleteItemAsyncWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/delete.item.write.flush/client",
        "${kafka}/delete.item.write.flush/server"})
    public void shouldDeleteItemWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item/client",
        "${kafka}/get.item/server"})
    public void shouldGetItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.empty/client",
        "${kafka}/get.item.empty/server"})
    public void shouldGetItemWhenEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.no.etag/client",
        "${kafka}/get.item.no.etag/server"})
    public void shouldGetItemWithNoEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.read.abort/client",
        "${kafka}/get.item.read.abort/server"})
    public void shouldNotGetItemWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.write.abort/client",
        "${kafka}/get.item.write.abort/server"})
    public void shouldNotGetItemWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.write.flush/client",
        "${kafka}/get.item.write.flush/server"})
    public void shouldGetItemWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.deleted/client",
        "${kafka}/get.item.deleted/server"})
    public void shouldNotGetItemDeleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.modifying/client",
        "${kafka}/get.item.modifying/server"})
    public void shouldGetItemModifying() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.modified/client",
        "${kafka}/get.item.modified/server"})
    public void shouldGetItemModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.no.etag.modified/client",
        "${kafka}/get.item.no.etag.modified/server"})
    public void shouldGetItemWithNoEtagModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.not.found/client",
        "${kafka}/get.item.not.found/server"})
    public void shouldNotGetItemNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.not.modified/client",
        "${kafka}/get.item.not.modified/server"})
    public void shouldNotGetItemNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.wait/client",
        "${kafka}/get.item.wait/server"})
    public void shouldGetItemWait() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.wait.timeout/client",
        "${kafka}/get.item.wait.timeout/server"})
    public void shouldNotGetItemWaitTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.item.child/client",
        "${kafka}/get.item.child/server"})
    public void shouldGetItemChild() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.items/client",
        "${kafka}/get.items/server"})
    public void shouldGetItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.items.empty/client",
        "${kafka}/get.items.empty/server"})
    public void shouldGetItemsEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.items.modified/client",
        "${kafka}/get.items.modified/server"})
    public void shouldGetItemsModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.items.not.modified/client",
        "${kafka}/get.items.not.modified/server"})
    public void shouldNotGetItemsNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.items.read.abort/client",
        "${kafka}/get.items.read.abort/server"})
    public void shouldNotGetItemsWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.items.write.abort/client",
        "${kafka}/get.items.write.abort/server"})
    public void shouldNotGetItemsWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/get.items.write.flush/client",
        "${kafka}/get.items.write.flush/server"})
    public void shouldGetItemsWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/patch.item/client",
        "${kafka}/patch.item/server"})
    public void shouldPatchItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/patch.item.async/client",
        "${kafka}/patch.item.async/server"})
    public void shouldPatchItemAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/patch.item.delayed/client",
        "${kafka}/patch.item.delayed/server"})
    public void shouldPatchItemDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/patch.item.if.match/client",
        "${kafka}/patch.item.if.match/server"})
    public void shouldPatchItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/patch.item.if.match.failed/client",
        "${kafka}/patch.item.if.match.failed/server"})
    public void shouldNotPatchItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.item.command/client",
        "${kafka}/post.item.command/server"})
    public void shouldPostItemCommand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.item.async.command/client",
        "${kafka}/post.item.async.command/server"})
    public void shouldPostItemAsyncCommand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.item.command.replayed/client",
        "${kafka}/post.item.command.replayed/server"})
    public void shouldPostItemCommandReplayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.item.command.delayed/client",
        "${kafka}/post.item.command.delayed/server"})
    public void shouldPostItemCommandDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.item.command.if.match/client",
        "${kafka}/post.item.command.if.match/server"})
    public void shouldPostItemCommandIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.item.command.if.match.failed/client",
        "${kafka}/post.item.command.if.match.failed/server"})
    public void shouldNotPostItemCommandIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.items/client",
        "${kafka}/post.items/server"})
    public void shouldPostItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/post.items.delayed/client",
        "${kafka}/post.items.delayed/server"})
    public void shouldPostItemsDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/put.item/client",
        "${kafka}/put.item/server"})
    public void shouldPutItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/put.item.async/client",
        "${kafka}/put.item.async/server"})
    public void shouldPutItemAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/put.item.no.reply/client",
        "${kafka}/put.item.no.reply/server"})
    public void shouldPutItemNoReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/put.item.delayed/client",
        "${kafka}/put.item.delayed/server"})
    public void shouldPutItemDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/put.item.if.match/client",
        "${kafka}/put.item.if.match/server"})
    public void shouldPutItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${kafka}/put.item.if.match.failed/client",
        "${kafka}/put.item.if.match.failed/server"})
    public void shouldNotPutItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }
}
