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
        "${kafka}/delete.item.delayed/client",
        "${kafka}/delete.item.delayed/server"})
    public void shouldDeleteItemDelayed() throws Exception
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
        "${kafka}/get.item/client",
        "${kafka}/get.item/server"})
    public void shouldGetItem() throws Exception
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
        "${kafka}/get.item.modified/client",
        "${kafka}/get.item.modified/server"})
    public void shouldGetItemModified() throws Exception
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
        "${kafka}/get.items/client",
        "${kafka}/get.items/server"})
    public void shouldGetItems() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("SEND_ASYNC_RESPONSE");
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
        k3po.start();
        k3po.notifyBarrier("SEND_ASYNC_RESPONSE");
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
