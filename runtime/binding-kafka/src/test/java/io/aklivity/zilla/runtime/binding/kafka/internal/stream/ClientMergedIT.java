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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_META_MAX_AGE_MILLIS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CLIENT_PRODUCE_MAX_BYTES;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest.KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ClientMergedIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/unmerged.p3.f5.d0.m5")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/merged");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(KAFKA_CLIENT_META_MAX_AGE_MILLIS, 1000)
        .configure(KAFKA_CLIENT_PRODUCE_MAX_BYTES, 116)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.header/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.not.key/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithNotKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.not.header/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.key.and.not.header/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyAndNotHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.header.and.header/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeaderAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.header.or.header/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeaderOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.key/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.key.and.header/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyAndHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.key.or.header/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithKeyOrHeaderFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.none/client",
        "${net}/unmerged.fetch.filter.none.read.committed/server"})
    public void shouldFetchMergedMessagesWithNoFilter() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE_B2");
        k3po.notifyBarrier("SEND_MESSAGE_A3");
        k3po.notifyBarrier("SEND_MESSAGE_B3");
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.message.values/client",
        "${net}/unmerged.fetch.message.values.read.committed/server"})
    public void shouldFetchMergedMessageValues() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/merged.fetch.message.values/client",
        "${net}/unmerged.fetch.message.values.read.committed/server"})
    public void shouldFetchMergedMessageValuesByDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.none.read.uncommitted/client",
        "${net}/unmerged.fetch.filter.none.read.uncommitted/server"})
    public void shouldFetchMergedMessagesWithIsolationReadUncommitted() throws Exception
    {
        k3po.start();
        k3po.awaitBarrier("RECEIVED_MESSAGE_B2");
        k3po.notifyBarrier("SEND_MESSAGE_A3");
        k3po.notifyBarrier("SEND_MESSAGE_B3");
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.produce.message.values.null/client",
        "${net}/unmerged.produce.message.values.null/server"})
    @Configure(name = KAFKA_CLIENT_PRODUCE_MAX_BYTES_NAME, value = "100")
    public void shouldProduceMergedMessageValuesNull() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.produce.message.values/client",
        "${net}/unmerged.produce.message.values/server"})
    public void shouldProduceMergedMessageValues() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.produce.message.value.10k/client",
        "${net}/unmerged.produce.message.value.10k/server"})
    @Configure(
        name = "zilla.binding.kafka.client.produce.max.bytes",
        value = "200000")
    @ScriptProperty("padding ${512 + 100}")
    public void shouldProduceMergedMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.produce.message.value.100k/client",
        "${net}/unmerged.produce.message.value.100k/server"})
    @Configure(
        name = "zilla.binding.kafka.client.produce.max.bytes",
        value = "200000")
    @ScriptProperty("padding ${512 + 100}")
    public void shouldProduceMergedMessageValue100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/merged.produce.message.values/client",
        "${net}/unmerged.produce.message.values/server"})
    public void shouldProduceMergedMessageValuesByDefault() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.produce.message.values.dynamic/client",
        "${net}/unmerged.produce.message.values.dynamic/server"})
    public void shouldProduceMergedMessageValuesDynamic() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.produce.message.values.dynamic.hashed/client",
        "${net}/unmerged.produce.message.values.dynamic.hashed/server"})
    public void shouldProduceMergedMessageValuesDynamicHashed() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.headers.one/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeadersOneFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.headers.one.empty/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeadersOneEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.headers.many/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeadersManyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.headers.many.empty/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeadersManyEmptyFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.headers.skip.one/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeadersSkipOneFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.headers.skip.two/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeadersSkipTwoFilter() throws Exception
    {
        k3po.finish();
    }

    @Ignore("filtered")
    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.headers.skip.many/client",
        "${net}/unmerged.fetch.filter.none/server"})
    public void shouldFetchMergedMessagesWithHeadersSkipManyFilter() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.options.merged.yaml")
    @Specification({
        "${app}/merged.fetch.filter.sync/client",
        "${net}/unmerged.fetch.filter.sync/server"})
    public void shouldFetchMergedFilterSync() throws Exception
    {
        k3po.finish();
    }
}
