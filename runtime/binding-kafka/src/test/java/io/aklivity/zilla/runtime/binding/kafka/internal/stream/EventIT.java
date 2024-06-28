/*
 * Copyright 2021-2023 Aklivity Inc.
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

public class EventIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/kafka/streams/network/group.f1.j5.s3.l3.h3")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/kafka/streams/application/group");

    private final TestRule timeout = new DisableOnDebug(new Timeout(15, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure("zilla.binding.kafka.client.instance.id",
            "io.aklivity.zilla.runtime.binding.kafka.internal.stream.EventIT::supplyInstanceId")
        .configurationRoot("io/aklivity/zilla/specs/binding/kafka/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.event.api.version.rejected.yaml")
    @Specification({
        "${app}/invalid.describe.config/client",
        "${net}/invalid.describe.config/server"})
    public void shouldHandleInvalidDescribeConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.event.cluster.authorization.failed.yaml")
    @Specification({
        "${app}/invalid.describe.config.cluster.authorization.error/client",
        "${net}/invalid.describe.config.cluster.authorization.error/server"})
    public void shouldHandleInvalidDescribeConfigClusterAuthorizationError() throws Exception
    {
        k3po.finish();
    }

    public static String supplyInstanceId()
    {
        return "zilla";
    }
}
