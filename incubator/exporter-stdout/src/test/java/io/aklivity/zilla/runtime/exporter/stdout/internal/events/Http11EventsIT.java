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

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_SERVER_HEADER;
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

public class Http11EventsIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7230/authorization")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7230/authorization");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(HTTP_SERVER_HEADER, "Zilla")
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/v1.1")
        .external("app0")
        .clean();

    private final StdoutOutputRule output = new StdoutOutputRule();

    @Rule
    public final TestRule chain = outerRule(output).around(engine).around(k3po).around(timeout);

    @Test
    @Configuration("my_server.authorization.credentials.yaml")
    @Specification({
        "${net}/reject.credentials.header/client",
    })
    @Configure(name = "zilla.exporter.stdout.output",
        value = "io.aklivity.zilla.runtime.exporter.stdout.internal.events.StdoutOutputRule.OUT")
    public void shouldRejectCredentialsHeader() throws Exception
    {
        k3po.finish();
        output.expect(Pattern.compile("test.net0 test \\[[^\\]]+\\] AUTHORIZATION_FAILED\n"));
    }
}