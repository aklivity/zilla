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

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/pgsql/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/create.table.with.primary.key/client",
        "${net}/create.table.with.primary.key/server"
    })
    public void shouldCreateTableWithPrimaryKey() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/select.table/client",
        "${net}/select.table/server" })
    public void shouldSelectTable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/select.table.with.error/client",
        "${net}/select.table.with.error/server" })
    public void shouldSelectTableWithError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/ssl.request/client",
        "${net}/ssl.request/server" })
    public void shouldHandleSslRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.write.abort/client",
        "${net}/client.sent.write.abort/server" })
    public void shouldHandleClientSentWriteAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.read.abort/client",
        "${net}/client.sent.read.abort/server" })
    public void shouldHandleClientSentReadAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/create.table.fragmented/client",
        "${net}/create.table.fragmented/server" })
    public void shouldHandleFragmentedCreateTable() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/termination.request/client",
        "${net}/termination.request/server" })
    public void shouldHandleTerminationRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/cancel.request/client",
        "${net}/cancel.request/server" })
    public void shouldHandleCancelRequest() throws Exception
    {
        k3po.finish();
    }
}
