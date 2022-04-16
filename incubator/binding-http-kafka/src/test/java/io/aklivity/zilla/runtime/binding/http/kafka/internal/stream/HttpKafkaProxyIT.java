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

import org.junit.Ignore;
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
    @Configuration("proxy.delete.item.json")
    @Specification({
        "${http}/delete.item/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.json")
    @Specification({
        "${http}/delete.item.if.match/client",
        "${kafka}/delete.item.if.match/server"})
    public void shouldDeleteItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.json")
    @Specification({
        "${http}/delete.item.if.match.failed/client",
        "${kafka}/delete.item.if.match.failed/server"})
    public void shouldNotDeleteItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.json")
    @Specification({
        "${http}/delete.item.prefer.async/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.async.json")
    @Specification({
        "${http}/delete.item.prefer.async.delayed/client",
        "${kafka}/delete.item.delayed/server"})
    public void shouldDeleteItemPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.delete.item.json")
    @Specification({
        "${http}/delete.item.prefer.async.ignored/client",
        "${kafka}/delete.item/server"})
    public void shouldDeleteItemPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Configuration("proxy.get.item.json")
    @Specification({
        "${http}/get.item/client",
        "${kafka}/get.item/server"})
    public void shouldGetItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.json")
    @Specification({
        "${http}/get.item.if.none.match/client",
        "${kafka}/get.item.modified/server"})
    public void shouldGetItemIfNoneMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.json")
    @Specification({
        "${http}/get.item.if.none.match.not.modified/client",
        "${kafka}/get.item.not.modified/server"})
    public void shouldNotGetItemIfNoneMatchNotModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.json")
    @Specification({
        "${http}/get.item.not.found/client",
        "${kafka}/get.item.not.found/server"})
    public void shouldNotGetItemNotFound() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.json")
    @Specification({
        "${http}/get.item.not.found/client",
        "${kafka}/get.item.deleted/server"})
    public void shouldNotGetItemDeleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.json")
    @Specification({
        "${http}/get.item.prefer.wait/client",
        "${kafka}/get.item.wait/server"})
    public void shouldGetItemPreferWait() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.get.item.json")
    @Specification({
        "${http}/get.item.prefer.wait.not.found/client",
        "${kafka}/get.item.wait.timeout/server"})
    public void shouldNotGetItemPreferWaitNotFound() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Not yet implemented")
    @Test
    @Configuration("proxy.get.items.json")
    @Specification({
        "${http}/get.items/client",
        "${kafka}/get.items/server"})
    public void shouldGetItems() throws Exception
    {
        k3po.finish();
    }

    @Ignore("Not yet implemented")
    @Test
    @Configuration("proxy.get.items.json")
    @Specification({
        "${http}/get.items.prefer.wait/client",
        "${kafka}/get.items/server"})
    public void shouldGetItemsPreferWait() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.json")
    @Specification({
        "${http}/patch.item/client",
        "${kafka}/patch.item/server"})
    public void shouldPatchItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.json")
    @Specification({
        "${http}/patch.item.if.match/client",
        "${kafka}/patch.item.if.match/server"})
    public void shouldPatchItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.json")
    @Specification({
        "${http}/patch.item.if.match.failed/client",
        "${kafka}/patch.item.if.match.failed/server"})
    public void shouldNotPatchItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.async.json")
    @Specification({
        "${http}/patch.item.prefer.async/client",
        "${kafka}/patch.item/server"})
    public void shouldPatchItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.async.json")
    @Specification({
        "${http}/patch.item.prefer.async.delayed/client",
        "${kafka}/patch.item.delayed/server"})
    public void shouldPatchItemPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.patch.item.json")
    @Specification({
        "${http}/patch.item.prefer.async.ignored/client",
        "${kafka}/patch.item/server"})
    public void shouldPatchItemPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.json")
    @Specification({
        "${http}/post.item.command/client",
        "${kafka}/post.item.command/server"})
    public void shouldPostItemCommand() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.json")
    @Specification({
        "${http}/post.item.command.if.match/client",
        "${kafka}/post.item.command.if.match/server"})
    public void shouldPostItemCommandIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.json")
    @Specification({
        "${http}/post.item.command.if.match.failed/client",
        "${kafka}/post.item.command.if.match.failed/server"})
    public void shouldNotPostItemCommandIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.async.json")
    @Specification({
        "${http}/post.item.command.prefer.async/client",
        "${kafka}/post.item.command/server"})
    public void shouldPostItemCommandPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.async.json")
    @Specification({
        "${http}/post.item.command.prefer.async.delayed/client",
        "${kafka}/post.item.command.delayed/server"})
    public void shouldPostItemCommandPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.item.command.json")
    @Specification({
        "${http}/post.item.command.prefer.async.ignored/client",
        "${kafka}/post.item.command/server"})
    public void shouldPostItemCommandPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.json")
    @Specification({
        "${http}/post.items/client",
        "${kafka}/post.items/server"})
    public void shouldPostItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.async.json")
    @Specification({
        "${http}/post.items.prefer.async/client",
        "${kafka}/post.items/server"})
    public void shouldPostItemsPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.async.json")
    @Specification({
        "${http}/post.items.prefer.async.delayed/client",
        "${kafka}/post.items.delayed/server"})
    public void shouldPostItemsPreferAsyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.post.items.json")
    @Specification({
        "${http}/post.items.prefer.async.ignored/client",
        "${http}/post.items/server"})
    public void shouldPostItemsPreferAsyncIgnored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.json")
    @Specification({
        "${http}/put.item/client",
        "${kafka}/put.item/server"})
    public void shouldPutItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.json")
    @Specification({
        "${http}/put.item.if.match/client",
        "${kafka}/put.item.if.match/server"})
    public void shouldPutItemIfMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.json")
    @Specification({
        "${http}/put.item.if.match.failed/client",
        "${kafka}/put.item.if.match.failed/server"})
    public void shouldNotPutItemIfMatchFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.async.json")
    @Specification({
        "${http}/put.item.prefer.async/client",
        "${kafka}/put.item/server"})
    public void shouldPutItemPreferAsync() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.async.json")
    @Specification({
        "${http}/put.item.prefer.async.delayed/client",
        "${kafka}/put.item.delayed/server"})
    public void shouldPutItemPreferAyncDelayed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.put.item.json")
    @Specification({
        "${http}/put.item.prefer.async.ignored/client",
        "${kafka}/put.item/server"})
    public void shouldPutItemPreferAyncIgnored() throws Exception
    {
        k3po.finish();
    }
}
