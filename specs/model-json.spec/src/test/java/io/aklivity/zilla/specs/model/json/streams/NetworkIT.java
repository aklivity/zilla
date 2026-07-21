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
package io.aklivity.zilla.specs.model.json.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class NetworkIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/model/json/streams/network");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/client.sent.json.valid/client",
        "${net}/client.sent.json.valid/server"
    })
    public void shouldForwardValidJson() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.missing.required/client",
        "${net}/client.sent.json.missing.required/server"
    })
    public void shouldRejectJsonMissingRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.malformed/client",
        "${net}/client.sent.json.malformed/server"
    })
    public void shouldRejectJsonMalformed() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.schema.violation/client",
        "${net}/client.sent.json.schema.violation/server"
    })
    public void shouldRejectJsonSchemaViolation() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.schema.violation.lenient/client",
        "${net}/client.sent.json.schema.violation.lenient/server"
    })
    public void shouldForwardJsonSchemaViolationLenient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.additional.property/client",
        "${net}/client.sent.json.additional.property/server"
    })
    public void shouldRejectJsonAdditionalProperty() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.additional.property.fragmented/client",
        "${net}/client.sent.json.additional.property.fragmented/server"
    })
    public void shouldRejectJsonAdditionalPropertyFragmented() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.strict.missing.required/client",
        "${net}/client.sent.json.strict.missing.required/server"
    })
    public void shouldRejectJsonStrictMissingRequired() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/client.sent.json.strict.missing.required.fragmented/client",
        "${net}/client.sent.json.strict.missing.required.fragmented/server"
    })
    public void shouldRejectJsonStrictMissingRequiredFragmented() throws Exception
    {
        k3po.finish();
    }
}
