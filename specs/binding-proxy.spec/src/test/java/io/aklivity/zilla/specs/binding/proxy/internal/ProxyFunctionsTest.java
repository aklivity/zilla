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
package io.aklivity.zilla.specs.binding.proxy.internal;

import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyAddressFamily.INET;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyAddressFamily.INET4;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyAddressFamily.INET6;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyAddressFamily.NONE;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyAddressFamily.UNIX;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyAddressProtocol.STREAM;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyInfoType.ALPN;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyInfoType.AUTHORITY;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyInfoType.IDENTITY;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyInfoType.NAMESPACE;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyInfoType.SECURE;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxySecureInfoType.CIPHER;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxySecureInfoType.KEY;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxySecureInfoType.NAME;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxySecureInfoType.SIGNATURE;
import static io.aklivity.zilla.specs.binding.proxy.internal.types.ProxySecureInfoType.VERSION;
import static org.agrona.BitUtil.fromHex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Method;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import javax.el.ELContext;
import javax.el.FunctionMapper;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.internal.el.ExpressionContext;

import io.aklivity.zilla.specs.binding.proxy.internal.types.ProxyInfoFW;
import io.aklivity.zilla.specs.binding.proxy.internal.types.stream.ProxyBeginExFW;

public class ProxyFunctionsTest
{
    @Test
    public void shouldResolveFunction() throws Exception
    {
        final ELContext ctx = new ExpressionContext();
        final FunctionMapper mapper = ctx.getFunctionMapper();
        final Method function = mapper.resolveFunction("proxy", "beginEx");

        assertNotNull(function);
        assertSame(ProxyFunctions.class, function.getDeclaringClass());
    }

    @Test
    public void shouldGenerateNoneBeginExtension() throws UnknownHostException
    {
        byte[] build = ProxyFunctions.beginEx()
                                     .typeId(0x01)
                                     .addressNone()
                                         .build()
                                     .info()
                                         .alpn("echo")
                                         .authority("example.com")
                                         .identity(fromHex("12345678"))
                                         .namespace("example")
                                         .secure()
                                             .version("TLSv1.3")
                                             .cipher("ECDHE-RSA-AES128-GCM-SHA256")
                                             .signature("SHA256")
                                             .name("name@domain")
                                             .key("RSA2048")
                                             .build()
                                         .build()
                                     .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        ProxyBeginExFW beginEx = new ProxyBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertNotNull(beginEx);
        assertEquals(0x01, beginEx.typeId());
        assertEquals(NONE, beginEx.address().kind());

        ProxyInfoFW info = new ProxyInfoFW();
        final DirectBuffer infos = beginEx.infos().items();
        for (int index = 0, offset = 0; offset < infos.capacity(); index++)
        {
            info.wrap(infos, offset, infos.capacity());
            switch (index)
            {
            case 0:
                assertEquals(ALPN, info.kind());
                assertEquals("echo", info.alpn().asString());
                break;
            case 1:
                assertEquals(AUTHORITY, info.kind());
                assertEquals("example.com", info.authority().asString());
                break;
            case 2:
                assertEquals(IDENTITY, info.kind());
                assertEquals(new UnsafeBuffer(fromHex("12345678")), info.identity().value().value());
                break;
            case 3:
                assertEquals(NAMESPACE, info.kind());
                assertEquals("example", info.namespace().asString());
                break;
            case 4:
                assertEquals(SECURE, info.kind());
                assertEquals(VERSION, info.secure().kind());
                assertEquals("TLSv1.3", info.secure().version().asString());
                break;
            case 5:
                assertEquals(SECURE, info.kind());
                assertEquals(CIPHER, info.secure().kind());
                assertEquals("ECDHE-RSA-AES128-GCM-SHA256", info.secure().cipher().asString());
                break;
            case 6:
                assertEquals(SECURE, info.kind());
                assertEquals(SIGNATURE, info.secure().kind());
                assertEquals("SHA256", info.secure().signature().asString());
                break;
            case 7:
                assertEquals(SECURE, info.kind());
                assertEquals(NAME, info.secure().kind());
                assertEquals("name@domain", info.secure().name().asString());
                break;
            case 8:
                assertEquals(SECURE, info.kind());
                assertEquals(KEY, info.secure().kind());
                assertEquals("RSA2048", info.secure().key().asString());
                break;
            }
            offset = info.limit();
        }
    }

