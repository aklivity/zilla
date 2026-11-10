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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_BOOTSTRAP;
import static io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration.KAFKA_CACHE_SERVER_RECONNECT_DELAY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.ScriptProperty;
import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfigurationTest;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class CacheShareIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/share.cache");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(KAFKA_CACHE_SERVER_BOOTSTRAP, false)
        .configure(KAFKA_CACHE_SERVER_RECONNECT_DELAY, 0)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("app1")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/receive.message.value/client",
        "${app}/member.receive.message.value/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReceiveMessageValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/receive.message.value.10k/client",
        "${app}/member.receive.message.value.10k/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReceiveMessageValue10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/receive.message.key.with.header/client",
        "${app}/member.receive.message.key.with.header/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReceiveMessageKeyWithHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/receive.message.values.outside.acquired/client",
        "${app}/member.receive.message.values.outside.acquired/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldReceiveMessageValuesOutsideAcquired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/distribute.messages.two.streams/client",
        "${app}/member.distribute.messages.two.streams/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldDistributeMessagesTwoStreams() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/distribute.messages.window/client",
        "${app}/member.distribute.messages.window/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldDistributeMessagesWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/reroute.messages.stream.abort/client",
        "${app}/member.reroute.messages.stream.abort/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRerouteMessagesStreamAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/reroute.messages.stream.end/client",
        "${app}/member.reroute.messages.stream.end/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRerouteMessagesStreamEnd() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/leave.release.unacknowledged/client",
        "${app}/member.leave.release.unacknowledged/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLeaveReleaseUnacknowledged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/leave.release.staged/client",
        "${app}/member.leave.release.staged/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldLeaveReleaseStaged() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/filter.key/client",
        "${app}/member.filter.key/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFilterKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/filter.key.skip.all/client",
        "${app}/member.filter.key.skip.all/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFilterKeySkipAll() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/filter.header/client",
        "${app}/member.filter.header/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldFilterHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/reject.filter.mismatch/client",
        "${app}/member.reject.filter.mismatch/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectFilterMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/reject.topic.mismatch/client",
        "${app}/member.reject.topic.mismatch/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldRejectTopicMismatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/acknowledge.invalid.record.state/client",
        "${app}/member.acknowledge.invalid.record.state/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAcknowledgeInvalidRecordState() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/acknowledge.unknown.record/client",
        "${app}/member.acknowledge.unknown.record/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldAcknowledgeUnknownRecord() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/expire.lock/client",
        "${app}/member.expire.lock/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldExpireLock() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/skip.lock.margin/client",
        "${app}/member.skip.lock.margin/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_SHARE_LOCK_MARGIN_PERCENT_NAME, value = "100")
    public void shouldSkipLockMargin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/limit.staging.bytes/client",
        "${app}/member.limit.staging.bytes/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    @Configure(name = KafkaConfigurationTest.KAFKA_CACHE_SERVER_SHARE_STAGING_MAX_BYTES_NAME, value = "1024")
    public void shouldLimitStagingBytes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("cache.yaml")
    @Specification({
        "${app}/server.sent.abort.after.data/client",
        "${app}/member.server.sent.abort.after.data/server"})
    @ScriptProperty("serverAddress \"zilla://streams/app1\"")
    public void shouldServerSentAbortAfterData() throws Exception
    {
        k3po.finish();
    }
}
