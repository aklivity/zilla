/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.http.internal.McpHttpConfigurationTest.MCP_HTTP_SESSION_ID_NAME;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
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

public class McpHttpProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/http/streams/mcp")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/http/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(MCP_HTTP_SESSION_ID_NAME, "%s::sessionId".formatted(McpHttpProxyIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/http/config")
        .external("http0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    public static String sessionId()
    {
        return "session-1";
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/initialize/client"})
    public void shouldInitialize() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/lifecycle.data/client"})
    public void shouldConsumeLifecycleData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/lifecycle.client.abort/client"})
    public void shouldAbortLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/lifecycle.client.reset/client"})
    public void shouldResetLifecycle() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.10k/client",
        "${http}/create.pr.10k/server"})
    public void shouldCallToolCreatePr10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.100k/client",
        "${http}/create.pr.100k/server"})
    public void shouldCallToolCreatePr100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.fragmented/client",
        "${http}/create.pr.fragmented/server"})
    public void shouldCallToolCreatePrFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.rich/client",
        "${http}/create.pr.rich/server"})
    public void shouldCallToolCreatePrWithStructuredArguments() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/read.order.10k/client",
        "${http}/read.order.10k/server"})
    public void shouldReadResourceOrder10k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/read.order.100k/client",
        "${http}/read.order.100k/server"})
    public void shouldReadResourceOrder100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr/client",
        "${http}/create.pr/server"})
    public void shouldCallToolCreatePr() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.remap.yaml")
    @Specification({
        "${mcp}/create.pr.remap/client",
        "${http}/create.pr/server"})
    public void shouldCallToolWithBodyTemplate() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.yaml")
    @Specification({
        "${mcp}/tools.list/client"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.yaml")
    @Specification({
        "${mcp}/tools.list.unknown.session/client"})
    public void shouldRejectUnknownSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.prompts.yaml")
    @Specification({
        "${mcp}/prompts.get/client"})
    public void shouldGetPrompt() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.prompts.yaml")
    @Specification({
        "${mcp}/prompts.list.configured/client"})
    public void shouldListConfiguredPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.yaml")
    @Specification({
        "${mcp}/resources.list/client"})
    public void shouldListResources() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.discovery.yaml")
    @Specification({
        "${mcp}/prompts.list/client"})
    public void shouldListPrompts() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/read.order/client",
        "${http}/read.order/server"})
    public void shouldReadResourceOrder() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/search.code/client",
        "${http}/search.code/server"})
    public void shouldCallToolSearchCode() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/search.code.invalid/client"})
    public void shouldRejectToolSearchCodeWhenArgumentsInvalid() throws Exception
    {
        k3po.finish();
    }

    // a ~100KB tools/call response, streamed across many suspend/resume cycles; the leading total_count
    // field is captured well before the large trailing items[].path value finishes streaming, proving
    // tool.summary interpolation survives more than one window (see McpHttpResults)
    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/search.code.100k/client",
        "${http}/search.code.100k/server"})
    public void shouldCallToolSearchCode100k() throws Exception
    {
        k3po.finish();
    }

    // a ~100KB structuredContent.message value, also interpolated into tool.summary's content.text: proves
    // the summary trailer (injected as more events once structuredContent's own value closes, see
    // McpHttpToolResult) survives many suspend/resume cycles across the encode slot exactly like
    // structuredContent's own streamed value does, not just when the destination has room left over
    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/get.report.large/client",
        "${http}/get.report.large/server"})
    public void shouldCallToolGetReportWithLargeSummary() throws Exception
    {
        k3po.finish();
    }

    // ping has no tool.summary configured: content[0].text falls back to the empty string
    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/ping/client",
        "${http}/ping/server"})
    public void shouldCallToolPing() throws Exception
    {
        k3po.finish();
    }

    // list_tags' output schema is array-rooted: structuredContent is the array itself, not an object
    // wrapping it
    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/list.tags/client",
        "${http}/list.tags/server"})
    public void shouldCallToolListTags() throws Exception
    {
        k3po.finish();
    }

    // count_items' output schema is a bare scalar: structuredContent is the scalar itself, not an
    // object/array wrapping it
    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/count.items/client",
        "${http}/count.items/server"})
    public void shouldCallToolCountItems() throws Exception
    {
        k3po.finish();
    }

    // a 12000-byte top-level argument value referenced by the route's :path template: proves
    // McpHttpArguments captures a value spanning multiple decode windows correctly (see the
    // mediating-transform rule / multi-window accumulation fix), not just short path arguments that
    // always arrive in one window
    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/echo.id.large/client",
        "${http}/echo.id.large/server"})
    public void shouldCallToolEchoIdWithLargeArgument() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.error/client",
        "${http}/create.pr.error/server"})
    public void shouldRejectToolCreatePr() throws Exception
    {
        k3po.finish();
    }

    // a ~100KB non-2xx upstream error body, relayed back verbatim as escaped text; proves the error relay
    // genuinely streams (windowed, no MAX_ERROR_BODY-style cap) rather than needing to fit one buffer
    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.error.100k/client",
        "${http}/create.pr.error.100k/server"})
    public void shouldRejectToolCreatePr100k() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.aborted/client",
        "${http}/create.pr.aborted/server"})
    public void shouldAbortToolCreatePr() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.error.aborted/client",
        "${http}/create.pr.error.aborted/server"})
    public void shouldAbortToolCreatePrDuringErrorRelay() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.client.reset/client",
        "${http}/create.pr.client.reset/server"})
    public void shouldAbortUpstreamWhenReplyReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guarded.yaml")
    @Specification({
        "${mcp}/create.pr/client",
        "${http}/create.pr/server"})
    public void shouldCallToolWhenAuthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.guarded.yaml")
    @Specification({
        "${mcp}/search.code.forbidden/client"})
    public void shouldRejectToolWhenUnauthorized() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.credentials.yaml")
    @Specification({
        "${mcp}/create.pr/client",
        "${http}/create.pr.credentials/server"})
    public void shouldInjectCredentials() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.invalid/client"})
    public void shouldRejectToolWhenArgumentsInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.malformed.request/client"})
    public void shouldRejectToolWhenRequestMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.unresolved.yaml")
    @Specification({
        "${mcp}/create.pr.unresolved/client"})
    public void shouldRejectToolWhenExpressionUnresolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.unresolved.event.yaml")
    @Specification({
        "${mcp}/create.pr.unresolved/client"})
    public void shouldEmitSchemaAccessorUnresolvedEvent() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.pr.malformed/client",
        "${http}/create.pr.malformed/server"})
    public void shouldRejectToolWhenResponseInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/read.order.malformed/client",
        "${http}/read.order.malformed/server"})
    public void shouldRejectResourceWhenResponseInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/read.order.error/client",
        "${http}/read.order.error/server"})
    public void shouldAbortResourceReadWhenUpstreamStatusNotOk() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/get.profile/client",
        "${http}/get.profile/server"})
    public void shouldCallToolGetProfileWithNoSummary() throws Exception
    {
        k3po.finish();
    }
}
