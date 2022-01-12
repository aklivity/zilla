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
package io.aklivity.zilla.runtime.cog.tls.internal.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.ScriptProperty;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.cog.tls.internal.TlsConfigurationTest;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ClientFragmentedIT
{
    private final K3poRule k3po = new K3poRule()
            .addScriptRoot("app", "io/aklivity/zilla/specs/cog/tls/streams/application")
            .addScriptRoot("net", "io/aklivity/zilla/specs/cog/tls/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
            .directory("target/zilla-itests")
            .commandBufferCapacity(1024)
            .responseBufferCapacity(1024)
            .counterValuesBufferCapacity(8192)
            .configurationRoot("io/aklivity/zilla/specs/cog/tls/config")
            .external("net0")
            .clean();

    @Rule
    public final TestRule chain = outerRule(timeout).around(engine).around(k3po);

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/connection.established/client",
        "${net}/connection.established/server" })
    @Configure(name = TlsConfigurationTest.TLS_HANDSHAKE_WINDOW_BYTES_NAME, value = "8")
    public void shouldEstablishConnectionWithLimitedHandshakeWindow() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.json")
    @Specification({
        "${app}/echo.payload.length.100k/client",
        "${net}/echo.payload.length.100k/server"})
    @ScriptProperty({
        "clientWindow 8192" })
    public void shouldEchoPayloadLength100kWithLimitedPayloadWindow() throws Exception
    {
        k3po.finish();
    }
}
