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
package io.aklivity.zilla.specs.binding.risingwave.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class PgsqlIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/risingwave/streams/pgsql");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/create.ztable.with.primary.key/client",
        "${app}/create.ztable.with.primary.key/server"
    })
    public void shouldCreateZtableWithPrimaryKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.zstream/client",
        "${app}/create.zstream/server"
    })
    public void shouldCreateZstream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.zview/client",
        "${app}/create.zview/server"
    })
    public void shouldCreateZview() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/query.with.multiple.statements/client",
        "${app}/query.with.multiple.statements/server"
    })
    public void shouldHandleQueryWithMultiStatements() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.function/client",
        "${app}/create.function/server"
    })
    public void shouldCreateFunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.function.python/client",
        "${app}/create.function.python/server"
    })
    public void shouldCreateFunctionPython() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.function.embedded.python/client",
        "${app}/create.function.embedded.python/server" })
    public void shouldCreateFunctionEmbeddedPython() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.function.return.struct/client",
        "${app}/create.function.return.struct/server"
    })
    public void shouldCreateFunctionReturnStruct() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.function.return.table/client",
        "${app}/create.function.return.table/server"
    })
    public void shouldCreateFunctionReturnTable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.function.embedded/client",
        "${app}/create.function.embedded/server" })
    public void shouldCreateFunctionEmbedded() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.ztable.with.generated.as/client",
        "${app}/create.ztable.with.generated.as/server" })
    public void shouldCreateTableWithGeneratedAs() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/show.tables.with.newline/client",
        "${app}/show.tables.with.newline/server" })
    public void shouldShowTablesWithNewline() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/query.with.multiple.statements.errored/client",
        "${app}/query.with.multiple.statements.errored/server"
    })
    public void shouldHandleQueryWithMultiStatementsThatErrored() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/set.variable/client",
        "${app}/set.variable/server" })
    public void shouldSetVariable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/drop.ztable/client",
        "${app}/drop.ztable/server" })
    public void shouldDropZtable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/drop.zstream/client",
        "${app}/drop.zstream/server" })
    public void shouldDropZstream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/drop.zview/client",
        "${app}/drop.zview/server" })
    public void shouldDropZview() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/alter.ztable.add.column/client",
        "${app}/alter.ztable.add.column/server" })
    public void shouldAlterTableAddColumn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/alter.ztable.modify.column.rejected/client",
        "${app}/alter.ztable.modify.column.rejected/server" })
    public void shouldNotAlterZtableModifyColumn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/show.zviews/client",
        "${app}/show.zviews/server" })
    public void shouldShowZviews() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.zfunction/client",
        "${app}/create.zfunction/server" })
    public void shouldCreateZfunction() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/show.zfunctions/client",
        "${app}/show.zfunctions/server" })
    public void shouldShowZfunctions() throws Exception
    {
        k3po.finish();
    }
}
