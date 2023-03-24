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
package io.aklivity.zilla.runtime.binding.http.internal.streams.rfc7540.server;

import static io.aklivity.zilla.runtime.binding.http.internal.HttpConfiguration.HTTP_CONCURRENT_STREAMS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class StartingIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/http/streams/network/rfc7540/starting/")
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/http/streams/application/rfc7540/starting/");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(8192)
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config/upgrade")
        .configure(HTTP_CONCURRENT_STREAMS, 100)
        .external("app0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.h2c.with.tls.and.alpn.h2/client" })
    public void shouldRejectUpgradeViaCleartextWithTlsAndAlpnH2() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.h2c.with.tls.and.alpn.http1.1/client",
        "${app}/upgrade.https/server" })
    public void shouldNotUpgradeViaCleartextWithTlsAndAlpnHttp11() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.h2c.with.tls.and.no.alpn/client",
        "${app}/upgrade.https/server" })
    public void shouldNotUpgradeViaCleartextWithTlsAndNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.h2c.with.no.tls/client",
        "${app}/upgrade.http/server" })
    public void shouldUpgradeViaCleartextWithNoTls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.pri.with.no.tls/client",
        "${app}/upgrade.http/server" })
    public void shouldUpgradeViaPriorKnowledgeWithNoTls() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.pri.with.tls.and.no.alpn/client" })
    public void shouldNotUpgradeViaPriorKnowledgeWithTlsAndNoAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("server.yaml")
    @Specification({
        "${net}/upgrade.pri.with.tls.and.alpn.http1.1/client" })
    public void shouldNotUpgradeViaPriorKnowledgeWithTlsAndAlpnHttp11() throws Exception
    {
        k3po.finish();
    }
}
