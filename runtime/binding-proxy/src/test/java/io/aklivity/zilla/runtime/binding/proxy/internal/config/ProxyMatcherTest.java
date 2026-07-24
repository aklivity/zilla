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
package io.aklivity.zilla.runtime.binding.proxy.internal.config;

import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressProtocol.STREAM;
import static org.agrona.BitUtil.fromHex;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.agrona.LangUtil;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.proxy.ProxyAddressConfig;
import io.aklivity.zilla.config.binding.proxy.ProxyConditionConfig;
import io.aklivity.zilla.config.binding.proxy.ProxyInfoConfig;
import io.aklivity.zilla.config.binding.proxy.ProxySecureInfoConfig;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class ProxyMatcherTest
{
    private ProxyBeginExFW.Builder builder;

    @Before
    public void initBuilder()
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);
        builder = new ProxyBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(0);
    }

    @Test
    public void shouldMatchCondition()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder().build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithTransport()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder().transport("stream").build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithTransport()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder().transport("datagram").build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithFamily()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder().family("inet4").build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithFamily()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder().family("inet6").build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithSource()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder()
                .source(ProxyAddressConfig.builder()
                    .host("192.168.0.0/24")
                    .port(32768)
                    .build())
                .build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithSource()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder()
                .source(ProxyAddressConfig.builder()
                    .host("127.0.0.0/24")
                    .port(32768)
                    .build())
                .build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithDestination()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder()
                .destination(ProxyAddressConfig.builder()
                    .host("192.168.0.0/24")
                    .port(443)
                    .build())
                .build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithDestination()
    {
        ProxyConditionConfig condition = ProxyConditionConfig.builder()
                .destination(ProxyAddressConfig.builder()
                    .host("127.0.0.0/24")
                    .port(443)
                    .build())
                .build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithAlpn()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().alpn("echo").build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.alpn("echo"))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithAlpn()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().alpn("http/1.1").build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.alpn("echo"))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithAuthority()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().authority("example.com").build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.authority("example.com"))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithAuthority()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().authority("example.net").build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.authority("example.com"))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithIdentity()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().identity(fromHex("12345678")).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.identity(v -> v.value(x -> x.set(fromHex("12345678")))))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithIdentity()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().identity(fromHex("87654321")).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.identity(v -> v.value(x -> x.set(fromHex("12345678")))))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithNamespace()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().namespace("example").build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.namespace("example"))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithNamespace()
    {
        ProxyInfoConfig info = ProxyInfoConfig.builder().namespace("mismatch").build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.namespace("example"))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithSecureVersion()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().version("TLSv1.3").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.version("TLSv1.3")))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithSecureVersion()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().version("TLSv1.2").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.version("TLSv1.3")))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithSecureCipher()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().cipher("ECDHE-RSA-AES128-GCM-SHA256").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.cipher("ECDHE-RSA-AES128-GCM-SHA256")))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithSecureCipher()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().cipher("ECDH-ECDSA-AES256-GCM-SHA384").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.cipher("ECDHE-RSA-AES128-GCM-SHA256")))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithSecureKey()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().key("RSA2048").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.key("RSA2048")))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithSecureKey()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().key("RSA1024").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.key("RSA2048")))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithSecureName()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().name("name@domain").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.name("name@domain")))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithSecureName()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().name("other@domain").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.name("name@domain")))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    @Test
    public void shouldMatchConditionWithSecureSignature()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().signature("SHA256").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.signature("SHA256")))
                .build();

        assertTrue(matcher.matches(beginEx));
    }

    @Test
    public void shouldNotMatchConditionWithSecureSignature()
    {
        ProxySecureInfoConfig secureInfo = ProxySecureInfoConfig.builder().signature("SHA384").build();
        ProxyInfoConfig info = ProxyInfoConfig.builder().secure(secureInfo).build();
        ProxyConditionConfig condition = ProxyConditionConfig.builder().info(info).build();
        ProxyConditionMatcher matcher = new ProxyConditionMatcher(condition);

        ProxyBeginExFW beginEx = builder
                .address(a -> a.inet4(i -> i
                        .protocol(p -> p.set(STREAM))
                        .source(s -> s.set(resolveHost("192.168.0.1")))
                        .destination(d -> d.set(resolveHost("192.168.0.254")))
                        .sourcePort(32768)
                        .destinationPort(443)))
                .infosItem(i -> i.secure(s -> s.signature("SHA256")))
                .build();

        assertFalse(matcher.matches(beginEx));
    }

    private static byte[] resolveHost(
        String host)
    {
        byte[] address = null;

        try
        {
            InetAddress inet = InetAddress.getByName(host);
            address = inet.getAddress();
        }
        catch (UnknownHostException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return address;
    }
}
