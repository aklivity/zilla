/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.http.streams.network.rfc7540;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class StartingIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/starting");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.tls.and.alpn.h2/client",
        "${net}/upgrade.h2c.with.tls.and.alpn.h2/server",
    })
    public void shouldRejectUpgradeViaH2CWithTlsAndAlpnH2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.tls.and.alpn.http1.1/client",
        "${net}/upgrade.h2c.with.tls.and.alpn.http1.1/server",
    })
    public void shouldNotUpgradeViaH2CWithTlsAndAlpnHttp11() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.tls.and.no.alpn/client",
        "${net}/upgrade.h2c.with.tls.and.no.alpn/server",
    })
    public void shouldNotUpgradeViaH2CWithTlsAndNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.extra.settings/client",
        "${net}/upgrade.h2c.with.extra.settings/server",
    })
    public void shouldNotUpgradeViaH2CWithExtraSettings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.no.settings/client",
        "${net}/upgrade.h2c.with.no.settings/server",
    })
    public void shouldNotUpgradeViaH2CWithNoSettings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.no.tls/client",
        "${net}/upgrade.h2c.with.no.tls/server",
    })
    public void shouldUpgradeViaH2CWithNoTls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.proxy.address/client",
        "${net}/upgrade.h2c.with.proxy.address/server",
    })
    public void shouldUpgradeViaH2CWithProxyAddress() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.pri.with.no.tls/client",
        "${net}/upgrade.pri.with.no.tls/server",
    })
    public void shouldUpgradeViaPriorKnowledgeWithNoTls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.pri.with.partial.frame/client",
        "${net}/upgrade.pri.with.partial.frame/server",
    })
    public void shouldUpgradeViaPriorKnowledgeWithPartialFrame() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.pri.with.tls.and.alpn.http1.1/client",
        "${net}/upgrade.pri.with.tls.and.alpn.http1.1/server",
    })
    public void shouldNotUpgradeViaPriorKnowledgeWithTlsAndAlpnHttp11() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.pri.with.tls.and.no.alpn/client",
        "${net}/upgrade.pri.with.tls.and.no.alpn/server",
    })
    public void shouldNotUpgradeViaPriorKnowledgeWithTlsAndNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Ignore
    @Test
    @Specification({
        "${net}/upgrade.pri.with.tls.and.alpn.h2/client",
        "${net}/upgrade.pri.with.tls.and.alpn.h2/server",
    })
    public void shouldUpgradeViaPriorKnowledgeWithTlsAndAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.multiple.requests.pipelined/client",
        "${net}/upgrade.h2c.with.multiple.requests.pipelined/server"})
    public void shouldRejectViaH2cWithMultipleRequestsPipelined() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.window.update.overflow.error/client",
        "${net}/upgrade.h2c.with.window.update.overflow.error/server",
    })
    public void shouldUpgradeH2cWithWindowUpdateOverflowError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.large.request/client",
        "${net}/upgrade.h2c.with.large.request/server",
    })
    public void shouldUpgradeH2cWithLargeRequest() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.large.response/client",
        "${net}/upgrade.h2c.with.large.response/server",
    })
    public void shouldUpgradeH2cWithLargeResponse() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.window.limited/client",
        "${net}/upgrade.h2c.with.window.limited/server",
    })
    public void shouldUpgradeH2cWithWindowLimited() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.partial.frame/client",
        "${net}/upgrade.h2c.with.partial.frame/server"
    })
    public void shouldUpgradeViaCleartextWithPartialFrame() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.pri.with.split.preface/client",
        "${net}/upgrade.pri.with.split.preface/server"
    })
    public void shouldUpgradeViaPriorKnowledgeWithSplitPreface() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.duplicate.settings/client",
        "${net}/upgrade.h2c.with.duplicate.settings/server",
    })
    public void shouldUpgradeH2cWithDuplicateSettings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.invalid.settings/client",
        "${net}/upgrade.h2c.with.invalid.settings/server",
    })
    public void shouldUpgradeH2cWithInvalidSettings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.truncated.settings/client",
        "${net}/upgrade.h2c.with.truncated.settings/server",
    })
    public void shouldUpgradeH2cWithTruncatedSettings() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.settings.window.error/client",
        "${net}/upgrade.h2c.with.settings.window.error/server",
    })
    public void shouldUpgradeH2cWithSettingsWindowError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.settings.frame.size.error/client",
        "${net}/upgrade.h2c.with.settings.frame.size.error/server",
    })
    public void shouldUpgradeH2cWithSettingsFrameSizeError() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.unknown.authority/client",
        "${net}/upgrade.h2c.with.unknown.authority/server",
    })
    public void shouldUpgradeH2cWithUnknownAuthority() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.large.body/client",
        "${net}/upgrade.h2c.with.large.body/server",
    })
    public void shouldUpgradeH2cWithLargeBody() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${net}/upgrade.h2c.with.client.close/client",
        "${net}/upgrade.h2c.with.client.close/server",
    })
    public void shouldUpgradeH2cWithClientClose() throws Exception
    {
        k3po.finish();
    }
}
