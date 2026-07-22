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
package io.aklivity.zilla.runtime.binding.mcp.schema.registry.internal.stream;

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

public class McpSchemaRegistryClientIT
{
    private static final String HTTP_CLIENT_EXIT_NAME = "zilla.binding.mcp.openapi.http.client.exit";
    private static final String SESSION_ID_NAME = "zilla.binding.mcp.http.session.id";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/schema/registry/streams/mcp")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/schema/registry/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(HTTP_CLIENT_EXIT_NAME, "test:http0")
        .configure(SESSION_ID_NAME, "%s::sessionId".formatted(McpSchemaRegistryClientIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/schema/registry/config")
        .external("http0")
        .clean();

    public static String sessionId()
    {
        return "session-1";
    }

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/tools.list/client"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/list.subjects/client",
        "${http}/list.subjects/server"})
    public void shouldCallToolListSubjects() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/describe.subject/client",
        "${http}/describe.subject/server"})
    public void shouldCallToolDescribeSubject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/get.schema/client",
        "${http}/get.schema/server"})
    public void shouldCallToolGetSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/register.schema/client",
        "${http}/register.schema/server"})
    public void shouldCallToolRegisterSchema() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/delete.subject/client",
        "${http}/delete.subject/server"})
    public void shouldCallToolDeleteSubject() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/delete.schema.version/client",
        "${http}/delete.schema.version/server"})
    public void shouldCallToolDeleteSchemaVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/check.compatibility/client",
        "${http}/check.compatibility/server"})
    public void shouldCallToolCheckCompatibility() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/get.compatibility/client",
        "${http}/get.compatibility/server"})
    public void shouldCallToolGetCompatibility() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/set.compatibility/client",
        "${http}/set.compatibility/server"})
    public void shouldCallToolSetCompatibility() throws Exception
    {
        k3po.finish();
    }
}
