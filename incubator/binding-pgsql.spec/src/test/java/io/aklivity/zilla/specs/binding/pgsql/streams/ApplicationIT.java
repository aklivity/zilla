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
package io.aklivity.zilla.specs.binding.pgsql.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/pgsql/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/create.table.with.primary.key/client",
        "${app}/create.table.with.primary.key/server"
    })
    public void shouldCreateTableWithPrimaryKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.view.with.notice/client",
        "${app}/create.view.with.notice/server"
    })
    public void shouldCreateViewWithNotice() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/select.table/client",
        "${app}/select.table/server" })
    public void shouldSelectTable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/select.table.with.error/client",
        "${app}/select.table.with.error/server" })
    public void shouldSelectTableWithError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.write.abort/client",
        "${app}/client.sent.write.abort/server" })
    public void shouldHandleClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/client.sent.read.abort/client",
        "${app}/client.sent.read.abort/server" })
    public void shouldHandleClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/create.table.fragmented/client",
        "${app}/create.table.fragmented/server" })
    public void shouldHandleFragmentedCreateTable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/gss.encrypt.request/client",
        "${app}/gss.encrypt.request/server" })
    public void shouldHandleGssEncryptRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/ssl.request/client",
        "${app}/ssl.request/server" })
    public void shouldHandleSslRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/termination.request/client",
        "${app}/termination.request/server" })
    public void shouldHandleTerminationRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/cancel.request/client",
        "${app}/cancel.request/server" })
    public void shouldHandleCancelRequest() throws Exception
    {
        k3po.finish();
    }
}
