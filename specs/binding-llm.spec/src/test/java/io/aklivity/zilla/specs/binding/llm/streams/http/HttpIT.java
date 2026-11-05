/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.llm.streams.http;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class HttpIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/llm/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${http}/openai.proxy.circuit.failures/client",
        "${http}/openai.proxy.circuit.failures/server"})
    public void shouldOpenaiProxyCircuitFailures() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/openai.proxy.circuit.retry.after/client",
        "${http}/openai.proxy.circuit.retry.after/server"})
    public void shouldOpenaiProxyCircuitRetryAfter() throws Exception
    {
        k3po.finish();
    }
}