    @Test
    public void shouldGenerateInetBeginExtension() throws UnknownHostException
    {
        byte[] build = ProxyFunctions.beginEx()
                                     .typeId(0x01)
                                     .addressInet()
                                         .protocol("stream")
                                         .source("*")
                                         .destination("example.com")
                                         .sourcePort(32768)
                                         .destinationPort(443)
                                         .build()
                                     .info()
                                         .alpn("echo")
                                         .authority("example.com")
                                         .identity(fromHex("12345678"))
                                         .namespace("example")
                                         .secure()
                                             .version("TLSv1.3")
                                             .cipher("ECDHE-RSA-AES128-GCM-SHA256")
                                             .signature("SHA256")
                                             .name("name@domain")
                                             .key("RSA2048")
                                             .build()
                                         .build()
                                     .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        ProxyBeginExFW beginEx = new ProxyBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertNotNull(beginEx);
        assertEquals(0x01, beginEx.typeId());
        assertEquals(INET, beginEx.address().kind());
        assertEquals(STREAM, beginEx.address().inet().protocol().get());
        assertEquals("*", beginEx.address().inet().source().asString());
        assertEquals("example.com", beginEx.address().inet().destination().asString());
        assertEquals(32768, beginEx.address().inet().sourcePort());
        assertEquals(443, beginEx.address().inet().destinationPort());

        ProxyInfoFW info = new ProxyInfoFW();
        final DirectBuffer infos = beginEx.infos().items();
        for (int index = 0, offset = 0; offset < infos.capacity(); index++)
        {
            info.wrap(infos, offset, infos.capacity());
            switch (index)
            {
            case 0:
                assertEquals(ALPN, info.kind());
                assertEquals("echo", info.alpn().asString());
                break;
            case 1:
                assertEquals(AUTHORITY, info.kind());
                assertEquals("example.com", info.authority().asString());
                break;
            case 2:
                assertEquals(IDENTITY, info.kind());
                assertEquals(new UnsafeBuffer(fromHex("12345678")), info.identity().value().value());
                break;
            case 3:
                assertEquals(NAMESPACE, info.kind());
                assertEquals("example", info.namespace().asString());
                break;
            case 4:
                assertEquals(SECURE, info.kind());
                assertEquals(VERSION, info.secure().kind());
                assertEquals("TLSv1.3", info.secure().version().asString());
                break;
            case 5:
                assertEquals(SECURE, info.kind());
                assertEquals(CIPHER, info.secure().kind());
                assertEquals("ECDHE-RSA-AES128-GCM-SHA256", info.secure().cipher().asString());
                break;
            case 6:
                assertEquals(SECURE, info.kind());
                assertEquals(SIGNATURE, info.secure().kind());
                assertEquals("SHA256", info.secure().signature().asString());
                break;
            case 7:
                assertEquals(SECURE, info.kind());
                assertEquals(NAME, info.secure().kind());
                assertEquals("name@domain", info.secure().name().asString());
                break;
            case 8:
                assertEquals(SECURE, info.kind());
                assertEquals(KEY, info.secure().kind());
                assertEquals("RSA2048", info.secure().key().asString());
                break;
            }
            offset = info.limit();
        }
    }

