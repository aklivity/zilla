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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7230.server;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_HEADER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class AuthorizationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/authorization")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230/authorization");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configure(HTTP_SERVER_HEADER, "Zilla")
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/reject.credentials.cookie/client",
    })
    public void shouldRejectCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/reject.credentials.header/client",
    })
    public void shouldRejectCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/reject.credentials.query/client",
    })
    public void shouldRejectCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/authorize.credentials.cookie/client",
        "${app}/authorize.credentials.cookie/server",
    })
    public void shouldAuthorizeCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/authorize.credentials.header/client",
        "${app}/authorize.credentials.header/server",
    })
    public void shouldAuthorizeCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/authorize.credentials.query/client",
        "${app}/authorize.credentials.query/server",
    })
    public void shouldAuthorizeCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/expire.credentials.cookie/client",
        "${app}/expire.credentials.cookie/server",
    })
    public void shouldExpireCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/expire.credentials.header/client",
        "${app}/expire.credentials.header/server",
    })
    public void shouldExpireCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/expire.credentials.query/client",
        "${app}/expire.credentials.query/server",
    })
    public void shouldExpireCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/challenge.credentials.cookie/client",
        "${app}/challenge.credentials.cookie/server",
    })
    public void shouldChallengeCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/challenge.credentials.header/client",
        "${app}/challenge.credentials.header/server",
    })
    public void shouldChallengeCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/challenge.credentials.query/client",
        "${app}/challenge.credentials.query/server",
    })
    public void shouldChallengeCredentialsQuery() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/reauthorize.credentials.cookie/client",
        "${app}/reauthorize.credentials.cookie/server",
    })
    public void shouldReauthorizeCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/reauthorize.credentials.header/client",
        "${app}/reauthorize.credentials.header/server",
    })
    public void shouldReauthorizeCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.authorization.credentials.yaml")
    @Specification({
        "${net}/reauthorize.credentials.query/client",
        "${app}/reauthorize.credentials.query/server",
    })
    public void shouldReauthorizeCredentialsQuery() throws Exception
    {
        k3po.finish();
    }
}
