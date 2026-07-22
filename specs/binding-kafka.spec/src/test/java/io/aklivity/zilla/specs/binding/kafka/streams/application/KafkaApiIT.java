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
package io.aklivity.zilla.specs.binding.kafka.streams.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class KafkaApiIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/api");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/create.topics.v3/client",
        "${app}/create.topics.v3/server"})
    public void shouldCreateTopicsV3() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/delete.topics.v3/client",
        "${app}/delete.topics.v3/server"})
    public void shouldDeleteTopicsV3() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.topics.v3.unsupported.version/client",
        "${app}/create.topics.v3.unsupported.version/server"})
    public void shouldCreateTopicsV3UnsupportedVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.topics.v3.reused.connection/client",
        "${app}/create.topics.v3.reused.connection/server"})
    public void shouldCreateTopicsV3ReusedConnection() throws Exception
    {
        k3po.finish();
    }
}