    @Test
    public void shouldGenerateInet4BeginExtension() throws UnknownHostException
    {
        byte[] build = ProxyFunctions.beginEx()
                                     .typeId(0x01)
                                     .addressInet4()
                                         .protocol("stream")
                                         .source("192.168.0.1")
                                         .destination("192.168.0.254")
                                         .sourcePort(32768)
                                         .destinationPort(443)
                                         .build()
                                     .info()
                                         .alpn("echo")
                                         .authority("example.com")
                                         .identity(fromHex("12345678"))
                                         .namespace("example")
                                         .secure()
                                             .version("TLSv1.3")
                                             .cipher("ECDHE-RSA-AES128-GCM-SHA256")
                                             .signature("SHA256")
                                             .name("name@domain")
                                             .key("RSA2048")
                                             .build()
                                         .build()
                                     .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        ProxyBeginExFW beginEx = new ProxyBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertNotNull(beginEx);
        assertEquals(0x01, beginEx.typeId());
        assertEquals(INET4, beginEx.address().kind());
        assertEquals(STREAM, beginEx.address().inet4().protocol().get());
        assertEquals(new UnsafeBuffer(fromHex("c0a80001")), beginEx.address().inet4().source().value());
        assertEquals(new UnsafeBuffer(fromHex("c0a800fe")), beginEx.address().inet4().destination().value());
        assertEquals(32768, beginEx.address().inet4().sourcePort());
        assertEquals(443, beginEx.address().inet4().destinationPort());

        ProxyInfoFW info = new ProxyInfoFW();
        final DirectBuffer infos = beginEx.infos().items();
        for (int index = 0, offset = 0; offset < infos.capacity(); index++)
        {
            info.wrap(infos, offset, infos.capacity());
            switch (index)
            {
            case 0:
                assertEquals(ALPN, info.kind());
                assertEquals("echo", info.alpn().asString());
                break;
            case 1:
                assertEquals(AUTHORITY, info.kind());
                assertEquals("example.com", info.authority().asString());
                break;
            case 2:
                assertEquals(IDENTITY, info.kind());
                assertEquals(new UnsafeBuffer(fromHex("12345678")), info.identity().value().value());
                break;
            case 3:
                assertEquals(NAMESPACE, info.kind());
                assertEquals("example", info.namespace().asString());
                break;
            case 4:
                assertEquals(SECURE, info.kind());
                assertEquals(VERSION, info.secure().kind());
                assertEquals("TLSv1.3", info.secure().version().asString());
                break;
            case 5:
                assertEquals(SECURE, info.kind());
                assertEquals(CIPHER, info.secure().kind());
                assertEquals("ECDHE-RSA-AES128-GCM-SHA256", info.secure().cipher().asString());
                break;
            case 6:
                assertEquals(SECURE, info.kind());
                assertEquals(SIGNATURE, info.secure().kind());
                assertEquals("SHA256", info.secure().signature().asString());
                break;
            case 7:
                assertEquals(SECURE, info.kind());
                assertEquals(NAME, info.secure().kind());
                assertEquals("name@domain", info.secure().name().asString());
                break;
            case 8:
                assertEquals(SECURE, info.kind());
                assertEquals(KEY, info.secure().kind());
                assertEquals("RSA2048", info.secure().key().asString());
                break;
            }
            offset = info.limit();
        }
    }

