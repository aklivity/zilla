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
package io.aklivity.zilla.runtime.binding.proxy.internal.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.agrona.LangUtil;
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
import io.aklivity.zilla.runtime.engine.test.annotation.Configure;

public class ProxyClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/proxy/streams/application")
        .addScriptRoot("net", "io/aklivity/zilla/specs/binding/proxy/streams/network.v2");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .commandBufferCapacity(1024)
        .responseBufferCapacity(1024)
        .counterValuesBufferCapacity(4096)
        .configurationRoot("io/aklivity/zilla/specs/binding/proxy/config")
        .external("net0")
        .clean();

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local/client",
        "${net}/connected.local/server" })
    public void shouldConnectLocal() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.client.sent.data/client",
        "${net}/connected.local.client.sent.data/server"})
    public void shouldConnectLocalClientSendsData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.client.sent.flush/client",
        "${net}/connected.local.client.sent.flush/server"})
    public void shouldConnectLocalClientSendsFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.client.sent.challenge/client",
        "${net}/connected.local.client.sent.challenge/server"})
    public void shouldConnectLocalClientSendsChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.client.sent.abort/client",
        "${net}/connected.local.client.sent.abort/server"})
    public void shouldConnectLocalClientSendsAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.client.sent.close/client",
        "${net}/connected.local.client.sent.close/server"})
    public void shouldConnectLocalClientSendsClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.client.sent.reset/client",
        "${net}/connected.local.client.sent.reset/server"})
    public void shouldConnectLocalClientSendsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.server.sent.data/client",
        "${net}/connected.local.server.sent.data/server"})
    public void shouldConnectLocalServerSendsData() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.server.sent.flush/client",
        "${net}/connected.local.server.sent.flush/server"})
    public void shouldConnectLocalServerSendsFlush() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.server.sent.challenge/client",
        "${net}/connected.local.server.sent.challenge/server"})
    public void shouldConnectLocalServerSendsChallenge() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.server.sent.abort/client",
        "${net}/connected.local.server.sent.abort/server"})
    public void shouldConnectLocalServerSendsAbort() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.server.sent.close/client",
        "${net}/connected.local.server.sent.close/server"})
    public void shouldConnectLocalServerSendsClose() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.local.server.sent.reset/client",
        "${net}/connected.local.server.sent.reset/server"})
    public void shouldConnectLocalServerSendsReset() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.unresolved/client",
        "${net}/connected.tcp4/server"})
    @Configure(name = "zilla.engine.host.resolver",
               value = "io.aklivity.zilla.runtime.binding.proxy.internal.streams.ProxyClientIT::resolveInet4")
    public void shouldConnectTcp4Unresolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4/client",
        "${net}/connected.tcp4/server"})
    public void shouldConnectTcp4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.alpn/client",
        "${net}/connected.tcp4.alpn/server"})
    public void shouldConnectTcp4WithAlpn() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.authority/client",
        "${net}/connected.tcp4.authority/server"})
    public void shouldConnectTcp4WithAuthority() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.crc32c/client",
        "${net}/connected.tcp4.crc32c/server"})
    public void shouldConnectTcp4WithCrc32c() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.identity/client",
        "${net}/connected.tcp4.identity/server"})
    public void shouldConnectTcp4WithIdentity() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.namespace/client",
        "${net}/connected.tcp4.namespace/server"})
    public void shouldConnectTcp4WithNamespace() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.ssl/client",
        "${net}/connected.tcp4.ssl/server"})
    public void shouldConnectTcp4WithSsl() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.ssl.client.cert/client",
        "${net}/connected.tcp4.ssl.client.cert/server"})
    public void shouldConnectTcp4WithSslClientCertificate() throws Exception
    {
        k3po.finish();
    }

    @Ignore("TODO")
    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp4.ssl.client.cert.session/client",
        "${net}/connected.tcp4.ssl.client.cert.session/server"})
    public void shouldConnectTcp4WithSslClientCertificateSession() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.udp4/client",
        "${net}/connected.udp4/server"})
    public void shouldConnectUdp4() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.tcp.yaml")
    @Specification({
        "${app}/connected.tcp6.unresolved/client",
        "${net}/connected.tcp6/server"})
    @Configure(name = "zilla.engine.host.resolver",
               value = "io.aklivity.zilla.runtime.binding.proxy.internal.streams.ProxyClientIT::resolveInet6")
    public void shouldConnectTcp6Unresolved() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.tcp6/client",
        "${net}/connected.tcp6/server"})
    public void shouldConnectTcp6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.udp6/client",
        "${net}/connected.udp6/server"})
    public void shouldConnectUdp6() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.sock.stream/client",
        "${net}/connected.sock.stream/server"})
    public void shouldConnectSockStream() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("client.yaml")
    @Specification({
        "${app}/connected.sock.datagram/client",
        "${net}/connected.sock.datagram/server"})
    public void shouldConnectSockDatagram() throws Exception
    {
        k3po.finish();
    }

    public static InetAddress[] resolveInet4(
        String host)
    {
        InetAddress[] resolved = null;

        try
        {
            InetAddress inet;
            if ("example.com".equals(host))
            {
                byte[] addr = InetAddress.getByName("192.168.0.254").getAddress();
                inet = InetAddress.getByAddress(host, addr);
            }
            else
            {
                inet = InetAddress.getByName(host);
            }
            resolved = new InetAddress[] { inet };
        }
        catch (UnknownHostException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return resolved;
    }

    public static InetAddress[] resolveInet6(
        String host)
    {
        InetAddress[] resolved = null;

        try
        {
            InetAddress inet;
            if ("example.com".equals(host))
            {
                byte[] addr = InetAddress.getByName("fd12:3456:789a:1::fe").getAddress();
                inet = InetAddress.getByAddress(host, addr);
            }
            else
            {
                inet = InetAddress.getByName(host);
            }
            resolved = new InetAddress[] { inet };
        }
        catch (UnknownHostException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return resolved;
    }
}
