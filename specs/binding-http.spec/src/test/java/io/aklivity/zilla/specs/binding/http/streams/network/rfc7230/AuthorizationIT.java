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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7230;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class AuthorizationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/authorization");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/authorize.credentials.cookie/client",
        "${net}/authorize.credentials.cookie/server",
    })
    public void shouldAuthorizeCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/authorize.credentials.header/client",
        "${net}/authorize.credentials.header/server",
    })
    public void shouldAuthorizeCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/authorize.credentials.query/client",
        "${net}/authorize.credentials.query/server",
    })
    public void shouldAuthorizeCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.credentials.cookie/client",
        "${net}/reject.credentials.cookie/server",
    })
    public void shouldRejectCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.credentials.header/client",
        "${net}/reject.credentials.header/server",
    })
    public void shouldRejectCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reject.credentials.query/client",
        "${net}/reject.credentials.query/server",
    })
    public void shouldRejectCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/challenge.response.before.expiration/client",
        "${net}/challenge.response.before.expiration/server",
    })
    public void shouldChallengeResponseBeforeExpiration() throws Exception
    {
        k3po.finish();
    }
}
