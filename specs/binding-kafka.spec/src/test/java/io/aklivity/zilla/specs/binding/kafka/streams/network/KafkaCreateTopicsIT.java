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
package io.aklivity.zilla.specs.binding.kafka.streams.network;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class KafkaCreateTopicsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/api");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/create.topics.v3/client",
        "${net}/create.topics.v3/server"})
    public void shouldCreateTopicsV3() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/create.topics.v3.negotiated/client",
        "${net}/create.topics.v3.negotiated/server"})
    public void shouldCreateTopicsV3Negotiated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/create.topics.v3.unsupported/client",
        "${net}/create.topics.v3.unsupported/server"})
    public void shouldRejectCreateTopicsV3WhenUnsupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/create.topics.v3.reconnect/client",
        "${net}/create.topics.v3.reconnect/server"})
    public void shouldReconnect() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/create.topics.v3.reconnect.sticky/client",
        "${net}/create.topics.v3.reconnect.sticky/server"})
    public void shouldReconnectWithStickyExplicitApiVersions() throws Exception
    {
        k3po.finish();
    }
}
