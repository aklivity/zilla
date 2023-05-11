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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7540;

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
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/authorization");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/reject.credentials.missing/client",
        "${net}/reject.credentials.missing/server",
    })
    public void shouldRejectCredentialsMissing() throws Exception
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
        "${net}/expire.credentials.cookie/client",
        "${net}/expire.credentials.cookie/server",
    })
    public void shouldExpireCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/expire.credentials.header/client",
        "${net}/expire.credentials.header/server",
    })
    public void shouldExpireCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/expire.credentials.query/client",
        "${net}/expire.credentials.query/server",
    })
    public void shouldExpireCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/challenge.credentials.cookie/client",
        "${net}/challenge.credentials.cookie/server",
    })
    public void shouldChallengeCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/challenge.credentials.header/client",
        "${net}/challenge.credentials.header/server",
    })
    public void shouldChallengeCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/challenge.credentials.query/client",
        "${net}/challenge.credentials.query/server",
    })
    public void shouldChallengeCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reauthorize.credentials.cookie/client",
        "${net}/reauthorize.credentials.cookie/server",
    })
    public void shouldReauthorizeCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reauthorize.credentials.header/client",
        "${net}/reauthorize.credentials.header/server",
    })
    public void shouldReauthorizeCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/reauthorize.credentials.query/client",
        "${net}/reauthorize.credentials.query/server",
    })
    public void shouldReauthorizeCredentialsQuery() throws Exception
    {
        k3po.finish();
    }
}
