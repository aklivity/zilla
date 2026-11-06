/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

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

public class A2aServerVersionIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/a2a/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/send.message.v0.3/client",
        "${app}/send.message/server"})
    public void shouldSendMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/stream.message.v0.3/client",
        "${app}/stream.message/server"})
    public void shouldStreamMessageV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/get.task.v0.3/client",
        "${app}/get.task/server"})
    public void shouldGetTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/cancel.task.v0.3/client",
        "${app}/cancel.task/server"})
    public void shouldCancelTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/subscribe.task.v0.3/client",
        "${app}/subscribe.task/server"})
    public void shouldSubscribeTaskV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.create.v0.3/client",
        "${app}/push.config.create/server"})
    public void shouldCreatePushConfigV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.get.v0.3/client",
        "${app}/push.config.get/server"})
    public void shouldGetPushConfigV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.list.v0.3/client",
        "${app}/push.config.list/server"})
    public void shouldListPushConfigsV03() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.url.yaml")
    @Specification({
        "${net}/push.config.delete.v0.3/client",
        "${app}/push.config.delete/server"})
    public void shouldDeletePushConfigV03() throws Exception
    {
        k3po.finish();
    }
}
