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
package io.aklivity.zilla.runtime.model.json.internal;

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

public class JsonModelIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/model/json/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/model/json/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/model/json/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("value.yaml")
    @Specification({
        "${net}/client.sent.json.valid/client",
        "${app}/client.sent.json.valid/server"
    })
    public void shouldForwardValidJson() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.yaml")
    @Specification({
        "${net}/client.sent.json.missing.required/client",
        "${app}/client.sent.json.missing.required/server"
    })
    public void shouldRejectJsonMissingRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.yaml")
    @Specification({
        "${net}/client.sent.json.malformed/client",
        "${app}/client.sent.json.malformed/server"
    })
    public void shouldRejectJsonMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.id.string.yaml")
    @Specification({
        "${net}/client.sent.json.schema.violation/client",
        "${app}/client.sent.json.schema.violation/server"
    })
    public void shouldRejectJsonSchemaViolation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.id.string.lenient.yaml")
    @Specification({
        "${net}/client.sent.json.schema.violation.lenient/client",
        "${app}/client.sent.json.schema.violation.lenient/server"
    })
    public void shouldForwardJsonSchemaViolationLenient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.strict.yaml")
    @Specification({
        "${net}/client.sent.json.additional.property/client",
        "${app}/client.sent.json.additional.property/server"
    })
    public void shouldRejectJsonAdditionalProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.strict.yaml")
    @Specification({
        "${net}/client.sent.json.additional.property.fragmented/client",
        "${app}/client.sent.json.additional.property.fragmented/server"
    })
    public void shouldRejectJsonAdditionalPropertyFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.strict.yaml")
    @Specification({
        "${net}/client.sent.json.strict.missing.required/client",
        "${app}/client.sent.json.strict.missing.required/server"
    })
    public void shouldRejectJsonStrictMissingRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("value.strict.yaml")
    @Specification({
        "${net}/client.sent.json.strict.missing.required.fragmented/client",
        "${app}/client.sent.json.strict.missing.required.fragmented/server"
    })
    public void shouldRejectJsonStrictMissingRequiredFragmented() throws Exception
    {
        k3po.finish();
    }
}
