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
package io.aklivity.zilla.specs.binding.http.streams.application.rfc7540;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class AccessControlIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/access.control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/allow.origin/client",
        "${app}/allow.origin/server",
    })
    public void shouldAllowOrigin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.methods/client",
        "${app}/allow.methods/server",
    })
    public void shouldAllowMethods() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.headers/client",
        "${app}/allow.headers/server",
    })
    public void shouldAllowHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.credentials.cookie/client",
        "${app}/allow.credentials.cookie/server",
    })
    public void shouldAllowCredentialsCookie() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/expose.headers/client",
        "${app}/expose.headers/server",
    })
    public void shouldExposeHeaders() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.origin.same.origin/client",
        "${app}/allow.origin.same.origin/server",
    })
    public void shouldAllowOriginWhenSameOrigin() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.origin.same.origin.implicit.http.port/client",
        "${app}/allow.origin.same.origin.implicit.http.port/server",
    })
    public void shouldAllowOriginWhenSameOriginWithImplicitHttpPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.origin.same.origin.implicit.https.port/client",
        "${app}/allow.origin.same.origin.implicit.https.port/server",
    })
    public void shouldAllowOriginWhenSameOriginWithImplicitHttpsPort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.origin.omitted/client",
        "${app}/allow.origin.omitted/server",
    })
    public void shouldAllowOriginWhenOmitted() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/allow.origin.present/client",
        "${app}/allow.origin.present/server",
    })
    public void shouldAllowOriginWhenPresent() throws Exception
    {
        k3po.finish();
    }

}
