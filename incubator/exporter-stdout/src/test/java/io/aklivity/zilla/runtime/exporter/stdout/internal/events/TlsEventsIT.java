/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.exporter.stdout.internal.events;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DRAIN_ON_CLOSE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.regex.Pattern;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class TlsEventsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/tls/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/tls/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/tls/config")
        .external("app0")
        .configure(ENGINE_DRAIN_ON_CLOSE, false)
        .clean();

    private final StdoutOutputRule output = new StdoutOutputRule();

    @Rule
    public final TestRule chain = outerRule(output).around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/client.hello.malformed/client"})
    @Configure(name = "zilla.exporter.stdout.output",
        value = "io.aklivity.zilla.runtime.exporter.stdout.internal.events.StdoutOutputRule.OUT")
    public void shouldResetMalformedClientHello() throws Exception
    {
        k3po.finish();
        output.expect(Pattern.compile("test.net0 - \\[[^\\]]+\\] TLS_FAILED\n"));
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/server.handshake.timeout/client"})
    @Configure(name = "zilla.binding.tls.handshake.timeout", value = "1")
    @Configure(name = "zilla.exporter.stdout.output",
        value = "io.aklivity.zilla.runtime.exporter.stdout.internal.events.StdoutOutputRule.OUT")
    public void shouldTimeoutHandshake() throws Exception
    {
        k3po.finish();
        output.expect(Pattern.compile("test.net0 - \\[[^\\]]+\\] HANDSHAKE_FAILED\n"));
    }
}
