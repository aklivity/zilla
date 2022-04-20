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

public class HttpIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/http/kafka/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${http}/delete.item/client",
        "${http}/delete.item/server"})
    public void shouldDeleteItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.if.match/client",
        "${http}/delete.item.if.match/server"})
    public void shouldDeleteItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.if.match.failed/client",
        "${http}/delete.item.if.match.failed/server"})
    public void shouldNotDeleteItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.read.abort/client",
        "${http}/delete.item.read.abort/server"})
    public void shouldNotDeleteItemWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.write.abort/client",
        "${http}/delete.item.write.abort/server"})
    public void shouldNotDeleteItemWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.write.flush/client",
        "${http}/delete.item.write.flush/server"})
    public void shouldDeleteItemWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async/client",
        "${http}/delete.item.prefer.async/server"})
    public void shouldDeleteItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async.with.body/client",
        "${http}/delete.item.prefer.async.with.body/server"})
    public void shouldDeleteItemPreferAsyncWithBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async.read.abort/client",
        "${http}/delete.item.prefer.async.read.abort/server"})
    public void shouldNotDeleteItemPreferAsyncWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async.write.abort/client",
        "${http}/delete.item.prefer.async.write.abort/server"})
    public void shouldNotDeleteItemPreferAsyncWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async.write.flush/client",
        "${http}/delete.item.prefer.async.write.flush/server"})
    public void shouldDeleteItemPreferAsyncWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async.delayed/client",
        "${http}/delete.item.prefer.async.delayed/server"})
    public void shouldDeleteItemPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async.wait.delayed/client",
        "${http}/delete.item.prefer.async.wait.delayed/server"})
    public void shouldDeleteItemPreferAsyncWaitDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.item.prefer.async.ignored/client",
        "${http}/delete.item.prefer.async.ignored/server"})
    public void shouldDeleteItemPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item/client",
        "${http}/get.item/server"})
    public void shouldGetItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.with.body/client",
        "${http}/get.item.with.body/server"})
    public void shouldGetItemWithBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.empty/client",
        "${http}/get.item.empty/server"})
    public void shouldGetItemWhenEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.implicit.etag/client",
        "${http}/get.item.implicit.etag/server"})
    public void shouldGetItemWithImplicitEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.read.abort/client",
        "${http}/get.item.read.abort/server"})
    public void shouldNotGetItemWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.write.abort/client",
        "${http}/get.item.write.abort/server"})
    public void shouldNotGetItemWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.write.flush/client",
        "${http}/get.item.write.flush/server"})
    public void shouldGetItemWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.if.none.match/client",
        "${http}/get.item.if.none.match/server"})
    public void shouldGetItemIfNoneMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.implicit.etag.if.none.match/client",
        "${http}/get.item.implicit.etag.if.none.match/server"})
    public void shouldGetItemWithImplicitEtagIfNoneMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.if.none.match.not.modified/client",
        "${http}/get.item.if.none.match.not.modified/server"})
    public void shouldNotGetItemIfNoneMatchNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.not.found/client",
        "${http}/get.item.not.found/server"})
    public void shouldNotGetItemNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.prefer.wait/client",
        "${http}/get.item.prefer.wait/server"})
    public void shouldGetItemPreferWait() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.item.prefer.wait.not.found/client",
        "${http}/get.item.prefer.wait.not.found/server"})
    public void shouldNotGetItemPreferWaitNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.items/client",
        "${http}/get.items/server"})
    public void shouldGetItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/get.items.prefer.wait/client",
        "${http}/get.items.prefer.wait/server"})
    public void shouldGetItemsPreferWait() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/patch.item/client",
        "${http}/patch.item/server"})
    public void shouldPatchItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/patch.item.if.match/client",
        "${http}/patch.item.if.match/server"})
    public void shouldPatchItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/patch.item.if.match.failed/client",
        "${http}/patch.item.if.match.failed/server"})
    public void shouldNotPatchItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/patch.item.prefer.async/client",
        "${http}/patch.item.prefer.async/server"})
    public void shouldPatchItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/patch.item.prefer.async.delayed/client",
        "${http}/patch.item.prefer.async.delayed/server"})
    public void shouldPatchItemPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/patch.item.prefer.async.ignored/client",
        "${http}/patch.item.prefer.async.ignored/server"})
    public void shouldPatchItemPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.item.command/client",
        "${http}/post.item.command/server"})
    public void shouldPostItemCommand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.item.command.if.match/client",
        "${http}/post.item.command.if.match/server"})
    public void shouldPostItemCommandIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.item.command.if.match.failed/client",
        "${http}/post.item.command.if.match.failed/server"})
    public void shouldNotPostItemCommandIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.item.command.prefer.async/client",
        "${http}/post.item.command.prefer.async/server"})
    public void shouldPostItemCommandPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.item.command.prefer.async.delayed/client",
        "${http}/post.item.command.prefer.async.delayed/server"})
    public void shouldPostItemCommandPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.item.command.prefer.async.ignored/client",
        "${http}/post.item.command.prefer.async.ignored/server"})
    public void shouldPostItemCommandPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.items/client",
        "${http}/post.items/server"})
    public void shouldPostItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.items.prefer.async/client",
        "${http}/post.items.prefer.async/server"})
    public void shouldPostItemsPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.items.prefer.async.delayed/client",
        "${http}/post.items.prefer.async.delayed/server"})
    public void shouldPostItemsPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/post.items.prefer.async.ignored/client",
        "${http}/post.items.prefer.async.ignored/server"})
    public void shouldPostItemsPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/put.item/client",
        "${http}/put.item/server"})
    public void shouldPutItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/put.item.if.match/client",
        "${http}/put.item.if.match/server"})
    public void shouldPutItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/put.item.if.match.failed/client",
        "${http}/put.item.if.match.failed/server"})
    public void shouldNotPutItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/put.item.prefer.async/client",
        "${http}/put.item.prefer.async/server"})
    public void shouldPutItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/put.item.prefer.async.delayed/client",
        "${http}/put.item.prefer.async.delayed/server"})
    public void shouldPutItemPreferAyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/put.item.prefer.async.ignored/client",
        "${http}/put.item.prefer.async.ignored/server"})
    public void shouldPutItemPreferAyncIgnored() throws Exception
    {
        k3po.finish();
    }
}
