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
package io.aklivity.zilla.runtime.binding.a2a.openapi.internal.stream;

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
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class A2aOpenapiProxyIT
{
    private static final String TASK_ID_NAME = "zilla.binding.a2a.http.task.id";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/openapi/streams/a2a")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/a2a/openapi/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(TASK_ID_NAME, "%s::taskId".formatted(A2aOpenapiProxyIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/openapi/config")
        .external("http0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    public static String taskId(
        long affinity)
    {
        assert affinity == 0L;
        return "5ca1ab1e-c0de-4a11-5e55-000100000000";
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/read.order/client",
        "${http}/read.order/server"})
    public void shouldReadOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/intent.select/client",
        "${http}/intent.select/server"})
    public void shouldSelectSkillByIntent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/read.order.10k/client",
        "${http}/read.order.10k/server"})
    public void shouldReadOrder10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/read.order.aborted/client",
        "${http}/read.order.aborted/server"})
    public void shouldAbortReadOrderWhenUpstreamAborted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/read.order.error/client",
        "${http}/read.order.error/server"})
    public void shouldFailReadOrderOnUpstreamError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.exit.yaml")
    @Specification({
        "${app}/read.order/client",
        "${http}/read.order/server"})
    public void shouldReadOrderWhenExitDeclaredOnBinding() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.credentials.yaml")
    @ScriptProperty("authorization 1L")
    @Specification({
        "${app}/read.order.credentials/client",
        "${http}/read.order.credentials/server"})
    public void shouldInjectCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/create.pr/client",
        "${http}/create.pr/server"})
    public void shouldCreatePr() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/search.items/client",
        "${http}/search.items/server"})
    public void shouldSearchItems() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/search.items.with.limit/client",
        "${http}/search.items.with.limit/server"})
    public void shouldSearchItemsWithLimit() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guarded.yaml")
    @Specification({
        "${app}/search.code.forbidden/client"})
    public void shouldRejectSearchCodeWhenForbidden() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/skills.card/client"})
    public void shouldServeSkillsCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.sse.yaml")
    @Specification({
        "${app}/skills.card.streaming/client"})
    public void shouldServeSkillsCardStreaming() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.sse.yaml")
    @Specification({
        "${app}/stream.sse/client",
        "${http}/stream.sse/server"})
    public void shouldStreamEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/input.required/client"})
    public void shouldRequireInputWhenArgumentsMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/intent.no.match/client"})
    public void shouldRejectWhenIntentNoMatch() throws Exception
    {
        k3po.finish();
    }
}
