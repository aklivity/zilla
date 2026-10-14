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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

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

public class PresidioClassifierIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/classifier/presidio/streams/network")
        .addScriptRoot("analyzer", "io/aklivity/zilla/specs/classifier/presidio/streams/analyzer")
        .addScriptRoot("app", "io/aklivity/zilla/specs/classifier/presidio/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/classifier/presidio/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("reject.yaml")
    @Specification({
        "${net}/client.sent.text.accepted/client",
        "${analyzer}/no.findings/server",
        "${app}/client.sent.text.accepted/server"
    })
    public void shouldForwardTextWithoutFindings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.no.timeout.yaml")
    @Specification({
        "${net}/client.sent.text.accepted/client",
        "${analyzer}/no.findings/server",
        "${app}/client.sent.text.accepted/server"
    })
    public void shouldForwardTextWithoutFindingsWhenTimeoutDisabled() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.prefixed.yaml")
    @Specification({
        "${net}/client.sent.text.accepted/client",
        "${analyzer}/no.findings.prefixed/server",
        "${app}/client.sent.text.accepted/server"
    })
    public void shouldForwardTextWithoutFindingsAtPathPrefix() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.yaml")
    @Specification({
        "${net}/client.sent.text.rejected/client",
        "${analyzer}/findings/server",
        "${app}/client.sent.text.rejected/server"
    })
    public void shouldAbortTextWithFindings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.minimal.yaml")
    @Specification({
        "${net}/client.sent.text.rejected/client",
        "${analyzer}/findings.minimal/server",
        "${app}/client.sent.text.rejected/server"
    })
    public void shouldAbortTextWithFindingsUsingDefaults() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.yaml")
    @Specification({
        "${net}/client.sent.text.rejected/client",
        "${analyzer}/error.response/server",
        "${app}/client.sent.text.rejected/server"
    })
    public void shouldAbortTextOnErrorResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.yaml")
    @Specification({
        "${net}/client.sent.text.rejected/client",
        "${analyzer}/malformed.response/server",
        "${app}/client.sent.text.rejected/server"
    })
    public void shouldAbortTextOnMalformedResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.yaml")
    @Specification({
        "${net}/client.sent.text.rejected/client",
        "${analyzer}/timeout/server",
        "${app}/client.sent.text.timeout.rejected/server"
    })
    public void shouldAbortTextOnTimeout() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("reject.yaml")
    @Specification({
        "${net}/client.sent.empty.text.accepted/client",
        "${app}/client.sent.empty.text.accepted/server"
    })
    public void shouldForwardEmptyTextWithoutRequest() throws Exception
    {
        k3po.finish();
    }
}