    @Test
    public void shouldMatchInetBeginExtension() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .source("*")
                                                 .destination("example.com")
                                                 .sourcePort(32768)
                                                 .destinationPort(443)
                                                 .build()
                                             .info()
                                                 .alpn("echo")
                                                 .authority("example.com")
                                                 .identity(fromHex("12345678"))
                                                 .namespace("example")
                                                 .secure()
                                                     .version("TLSv1.3")
                                                     .cipher("ECDHE-RSA-AES128-GCM-SHA256")
                                                     .signature("SHA256")
                                                     .name("name@domain")
                                                     .key("RSA2048")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.alpn("echo"))
            .infosItem(i -> i.authority("example.com"))
            .infosItem(i -> i.identity(id -> id.value(v -> v.set(fromHex("12345678")))))
            .infosItem(i -> i.namespace("example"))
            .infosItem(i -> i.secure(s -> s.version("TLSv1.3")))
            .infosItem(i -> i.secure(s -> s.cipher("ECDHE-RSA-AES128-GCM-SHA256")))
            .infosItem(i -> i.secure(s -> s.signature("SHA256")))
            .infosItem(i -> i.secure(s -> s.name("name@domain")))
            .infosItem(i -> i.secure(s -> s.key("RSA2048")))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchNoneBeginExtension() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressNone()
                                                 .build()
                                             .info()
                                                 .alpn("echo")
                                                 .authority("example.com")
                                                 .identity(fromHex("12345678"))
                                                 .namespace("example")
                                                 .secure()
                                                     .version("TLSv1.3")
                                                     .cipher("ECDHE-RSA-AES128-GCM-SHA256")
                                                     .signature("SHA256")
                                                     .name("name@domain")
                                                     .key("RSA2048")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.none(n -> {}))
            .infosItem(i -> i.alpn("echo"))
            .infosItem(i -> i.authority("example.com"))
            .infosItem(i -> i.identity(id -> id.value(v -> v.set(fromHex("12345678")))))
            .infosItem(i -> i.namespace("example"))
            .infosItem(i -> i.secure(s -> s.version("TLSv1.3")))
            .infosItem(i -> i.secure(s -> s.cipher("ECDHE-RSA-AES128-GCM-SHA256")))
            .infosItem(i -> i.secure(s -> s.signature("SHA256")))
            .infosItem(i -> i.secure(s -> s.name("name@domain")))
            .infosItem(i -> i.secure(s -> s.key("RSA2048")))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInetBeginExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInetBeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .protocol("stream")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }


    @Test
    public void shouldMatchInetBeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .source("*")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInetBeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .destination("example.com")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .protocol("datagram")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .source("+")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .destination("example.net")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionSourcePort() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .sourcePort(32767)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionDestinationPort() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet()
                                                 .destinationPort(444)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionAlpn() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .alpn("dot")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.alpn("echo"))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionAuthority() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .authority("example.net")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.authority("example.com"))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionIdentity() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .identity(fromHex("12345679"))
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.identity(id -> id.value(v -> v.set(fromHex("12345678")))))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionNamespace() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .namespace("exemplar")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.namespace("example"))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionSecureProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .version("TLSv1.2")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.version("TLSv1.3")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionSecureCipher() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .cipher("ECDHE-RSA-AES128-GCM-SHA257")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.cipher("ECDHE-RSA-AES128-GCM-SHA256")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionSecureSignature() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .signature("SHA257")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.signature("SHA256")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionSecureName() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .name("other@domain")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.name("name@domain")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInetBeginExtensionSecureKey() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .key("RSA2049")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet(i -> i.protocol(p -> p.set(STREAM))
                                       .source("*")
                                       .destination("example.com")
                                       .sourcePort(32768)
                                       .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.key("RSA2048")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet4BeginExtension() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .source("192.168.0.1")
                                                 .destination("192.168.0.254")
                                                 .sourcePort(32768)
                                                 .destinationPort(443)
                                                 .build()
                                             .info()
                                                 .alpn("echo")
                                                 .authority("example.com")
                                                 .identity(fromHex("12345678"))
                                                 .namespace("example")
                                                 .secure()
                                                     .version("TLSv1.3")
                                                     .cipher("ECDHE-RSA-AES128-GCM-SHA256")
                                                     .signature("SHA256")
                                                     .name("name@domain")
                                                     .key("RSA2048")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.alpn("echo"))
            .infosItem(i -> i.authority("example.com"))
            .infosItem(i -> i.identity(id -> id.value(v -> v.set(fromHex("12345678")))))
            .infosItem(i -> i.namespace("example"))
            .infosItem(i -> i.secure(s -> s.version("TLSv1.3")))
            .infosItem(i -> i.secure(s -> s.cipher("ECDHE-RSA-AES128-GCM-SHA256")))
            .infosItem(i -> i.secure(s -> s.signature("SHA256")))
            .infosItem(i -> i.secure(s -> s.name("name@domain")))
            .infosItem(i -> i.secure(s -> s.key("RSA2048")))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet4BeginExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet4BeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .protocol("stream")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet4BeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .source("192.168.0.1")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet4BeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .destination("192.168.0.254")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .protocol("datagram")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .source("192.168.0.2")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .destination("192.168.0.253")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionSourcePort() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .sourcePort(32767)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionDestinationPort() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet4()
                                                 .destinationPort(444)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchNoneBeginExtension() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressNone()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionAlpn() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .alpn("dot")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.alpn("echo"))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionAuthority() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .authority("example.net")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.authority("example.com"))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionIdentity() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .identity(fromHex("12345679"))
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.identity(id -> id.value(v -> v.set(fromHex("12345678")))))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionNamespace() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .namespace("exemplar")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.namespace("example"))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionSecureProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .version("TLSv1.2")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.version("TLSv1.3")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionSecureCipher() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .cipher("ECDHE-RSA-AES128-GCM-SHA257")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.cipher("ECDHE-RSA-AES128-GCM-SHA256")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionSecureSignature() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .signature("SHA257")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.signature("SHA256")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionSecureName() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .name("other@domain")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.name("name@domain")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet4BeginExtensionSecureKey() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .info()
                                                 .secure()
                                                     .key("RSA2049")
                                                     .build()
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .infosItem(i -> i.secure(s -> s.key("RSA2048")))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateInet6BeginExtension() throws UnknownHostException
    {
        byte[] build = ProxyFunctions.beginEx()
                                     .typeId(0x01)
                                     .addressInet6()
                                         .protocol("stream")
                                         .source("fd12:3456:789a:1::1")
                                         .destination("fd12:3456:789a:1::fe")
                                         .sourcePort(32768)
                                         .destinationPort(443)
                                         .build()
                                     .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        ProxyBeginExFW beginEx = new ProxyBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertNotNull(beginEx);
        assertEquals(0x01, beginEx.typeId());
        assertEquals(INET6, beginEx.address().kind());
        assertEquals(STREAM, beginEx.address().inet6().protocol().get());
        assertEquals(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")),
                beginEx.address().inet6().source().value());
        assertEquals(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")),
                beginEx.address().inet6().destination().value());
        assertEquals(32768, beginEx.address().inet6().sourcePort());
        assertEquals(443, beginEx.address().inet6().destinationPort());
    }

    @Test
    public void shouldMatchInet6BeginExtension() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .source("fd12:3456:789a:1::1")
                                                 .destination("fd12:3456:789a:1::fe")
                                                 .sourcePort(32768)
                                                 .destinationPort(443)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet6BeginExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet6BeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .protocol("stream")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet6BeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .source("fd12:3456:789a:1::1")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchInet6BeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .destination("fd12:3456:789a:1::fe")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet6BeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .protocol("datagram")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet6BeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .source("fd12:3456:789a:1::2")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet6BeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .destination("fd12:3456:789a:1::fd")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet6BeginExtensionSourcePort() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .sourcePort(32767)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchInet6BeginExtensionDestinationPort() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressInet6()
                                                 .destinationPort(444)
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet6(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("fd123456789a00010000000000000001")), 0, 16)
                                        .destination(new UnsafeBuffer(fromHex("fd123456789a000100000000000000fe")), 0, 16)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateUnixBeginExtension() throws UnknownHostException
    {
        byte[] build = ProxyFunctions.beginEx()
                                     .typeId(0x01)
                                     .addressUnix()
                                         .protocol("stream")
                                         .source("source-1234")
                                         .destination("destination-5678")
                                         .build()
                                     .build();
        DirectBuffer buffer = new UnsafeBuffer(build);
        ProxyBeginExFW beginEx = new ProxyBeginExFW().wrap(buffer, 0, buffer.capacity());
        assertNotNull(beginEx);
        assertEquals(0x01, beginEx.typeId());
        assertEquals(UNIX, beginEx.address().kind());
        assertEquals(STREAM, beginEx.address().unix().protocol().get());
        assertEquals(paddedUtf8("source-1234", 108), beginEx.address().unix().source().value());
        assertEquals(paddedUtf8("destination-5678", 108), beginEx.address().unix().destination().value());
    }

    @Test
    public void shouldMatchUnixBeginExtension() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressUnix()
                                                 .source("source-1234")
                                                 .destination("destination-5678")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchUnixBeginExtensionTypeId() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchUnixBeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressUnix()
                                                 .protocol("stream")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchUnixBeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressUnix()
                                                 .source("source-1234")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchUnixBeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressUnix()
                                                 .destination("destination-5678")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchUnixBeginExtensionProtocol() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressUnix()
                                                 .protocol("datagram")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchUnixBeginExtensionSource() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressUnix()
                                                 .source("source-12345")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchUnixBeginExtensionDestination() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .addressUnix()
                                                 .destination("destination-56789")
                                                 .build()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.unix(i -> i.protocol(p -> p.set(STREAM))
                                       .source(paddedUtf8("source-1234", 108), 0, 108)
                                       .destination(paddedUtf8("destination-5678", 108), 0, 108)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailWhenBufferEmpty() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenBufferIncomplete() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1);

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldFailWhenDoNotSetTypeId() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x01)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenTypeIdDoNotMatch() throws Exception
    {
        BytesMatcher matcher = ProxyFunctions.matchBeginEx()
                                             .typeId(0x01)
                                             .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(1024);

        new ProxyBeginExFW.Builder().wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0x02)
            .address(a -> a.inet4(i -> i.protocol(p -> p.set(STREAM))
                                        .source(new UnsafeBuffer(fromHex("c0a80001")), 0, 4)
                                        .destination(new UnsafeBuffer(fromHex("c0a800fe")), 0, 4)
                                        .sourcePort(32768)
                                        .destinationPort(443)))
            .build();

        matcher.match(byteBuf);
    }

    private static DirectBuffer paddedUtf8(
        String utf8,
        int length)
    {
        UnsafeBuffer padded = new UnsafeBuffer(new byte[length]);
        padded.putStringWithoutLengthUtf8(0, utf8);
        return padded;
    }
}
