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
package io.aklivity.zilla.specs.binding.mcp.schema.registry.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class McpServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/schema/registry/streams/mcp");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${mcp}/list.subjects/client",
        "${mcp}/list.subjects/server"})
    public void shouldCallToolListSubjects() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/describe.subject/client",
        "${mcp}/describe.subject/server"})
    public void shouldCallToolDescribeSubject() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/get.schema/client",
        "${mcp}/get.schema/server"})
    public void shouldCallToolGetSchema() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/register.schema/client",
        "${mcp}/register.schema/server"})
    public void shouldCallToolRegisterSchema() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/delete.subject/client",
        "${mcp}/delete.subject/server"})
    public void shouldCallToolDeleteSubject() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/delete.schema.version/client",
        "${mcp}/delete.schema.version/server"})
    public void shouldCallToolDeleteSchemaVersion() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/check.compatibility/client",
        "${mcp}/check.compatibility/server"})
    public void shouldCallToolCheckCompatibility() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/get.compatibility/client",
        "${mcp}/get.compatibility/server"})
    public void shouldCallToolGetCompatibility() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/set.compatibility/client",
        "${mcp}/set.compatibility/server"})
    public void shouldCallToolSetCompatibility() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${mcp}/list.contexts/client",
        "${mcp}/list.contexts/server"})
    public void shouldCallToolListContexts() throws Exception
    {
        k3po.finish();
    }
}
