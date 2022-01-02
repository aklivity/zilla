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
package io.aklivity.zilla.specs.cog.http.streams.network.rfc7230;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class ArchitectureIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/cog/http/streams/network/rfc7230/architecture");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/request.and.response/client",
        "${net}/request.and.response/server"})
    public void requestAndResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.uri.with.percent.chars/client",
        "${net}/request.uri.with.percent.chars/server"})
    public void shouldAcceptRequestWithPercentChars() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.version.1.2+/client",
        "${net}/request.version.1.2+/server"})
    public void shouldRespondVersionHttp11WhenRequestVersionHttp12plus() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.header.host.missing/client",
        "${net}/request.header.host.missing/server"})
    public void shouldRejectRequestWhenHostHeaderMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.uri.with.user.info/client",
        "${net}/request.uri.with.user.info/server"})
    public void shouldRejectRequestWithUserInfo() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.version.invalid/client",
        "${net}/request.version.invalid/server"})
    public void shouldRejectRequestWhenVersionInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.version.missing/client",
        "${net}/request.version.missing/server"})
    public void shouldRejectRequestWhenVersionMissing() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/request.version.not.1.x/client",
        "${net}/request.version.not.1.x/server"})
    public void shouldRejectRequestWhenVersionNotHttp1x() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/response.version.missing/client",
        "${net}/response.version.missing/server"})
    public void responseVersionMissing() throws Exception
    {
        k3po.finish();
    }
}
