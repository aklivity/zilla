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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class KafkaApiIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/api")
        .addScriptRoot("netCreateTopics",
            "io/aklivity/zilla/specs/binding/kafka/streams/network/create.topics.v3.api.versions.v0")
        .addScriptRoot("netDeleteTopics",
            "io/aklivity/zilla/specs/binding/kafka/streams/network/delete.topics.v3.api.versions.v0")
        .addScriptRoot("netCreateTopicsUnsupported",
            "io/aklivity/zilla/specs/binding/kafka/streams/network/create.topics.v3.api.versions.v0.unsupported")
        .addScriptRoot("netCreateTopicsReused",
            "io/aklivity/zilla/specs/binding/kafka/streams/network/create.topics.v3.api.versions.v0.reused")
        .addScriptRoot("netCreateTopicsUnsupportedReactive",
            "io/aklivity/zilla/specs/binding/kafka/streams/network/create.topics.v3.unsupported.version.reactive")
        .addScriptRoot("netCreateTopicsReconnect",
            "io/aklivity/zilla/specs/binding/kafka/streams/network/create.topics.v3.reconnect");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/create.topics.v3/client",
        "${netCreateTopics}/create.topics/server"})
    public void shouldCreateTopicsV3() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/delete.topics.v3/client",
        "${netDeleteTopics}/delete.topics/server"})
    public void shouldDeleteTopicsV3() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/create.topics.v3.unsupported.version/client",
        "${netCreateTopicsUnsupported}/create.topics/server"})
    public void shouldCreateTopicsV3UnsupportedVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/create.topics.v3.reused.connection/client",
        "${netCreateTopicsReused}/create.topics.then.delete.topics/server"})
    public void shouldCreateTopicsV3ReusedConnection() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/create.topics.v3.explicit.api.versions/client",
        "${netCreateTopics}/create.topics/server"})
    public void shouldCreateTopicsV3ExplicitApiVersions() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/create.topics.v3.unsupported.version.reactive/client",
        "${netCreateTopicsUnsupportedReactive}/create.topics/server"})
    public void shouldRejectCreateTopicsV3WhenUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/create.topics.v3.reconnect/client",
        "${netCreateTopicsReconnect}/create.topics.aborted.then.delete.topics/server"})
    public void shouldCreateTopicsV3Reconnect() throws Exception
    {
        k3po.finish();
    }
}
