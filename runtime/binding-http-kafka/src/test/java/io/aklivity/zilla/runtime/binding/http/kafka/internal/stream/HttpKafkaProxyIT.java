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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.stream;

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

public class HttpKafkaProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/http/kafka/streams/http")
        .addScriptRoot("kafka", "io/aklivity/zilla/specs/binding/http/kafka/streams/kafka");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/kafka/config")
        .external("kafka0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.no.reply.yaml")
    @Specification({
        "${http}/delete.item/client",
        "${kafka}/delete.item.no.reply/server"})
    public void shouldDeleteItemNoReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.no.reply.yaml")
    @Specification({
        "${http}/delete.item.rejected/client",
        "${kafka}/delete.item.rejected.no.reply/server"})
    public void shouldRejectDeleteItemNoReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItemSync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.rejected/client",
        "${kafka}/delete.item.rejected/server"})
    public void shouldRejectDeleteItemSync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item/client",
        "${kafka}/delete.item.repeated/server"})
    public void shouldDeleteItemRepeated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item.if.match/client",
        "${kafka}/delete.item.if.match/server"})
    public void shouldDeleteItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item.if.match.failed/client",
        "${kafka}/delete.item.if.match.failed/server"})
    public void shouldNotDeleteItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item.if.match.no.etag/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItemIfMatchNoEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item.read.abort/client",
        "${kafka}/delete.item.read.abort/server"})
    public void shouldNotDeleteItemWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item.write.abort/client",
        "${kafka}/delete.item.write.abort/server"})
    public void shouldNotDeleteItemWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item.write.flush/client",
        "${kafka}/delete.item.write.flush/server"})
    public void shouldDeleteItemWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.rejected/client",
        "${kafka}/delete.item.rejected/server"})
    public void shouldRejectDeleteItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async/client",
        "${kafka}/delete.item.async/server"})
    public void shouldDeleteItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.with.body/client",
        "${kafka}/delete.item.async/server"})
    public void shouldDeleteItemPreferAsyncWithBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.read.abort/client",
        "${kafka}/delete.item.async.read.abort/server"})
    public void shouldNotDeleteItemPreferAsyncWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.write.abort/client",
        "${kafka}/delete.item.write.abort/server"})
    public void shouldNotDeleteItemPreferAsyncWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.write.flush/client",
        "${kafka}/delete.item.async.write.flush/server"})
    public void shouldDeleteItemPreferAsyncWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.delayed/client",
        "${kafka}/delete.item.delayed/server"})
    public void shouldDeleteItemPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.wait.delayed/client",
        "${kafka}/delete.item.wait.delayed/server"})
    public void shouldDeleteItemPreferAsyncWaitDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.yaml")
    @Specification({
        "${http}/delete.item.prefer.async.ignored/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItemPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item/client",
        "${kafka}/get.item/server"})
    public void shouldGetItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item/client",
        "${kafka}/get.item.modifying/server"})
    public void shouldGetItemModifying() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.with.body/client",
        "${kafka}/get.item/server"})
    public void shouldGetItemWithBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.empty/client",
        "${kafka}/get.item.empty/server"})
    public void shouldGetItemWhenEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.implicit.etag/client",
        "${kafka}/get.item.no.etag/server"})
    public void shouldGetItemWithImplicitEtag() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.read.abort/client",
        "${kafka}/get.item.read.abort/server"})
    public void shouldNotGetItemWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.write.abort/client",
        "${kafka}/get.item.write.abort/server"})
    public void shouldNotGetItemWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.write.flush/client",
        "${kafka}/get.item.write.flush/server"})
    public void shouldGetItemWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.if.none.match/client",
        "${kafka}/get.item.modified/server"})
    public void shouldGetItemIfNoneMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.implicit.etag.if.none.match/client",
        "${kafka}/get.item.no.etag.modified/server"})
    public void shouldGetItemWithImplicitEtagIfNoneMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.if.none.match.not.modified/client",
        "${kafka}/get.item.not.modified/server"})
    public void shouldNotGetItemIfNoneMatchNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.not.found/client",
        "${kafka}/get.item.not.found/server"})
    public void shouldNotGetItemNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.not.found/client",
        "${kafka}/get.item.deleted/server"})
    public void shouldNotGetItemDeleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.prefer.wait/client",
        "${kafka}/get.item.wait/server"})
    public void shouldGetItemPreferWait() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.yaml")
    @Specification({
        "${http}/get.item.prefer.wait.not.found/client",
        "${kafka}/get.item.wait.timeout/server"})
    public void shouldNotGetItemPreferWaitNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.child.yaml")
    @Specification({
        "${http}/get.item.child/client",
        "${kafka}/get.item.child/server"})
    public void shouldGetItemChild() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items/client",
        "${kafka}/get.items/server"})
    public void shouldGetItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.empty/client",
        "${kafka}/get.items.empty/server"})
    public void shouldGetItemsEmpty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.with.body/client",
        "${kafka}/get.items/server"})
    public void shouldGetItemsWithBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.if.none.match/client",
        "${kafka}/get.items.modified/server"})
    public void shouldGetItemsIfNoneMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.if.none.match.not.modified/client",
        "${kafka}/get.items.not.modified/server"})
    public void shouldNotGetItemsIfNoneMatchNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.prefer.wait/client",
        "${kafka}/get.items/server"})
    public void shouldGetItemsPreferWait() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.prefer.wait.not.modified/client",
        "${kafka}/get.items.not.modified/server"})
    public void shouldNotGetItemsPreferWaitNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.read.abort/client",
        "${kafka}/get.items.read.abort/server"})
    public void shouldNotGetItemsWhenReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.write.abort/client",
        "${kafka}/get.items.write.abort/server"})
    public void shouldNotGetItemsWhenWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.items.yaml")
    @Specification({
        "${http}/get.items.write.flush/client",
        "${kafka}/get.items.write.flush/server"})
    public void shouldGetItemsWriteFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.yaml")
    @Specification({
        "${http}/patch.item/client",
        "${kafka}/patch.item/server"})
    public void shouldPatchItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.yaml")
    @Specification({
        "${http}/patch.item.if.match/client",
        "${kafka}/patch.item.if.match/server"})
    public void shouldPatchItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.yaml")
    @Specification({
        "${http}/patch.item.if.match.failed/client",
        "${kafka}/patch.item.if.match.failed/server"})
    public void shouldNotPatchItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.async.yaml")
    @Specification({
        "${http}/patch.item.prefer.async/client",
        "${kafka}/patch.item.async/server"})
    public void shouldPatchItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.async.yaml")
    @Specification({
        "${http}/patch.item.prefer.async.delayed/client",
        "${kafka}/patch.item.delayed/server"})
    public void shouldPatchItemPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.yaml")
    @Specification({
        "${http}/patch.item.prefer.async.ignored/client",
        "${kafka}/patch.item/server"})
    public void shouldPatchItemPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.yaml")
    @Specification({
        "${http}/post.item.command/client",
        "${kafka}/post.item.command/server"})
    public void shouldPostItemCommand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.yaml")
    @Specification({
        "${http}/post.item.command/client",
        "${kafka}/post.item.command.replayed/server"})
    public void shouldPostItemCommandReplayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.yaml")
    @Specification({
        "${http}/post.item.command.if.match/client",
        "${kafka}/post.item.command.if.match/server"})
    public void shouldPostItemCommandIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.yaml")
    @Specification({
        "${http}/post.item.command.if.match.failed/client",
        "${kafka}/post.item.command.if.match.failed/server"})
    public void shouldNotPostItemCommandIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.async.yaml")
    @Specification({
        "${http}/post.item.command.prefer.async/client",
        "${kafka}/post.item.async.command/server"})
    public void shouldPostItemCommandPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.async.yaml")
    @Specification({
        "${http}/post.item.command.prefer.async/client",
        "${kafka}/post.item.command.async.replayed/server"})
    public void shouldPostItemCommandPreferAsyncReplayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.async.yaml")
    @Specification({
        "${http}/post.item.command.prefer.async.delayed/client",
        "${kafka}/post.item.command.delayed/server"})
    public void shouldPostItemCommandPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.yaml")
    @Specification({
        "${http}/post.item.command.prefer.async.ignored/client",
        "${kafka}/post.item.command/server"})
    public void shouldPostItemCommandPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.yaml")
    @Specification({
        "${http}/post.items/client",
        "${kafka}/post.items/server"})
    public void shouldPostItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.async.yaml")
    @Specification({
        "${http}/post.items.prefer.async/client",
        "${kafka}/post.items.async/server"})
    public void shouldPostItemsPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.async.yaml")
    @Specification({
        "${http}/post.items.prefer.async.delayed/client",
        "${kafka}/post.items.async.delayed/server"})
    public void shouldPostItemsPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.yaml")
    @Specification({
        "${http}/post.items.prefer.async.ignored/client",
        "${http}/post.items/server"})
    public void shouldPostItemsPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.yaml")
    @Specification({
        "${http}/put.item/client",
        "${kafka}/put.item/server"})
    public void shouldPutItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.no.reply.yaml")
    @Specification({
        "${http}/put.item/client",
        "${kafka}/put.item.no.reply/server"})
    public void shouldPutItemNoReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.yaml")
    @Specification({
        "${http}/put.item.if.match/client",
        "${kafka}/put.item.if.match/server"})
    public void shouldPutItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.yaml")
    @Specification({
        "${http}/put.item.if.match.failed/client",
        "${kafka}/put.item.if.match.failed/server"})
    public void shouldNotPutItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.async.yaml")
    @Specification({
        "${http}/put.item.prefer.async/client",
        "${kafka}/put.item.async/server"})
    public void shouldPutItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.async.yaml")
    @Specification({
        "${http}/put.item.prefer.async.delayed/client",
        "${kafka}/put.item.delayed/server"})
    public void shouldPutItemPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.yaml")
    @Specification({
        "${http}/put.item.prefer.async.ignored/client",
        "${kafka}/put.item/server"})
    public void shouldPutItemPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }
}
