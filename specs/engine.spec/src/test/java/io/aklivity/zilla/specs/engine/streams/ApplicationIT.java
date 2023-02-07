/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.specs.engine.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ApplicationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/engine/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/reconfigure.modify.via.file/client",
        "${app}/reconfigure.modify.via.file/server" })
    public void shouldReconfigureWhenModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.modify.complex.chain.via.file/client",
        "${app}/reconfigure.modify.complex.chain.via.file/server" })
    public void shouldReconfigureWhenModifiedUsingComplexSymlinkChain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.create.via.file/client",
        "${app}/reconfigure.create.via.file/server" })
    public void shouldReconfigureWhenCreated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.delete.via.file/client",
        "${app}/reconfigure.delete.via.file/server" })
    public void shouldReconfigureWhenDeleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.not.modify.parse.failed.via.file/server",
        "${app}/reconfigure.not.modify.parse.failed.via.file/client"
    })
    public void shouldNotReconfigureWhenModifiedButParseFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.modify.via.http/client",
        "${app}/reconfigure.modify.via.http/server" })
    public void shouldReconfigureWhenModifiedHTTP() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.create.via.http/client",
        "${app}/reconfigure.create.via.http/server" })
    public void shouldReconfigureWhenCreatedHTTP() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.delete.via.http/client",
        "${app}/reconfigure.delete.via.http/server" })
    public void shouldReconfigureWhenDeletedHTTP() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.modify.no.etag.via.http/server",
        "${app}/reconfigure.modify.no.etag.via.http/client"
    })
    public void shouldReconfigureWhenModifiedHTTPEtagNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/reconfigure.server.error.via.http/server",
        "${app}/reconfigure.server.error.via.http/client"
    })
    public void shouldNotReconfigureWhen500Returned() throws Exception
    {
        k3po.finish();
    }
}
