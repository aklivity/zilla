/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7230.client;

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

public class AuthorizationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/authorization")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230/authorization");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.authorization.credentials.yaml")
    @Specification({
        "${app}/inject.credentials.header/client",
        "${net}/inject.credentials.header/server" })
    public void shouldInjectCredentialsHeader() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.authorization.credentials.basic.yaml")
    @Specification({
        "${app}/inject.credentials.header.basic/client",
        "${net}/inject.credentials.header.basic/server" })
    public void shouldInjectCredentialsHeaderWithBasicAuth() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.authorization.credentials.cookies.yaml")
    @Specification({
        "${app}/inject.credentials.cookie/client",
        "${net}/inject.credentials.cookie/server" })
    public void shouldInjectCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.authorization.credentials.query.yaml")
    @Specification({
        "${app}/inject.credentials.query/client",
        "${net}/inject.credentials.query/server" })
    public void shouldInjectCredentialsQuery() throws Exception
    {
        k3po.finish();
    }
}
