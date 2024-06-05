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
package io.aklivity.zilla.specs.binding.asyncapi.streams.asyncapi;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class AsyncapiIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("asyncapi", "io/aklivity/zilla/specs/binding/asyncapi/streams/asyncapi");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${asyncapi}/mqtt/publish.and.subscribe/client",
        "${asyncapi}/mqtt/publish.and.subscribe/server"
    })
    public void shouldPublishAndSubscribe() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${asyncapi}/http/create.pet/client",
        "${asyncapi}/http/create.pet/server"
    })
    public void shouldCreatePet() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${asyncapi}/sse/data.multiple/client",
        "${asyncapi}/sse/data.multiple/server"
    })
    public void shouldReceiveMultipleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${asyncapi}/kafka/produce.message/client",
        "${asyncapi}/kafka/produce.message/server"
    })
    public void shouldProduceMessage() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${asyncapi}/proxy.kafka.publish/client",
        "${asyncapi}/proxy.kafka.publish/server"
    })
    public void shouldProxyPublishMessageKafka() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${asyncapi}/proxy.mqtt.publish/client",
        "${asyncapi}/proxy.mqtt.publish/server"
    })
    public void shouldProxyPublishMessageMqtt() throws Exception
    {
        k3po.start();
        k3po.notifyBarrier("WILL_STREAM_STARTED");
        k3po.finish();
    }
}
