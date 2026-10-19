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
package io.aklivity.zilla.specs.classifier.presidio.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class AnalyzerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("analyzer", "io/aklivity/zilla/specs/classifier/presidio/streams/analyzer");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${analyzer}/no.findings/client",
        "${analyzer}/no.findings/server"
    })
    public void shouldAnalyzeTextWithoutFindings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${analyzer}/no.findings.prefixed/client",
        "${analyzer}/no.findings.prefixed/server"
    })
    public void shouldAnalyzeTextWithoutFindingsAtPathPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${analyzer}/findings/client",
        "${analyzer}/findings/server"
    })
    public void shouldAnalyzeTextWithFindings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${analyzer}/findings.minimal/client",
        "${analyzer}/findings.minimal/server"
    })
    public void shouldAnalyzeTextWithDefaults() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${analyzer}/error.response/client",
        "${analyzer}/error.response/server"
    })
    public void shouldFailOnErrorResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${analyzer}/malformed.response/client",
        "${analyzer}/malformed.response/server"
    })
    public void shouldFailOnMalformedResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${analyzer}/timeout/client",
        "${analyzer}/timeout/server"
    })
    public void shouldFailOnTimeout() throws Exception
    {
        k3po.finish();
    }
}
