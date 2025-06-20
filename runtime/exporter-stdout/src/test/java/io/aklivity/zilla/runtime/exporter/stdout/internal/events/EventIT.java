/*
 * Copyright 2021-2024 Aklivity Inc
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

import static io.aklivity.zilla.runtime.exporter.stdout.internal.StdoutExporterConfigurationTest.STDOUT_OUTPUT_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.util.regex.Pattern;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class EventIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/runtime/exporter/stdout/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/runtime/exporter/stdout/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/exporter/stdout/config")
        .external("app0")
        .clean();

    private final StdoutOutputRule output = new StdoutOutputRule();

    @Rule
    public final TestRule chain = outerRule(engine).around(output).around(k3po).around(timeout);

    @Test
    @Configuration("server.event.yaml")
    @Specification({
        "${net}/client",
        "${app}/server"
    })
    @Configure(name = STDOUT_OUTPUT_NAME,
        value = "io.aklivity.zilla.runtime.exporter.stdout.internal.events.StdoutOutputRule.OUT")
    public void shouldLogEvents() throws Exception
    {
        k3po.finish();
        output.expect(Pattern.compile("test.net0 \\[[^\\]]+\\] \\[[^\\]]+\\] BINDING_TEST_CONNECTED test event message\n"));
    }
}
