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

public class ValidationIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/validation");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/invalid.request/client",
        "${app}/invalid.request/server" })
    public void shouldRejectInvalidRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/valid.request/client",
        "${app}/valid.request/server" })
    public void shouldProcessValidRequests() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/invalid.response.header/client",
        "${app}/invalid.response.header/server" })
    public void shouldSendErrorForInvalidHeaderResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/invalid.response.content/client",
        "${app}/invalid.response.content/server" })
    public void shouldAbortForInvalidResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/valid.response/client",
        "${app}/valid.response/server" })
    public void shouldProcessValidResponse() throws Exception
    {
        k3po.finish();
    }
}
