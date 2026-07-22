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

public class HttpClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/schema/registry/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${http}/list.subjects/client",
        "${http}/list.subjects/server"})
    public void shouldProxyListSubjectsToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/describe.subject/client",
        "${http}/describe.subject/server"})
    public void shouldProxyDescribeSubjectToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/get.schema/client",
        "${http}/get.schema/server"})
    public void shouldProxyGetSchemaToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/register.schema/client",
        "${http}/register.schema/server"})
    public void shouldProxyRegisterSchemaToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/delete.subject/client",
        "${http}/delete.subject/server"})
    public void shouldProxyDeleteSubjectToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/delete.schema.version/client",
        "${http}/delete.schema.version/server"})
    public void shouldProxyDeleteSchemaVersionToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/check.compatibility/client",
        "${http}/check.compatibility/server"})
    public void shouldProxyCheckCompatibilityToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/get.compatibility/client",
        "${http}/get.compatibility/server"})
    public void shouldProxyGetCompatibilityToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/set.compatibility/client",
        "${http}/set.compatibility/server"})
    public void shouldProxySetCompatibilityToHttp() throws Exception
    {
        k3po.finish();
    }


    @Test
    @Specification({
        "${http}/list.contexts/client",
        "${http}/list.contexts/server"})
    public void shouldProxyListContextsToHttp() throws Exception
    {
        k3po.finish();
    }
}
