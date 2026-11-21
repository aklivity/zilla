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
package io.aklivity.zilla.runtime.binding.a2a.http.internal.stream;

import static io.aklivity.zilla.runtime.binding.a2a.http.internal.A2aHttpConfigurationTest.A2A_HTTP_TASK_ID_NAME;
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

public class A2aHttpProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/a2a/http/streams/a2a")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/a2a/http/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(A2A_HTTP_TASK_ID_NAME, "%s::taskId".formatted(A2aHttpProxyIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/http/config")
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
        "${app}/get.item/client",
        "${http}/get.item/server"})
    public void shouldGetItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.item.100k/client",
        "${http}/get.item.100k/server"})
    public void shouldGetItem100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.item.error/client",
        "${http}/get.item.error/server"})
    public void shouldFailGetItemOnError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.item.aborted/client",
        "${http}/get.item.aborted/server"})
    public void shouldAbortGetItemOnHttpAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.unresolved.yaml")
    @Specification({
        "${app}/get.item.unresolved/client"})
    public void shouldRejectGetItemWhenExpressionUnresolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guarded.yaml")
    @Specification({
        "${app}/get.item.forbidden/client"})
    public void shouldRejectGetItemWhenForbidden() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.credentials.yaml")
    @ScriptProperty("authorization 1L")
    @Specification({
        "${app}/get.item.credentials/client",
        "${http}/get.item.credentials/server"})
    public void shouldInjectCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/create.item/client",
        "${http}/create.item/server"})
    public void shouldCreateItem() throws Exception
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
        "${app}/intent.no.match/client"})
    public void shouldRejectWhenIntentNoMatch() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/args.missing.input.required/client"})
    public void shouldRequireInputWhenArgumentsMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/args.invalid.input.required/client"})
    public void shouldRequireInputWhenArgumentsInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/input.required.followup.valid/client",
        "${http}/get.item/server"})
    public void shouldCompleteInputRequiredFollowup() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/input.required.followup.unknown.task/client"})
    public void shouldRejectFollowupWhenTaskUnknown() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.card.yaml")
    @Specification({
        "${app}/card.generated/client"})
    public void shouldGenerateAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.sse.yaml")
    @Specification({
        "${app}/stream.sse/client",
        "${http}/stream.sse/server"})
    public void shouldStreamServerSentEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.sse.yaml")
    @Specification({
        "${app}/send.sse.unsupported/client",
        "${http}/send.sse.unsupported/server"})
    public void shouldRejectSendOnServerSentEvents() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.task.unsupported/client"})
    public void shouldRejectGetTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/push.config.create.unsupported/client"})
    public void shouldRejectPushConfigCreate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/extended.agent.card.unsupported/client"})
    public void shouldRejectExtendedAgentCard() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.item.text/client",
        "${http}/get.item.text/server"})
    public void shouldGetTextItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.item.file/client",
        "${http}/get.item.file/server"})
    public void shouldGetFileItem() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${app}/get.item.empty/client",
        "${http}/get.item.empty/server"})
    public void shouldGetEmptyItem() throws Exception
    {
        k3po.finish();
    }
}
