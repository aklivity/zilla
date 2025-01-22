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
        "${pgsql}/create.ztable.with.primary.key/client",
        "${effective}/create.ztable.with.primary.key/server" })
    public void shouldCreateZtableWithPrimaryKey() throws Exception
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
        "${pgsql}/query.with.multiple.statements.errored/client",
        "${effective}/query.with.multiple.statements.errored/server"
    })
    public void shouldHandleQueryWithMultiStatementsThatErrored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.zview/client",
        "${effective}/create.zview/server"
    })
    public void shouldCreateZview() throws Exception
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
        "${pgsql}/create.function.python/client",
        "${effective}/create.function.python/server" })
    public void shouldCreateFunctionPython() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.function.yaml")
    @Specification({
        "${pgsql}/create.function.embedded.python/client",
        "${effective}/create.function.embedded.python/server" })
    public void shouldCreateFunctionEmbeddedPython() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.function.yaml")
    @Specification({
        "${pgsql}/create.function.return.struct/client",
        "${effective}/create.function.return.struct/server" })
    public void shouldCreateFunctionReturnStruct() throws Exception
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
        "${pgsql}/create.ztable.with.generated.as/client",
        "${effective}/create.ztable.with.generated.as/server" })
    public void shouldCreateTableWithGeneratedAs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.risingwave.yaml")
    @Specification({
        "${pgsql}/set.variable/client",
        "${effective}/set.variable/server" })
    public void shouldSetVariable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/drop.ztable/client",
        "${effective}/drop.ztable/server" })
    public void shouldDropZtable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/drop.zview/client",
        "${effective}/drop.zview/server" })
    public void shouldDropZview() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/alter.ztable.add.column/client",
        "${effective}/alter.ztable.add.column/server" })
    public void shouldAlterZtableAddColumn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/alter.ztable.modify.column.rejected/client",
        "${effective}/client.stream.established/server" })
    public void shouldNotAlterZtableModifyColumn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.risingwave.yaml")
    @Specification({
        "${pgsql}/show.zviews/client",
        "${effective}/show.zviews/server" })
    public void shouldShowZviews() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.risingwave.yaml")
    @Specification({
        "${pgsql}/show.ztables/client",
        "${effective}/show.ztables/server" })
    public void shouldShowZtables() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${pgsql}/create.zfunction/client",
        "${effective}/create.zfunction/server" })
    public void shouldCreateZfunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.risingwave.yaml")
    @Specification({
        "${pgsql}/show.zfunctions/client",
        "${effective}/show.zfunctions/server" })
    public void shouldShowZfunctions() throws Exception
    {
        k3po.finish();
    }
}
