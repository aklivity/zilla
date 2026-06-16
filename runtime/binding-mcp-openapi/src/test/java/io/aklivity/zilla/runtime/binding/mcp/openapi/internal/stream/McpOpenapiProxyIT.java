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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.stream;

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

public class McpOpenapiProxyIT
{
    private static final String HTTP_CLIENT_EXIT_NAME = "zilla.binding.mcp.openapi.http.client.exit";
    private static final String SESSION_ID_NAME = "zilla.binding.mcp.http.session.id";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/openapi/streams/mcp")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/openapi/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_BUFFER_SLOT_CAPACITY, 8192)
        .configure(HTTP_CLIENT_EXIT_NAME, "test:http0")
        .configure(SESSION_ID_NAME, "%s::sessionId".formatted(McpOpenapiProxyIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/openapi/config")
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
        "${mcp}/create.pr/client",
        "${http}/create.pr/server"})
    public void shouldCallToolCreatePr() throws Exception
    {
        k3po.finish();
    }
}
