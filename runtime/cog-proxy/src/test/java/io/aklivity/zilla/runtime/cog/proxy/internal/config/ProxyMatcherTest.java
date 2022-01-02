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
package io.aklivity.zilla.runtime.cog.proxy.internal.config;

import static io.aklivity.zilla.runtime.cog.proxy.internal.types.ProxyAddressProtocol.STREAM;
import static org.agrona.BitUtil.fromHex;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.cog.proxy.internal.types.stream.ProxyBeginExFW;

public class ProxyMatcherTest
{
    private ProxyBeginExFW.Builder builder;

    @Before
    public void initBuilder()
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
        builder = new ProxyBeginExFW.Builder().wrap(buffer, 0, buffer.capacity())
            .typeId(0);
    }

    @Test
    public void shouldMatchCondition()
    {
        ProxyCondition condition = new ProxyCondition(null, null, null, null, null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition("stream", null, null, null, null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition("datagram", null, null, null, null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition(null, "inet4", null, null, null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition(null, "inet6", null, null, null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition(null, null, new ProxyAddress("192.168.0.0/24", 32768), null, null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition(null, null, new ProxyAddress("127.0.0.0/24", 32768), null, null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition(null, null, null, new ProxyAddress("192.168.0.0/24", 443), null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyCondition condition = new ProxyCondition(null, null, null, new ProxyAddress("127.0.0.0/24", 443), null);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo("echo", null, null, null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo("http/1.1", null, null, null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo(null, "example.com", null, null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo(null, "example.net", null, null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo(null, null, fromHex("12345678"), null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo(null, null, fromHex("87654321"), null, null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo(null, null, null, "example", null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxyInfo info = new ProxyInfo(null, null, null, "mismatch", null);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo("TLSv1.3", null, null, null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo("TLSv1.2", null, null, null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, "ECDHE-RSA-AES128-GCM-SHA256", null, null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, "ECDH-ECDSA-AES256-GCM-SHA384", null, null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, "RSA2048", null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, "RSA1024", null, null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, null, "name@domain", null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, null, "other@domain", null);
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, null, null, "SHA256");
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
        ProxySecureInfo secureInfo = new ProxySecureInfo(null, null, null, null, "SHA384");
        ProxyInfo info = new ProxyInfo(null, null, null, null, secureInfo);
        ProxyCondition condition = new ProxyCondition(null, null, null, null, info);
        ProxyMatcher matcher = new ProxyMatcher(condition);

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
