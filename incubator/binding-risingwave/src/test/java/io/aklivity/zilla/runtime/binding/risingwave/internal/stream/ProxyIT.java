/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.risingwave.internal.stream;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
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

public class ProxyIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("pgsql", "io/aklivity/zilla/specs/binding/risingwave/streams/pgsql")
        .addScriptRoot("effective", "io/aklivity/zilla/specs/binding/risingwave/streams/effective");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .configurationRoot("io/aklivity/zilla/specs/binding/risingwave/config")
        .external("app1")
        .external("app2")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.table.with.primary.key/client",
        "${effective}/create.table.with.primary.key/server" })
    public void shouldCreateTableWithPrimaryKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.table/client",
        "${effective}/create.table/server" })
    public void shouldCreateTable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.tables/client",
        "${effective}/create.tables/server" })
    public void shouldCreateTables() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.risingwave.yaml")
    @Specification({
        "${pgsql}/show.tables.with.newline/client",
        "${effective}/show.tables.with.newline/server" })
    public void shouldShowTablesWithNewline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/query.with.multiple.statements/client",
        "${effective}/query.with.multiple.statements/server"
    })
    public void shouldHandleQueryWithMultiStatements() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.materialized.view/client",
        "${effective}/create.materialized.view/server"
    })
    public void shouldCreateMaterializedView() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.function.yaml")
    @Specification({
        "${pgsql}/create.function/client",
        "${effective}/create.function/server" })
    public void shouldCreateFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.function.yaml")
    @Specification({
        "${pgsql}/create.function.return.table/client",
        "${effective}/create.function.return.table/server" })
    public void shouldCreateFunctionReturnTable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.function.yaml")
    @Specification({
        "${pgsql}/create.function.embedded/client",
        "${effective}/create.function.embedded/server" })
    public void shouldCreateFunctionEmbedded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.table.with.includes/client",
        "${effective}/create.table.with.includes/server" })
    public void shouldCreateTableWithIncludes() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.table.with.primary.key.and.includes/client",
        "${effective}/create.table.with.primary.key.and.includes/server" })
    public void shouldCreateTableWithPrimaryKeyAndIncludes() throws Exception
    {
        k3po.finish();
    }
}
