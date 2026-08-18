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
package io.aklivity.zilla.runtime.model.core.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class CoreModelIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/model/core/streams/network")
        .addScriptRoot("app", "io/aklivity/zilla/specs/model/core/streams/application");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    // 64KB, raised from the 32KB default -- still well under client.received.bytes.ext.uppercase.100k's
    // ~100KB value, so that scenario genuinely forces the ModelExt pipeline's OVERFLOW/drain-across-calls
    // path rather than sidestepping it with an oversized buffer.
    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(4096)
        .configure(EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY, 65_536)
        .configurationRoot("io/aklivity/zilla/specs/model/core/config")
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("string.yaml")
    @Specification({
        "${net}/client.sent.string.valid/client",
        "${app}/client.sent.string.valid/server"
    })
    public void shouldForwardValidString() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("string.pattern.yaml")
    @Specification({
        "${net}/client.sent.string.matching.pattern/client",
        "${app}/client.sent.string.matching.pattern/server"
    })
    public void shouldForwardStringMatchingPattern() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("string.pattern.yaml")
    @Specification({
        "${net}/client.sent.string.invalid.pattern/client",
        "${app}/client.sent.string.invalid.pattern/server"
    })
    public void shouldRejectStringInvalidPattern() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("int32.yaml")
    @Specification({
        "${net}/client.sent.int32/client",
        "${app}/client.sent.int32/server"
    })
    public void shouldForwardInt32() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("int32.yaml")
    @Specification({
        "${net}/client.sent.int32.invalid/client",
        "${app}/client.sent.int32.invalid/server"
    })
    public void shouldRejectInt32Invalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("int32.range.yaml")
    @Specification({
        "${net}/client.sent.int32.out.of.range/client",
        "${app}/client.sent.int32.out.of.range/server"
    })
    public void shouldRejectInt32OutOfRange() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("int32.lenient.yaml")
    @Specification({
        "${net}/client.sent.int32.lenient/client",
        "${app}/client.sent.int32.lenient/server"
    })
    public void shouldForwardInt32Lenient() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("int64.yaml")
    @Specification({
        "${net}/client.sent.int64/client",
        "${app}/client.sent.int64/server"
    })
    public void shouldForwardInt64() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("int64.yaml")
    @Specification({
        "${net}/client.sent.int64.invalid/client",
        "${app}/client.sent.int64.invalid/server"
    })
    public void shouldRejectInt64Invalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("double.yaml")
    @Specification({
        "${net}/client.sent.double/client",
        "${app}/client.sent.double/server"
    })
    public void shouldForwardDouble() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("double.yaml")
    @Specification({
        "${net}/client.sent.double.invalid/client",
        "${app}/client.sent.double.invalid/server"
    })
    public void shouldRejectDoubleInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("float.yaml")
    @Specification({
        "${net}/client.sent.float/client",
        "${app}/client.sent.float/server"
    })
    public void shouldForwardFloat() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("float.yaml")
    @Specification({
        "${net}/client.sent.float.invalid/client",
        "${app}/client.sent.float.invalid/server"
    })
    public void shouldRejectFloatInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("boolean.yaml")
    @Specification({
        "${net}/client.sent.boolean/client",
        "${app}/client.sent.boolean/server"
    })
    public void shouldForwardBoolean() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("boolean.yaml")
    @Specification({
        "${net}/client.sent.boolean.invalid/client",
        "${app}/client.sent.boolean.invalid/server"
    })
    public void shouldRejectBooleanInvalid() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("bytes.yaml")
    @Specification({
        "${net}/client.sent.bytes/client",
        "${app}/client.sent.bytes/server"
    })
    public void shouldForwardBytes() throws Exception
    {
        k3po.finish();
    }

    // The scenarios below drive a live engine with two test-only BytesModelExtFactorySpi installed
    // (registered under src/test only, never shipped in the production jar) to prove the ModelExt
    // composition mechanism -- apply, fragment streaming, OVERFLOW/drain, withhold, reject, and either
    // direction -- works end-to-end through a real engine, independent of any specific installed
    // extension's own tests. One overrides decode only, the other encode only.

    @Test
    @Configuration("bytes.yaml")
    @Specification({
        "${net}/client.received.bytes.ext.uppercase/client",
        "${app}/client.received.bytes.ext.uppercase/server"
    })
    public void shouldApplyExtensionOnReply() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("bytes.yaml")
    @Specification({
        "${net}/client.received.bytes.ext.uppercase.100k/client",
        "${app}/client.received.bytes.ext.uppercase.100k/server"
    })
    public void shouldDrainExtensionOverflowAcrossMultipleFrames() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("bytes.yaml")
    @Specification({
        "${net}/client.received.bytes.ext.withhold/client",
        "${app}/client.received.bytes.ext.withhold/server"
    })
    public void shouldAbortReplyWhenExtensionWithholdsValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("bytes.yaml")
    @Specification({
        "${net}/client.received.bytes.ext.reject/client",
        "${app}/client.received.bytes.ext.reject/server"
    })
    public void shouldAbortReplyWhenExtensionRejectsValue() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("bytes.yaml")
    @Specification({
        "${net}/client.sent.bytes.ext.uppercase/client",
        "${app}/client.sent.bytes.ext.uppercase/server"
    })
    public void shouldApplyExtensionOnEncodeDirection() throws Exception
    {
        k3po.finish();
    }
}
