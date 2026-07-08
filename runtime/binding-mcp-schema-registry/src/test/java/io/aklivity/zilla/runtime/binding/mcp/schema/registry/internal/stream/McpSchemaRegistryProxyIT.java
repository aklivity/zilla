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

/*
 * Not yet runnable: no BindingFactorySpi is registered for type "mcp_schema_registry"
 * or "openapi" catalog wiring for this fixture until the runtime implementation lands.
 * Expected failure mode until then: engine config load rejects the fixture with an
 * unrecognized binding type / unresolved catalog subject error, per the test-first
 * discipline in AGENTS.md ("confirm the tests fail for the right reason").
 */
public class McpSchemaRegistryProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/schema/registry/streams/mcp")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/schema/registry/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/schema/registry/config")
        .external("http0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

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


    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/list.contexts/client",
        "${http}/list.contexts/server"})
    public void shouldCallToolListContexts() throws Exception
    {
        k3po.finish();
    }
}
