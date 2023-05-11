/*
 * Copyright 2021-2023 Aklivity Inc.
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

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/engine/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/reconfigure.modify.via.file/client",
        "${net}/reconfigure.modify.via.file/server" })
    public void shouldReconfigureWhenModified() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.modify.complex.chain.via.file/client",
        "${net}/reconfigure.modify.complex.chain.via.file/server" })
    public void shouldReconfigureWhenModifiedUsingComplexSymlinkChain() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.create.via.file/client",
        "${net}/reconfigure.create.via.file/server" })
    public void shouldReconfigureWhenCreated() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.delete.via.file/client",
        "${net}/reconfigure.delete.via.file/server" })
    public void shouldReconfigureWhenDeleted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.not.modify.parse.failed.via.file/server",
        "${net}/reconfigure.not.modify.parse.failed.via.file/client"
    })
    public void shouldNotReconfigureWhenModifiedButParseFailed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.modify.via.http/client",
        "${net}/reconfigure.modify.via.http/server" })
    public void shouldReconfigureWhenModifiedHTTP() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.create.via.http/client",
        "${net}/reconfigure.create.via.http/server" })
    public void shouldReconfigureWhenCreatedHTTP() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.delete.via.http/client",
        "${net}/reconfigure.delete.via.http/server" })
    public void shouldReconfigureWhenDeletedHTTP() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.modify.no.etag.via.http/server",
        "${net}/reconfigure.modify.no.etag.via.http/client"
    })
    public void shouldReconfigureWhenModifiedHTTPEtagNotSupported() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reconfigure.server.error.via.http/server",
        "${net}/reconfigure.server.error.via.http/client"
    })
    public void shouldNotReconfigureWhen500Returned() throws Exception
    {
        k3po.finish();
    }
}
