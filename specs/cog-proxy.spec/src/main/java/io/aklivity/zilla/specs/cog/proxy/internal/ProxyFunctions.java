/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.specs.cog.proxy.internal;

import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressFamily.INET;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressFamily.INET4;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressFamily.INET6;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressFamily.NONE;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressFamily.UNIX;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyInfoType.ALPN;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyInfoType.AUTHORITY;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyInfoType.IDENTITY;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyInfoType.NAMESPACE;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyInfoType.SECURE;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxySecureInfoType.CIPHER;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxySecureInfoType.KEY;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxySecureInfoType.NAME;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxySecureInfoType.PROTOCOL;
import static io.aklivity.zilla.specs.cog.proxy.internal.types.ProxySecureInfoType.SIGNATURE;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.cog.proxy.internal.types.Array32FW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.OctetsFW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressFW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressInet4FW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressInet6FW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressNoneFW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressProtocol;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyAddressUnixFW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyInfoFW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxyInfoType;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxySecureInfoFW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.ProxySecureInfoType;
import io.aklivity.zilla.specs.cog.proxy.internal.types.String16FW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.String8FW;
import io.aklivity.zilla.specs.cog.proxy.internal.types.stream.ProxyBeginExFW;

public final class ProxyFunctions
{
    @Function
    public static ProxyBeginExBuilder beginEx()
    {
        return new ProxyBeginExBuilder();
    }

    @Function
    public static ProxyBeginExMatcherBuilder matchBeginEx()
    {
        return new ProxyBeginExMatcherBuilder();
    }

    public static final class ProxyBeginExBuilder
    {
        private final ProxyBeginExFW.Builder beginExRW;

        private ProxyBeginExBuilder()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);
            this.beginExRW = new ProxyBeginExFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public ProxyBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public ProxyAddressInetBuilder addressInet()
        {
            return new ProxyAddressInetBuilder();
        }

        public ProxyAddressInet4Builder addressInet4()
        {
            return new ProxyAddressInet4Builder();
        }

        public ProxyAddressInet6Builder addressInet6()
        {
            return new ProxyAddressInet6Builder();
        }

        public ProxyAddressUnixBuilder addressUnix()
        {
            return new ProxyAddressUnixBuilder();
        }

        public ProxyAddressNoneBuilder addressNone()
        {
            return new ProxyAddressNoneBuilder();
        }

        public ProxyInfoBuilder info()
        {
            return new ProxyInfoBuilder();
        }

        public byte[] build()
        {
            final ProxyBeginExFW beginEx = beginExRW.build();
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }

        public final class ProxyAddressInetBuilder
        {
            private final ProxyAddressFW.Builder addressRW = new ProxyAddressFW.Builder();

            private final ProxyAddressInetFW.Builder addressInetRW = new ProxyAddressInetFW.Builder();

            private ProxyAddressInetBuilder()
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[128]);
                addressRW.wrap(buffer, 0, buffer.capacity());
                addressInetRW.wrap(buffer, 1, buffer.capacity());
            }

            public ProxyAddressInetBuilder protocol(
                String protocol)
            {
                addressInetRW.protocol(p -> p.set(ProxyAddressProtocol.valueOf(protocol.toUpperCase())));
                return this;
            }

            public ProxyAddressInetBuilder source(
                String source) throws UnknownHostException
            {
                addressInetRW.source(source);
                return this;
            }

            public ProxyAddressInetBuilder destination(
                String destination) throws UnknownHostException
            {
                addressInetRW.destination(destination);
                return this;
            }

            public ProxyAddressInetBuilder sourcePort(
                int sourcePort)
            {
                addressInetRW.sourcePort(sourcePort);
                return this;
            }

            public ProxyAddressInetBuilder destinationPort(
                int destinationPort)
            {
                addressInetRW.destinationPort(destinationPort);
                return this;
            }

            public ProxyBeginExBuilder build()
            {
                beginExRW.address(addressRW.inet(addressInetRW.build()).build());
                return ProxyBeginExBuilder.this;
            }
        }

        public final class ProxyAddressInet4Builder
        {
            private final ProxyAddressFW.Builder addressRW = new ProxyAddressFW.Builder();

            private final ProxyAddressInet4FW.Builder addressInet4RW = new ProxyAddressInet4FW.Builder();

            private ProxyAddressInet4Builder()
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[14]);
                addressRW.wrap(buffer, 0, buffer.capacity());
                addressInet4RW.wrap(buffer, 1, buffer.capacity());
            }

            public ProxyAddressInet4Builder protocol(
                String protocol)
            {
                addressInet4RW.protocol(p -> p.set(ProxyAddressProtocol.valueOf(protocol.toUpperCase())));
                return this;
            }

            public ProxyAddressInet4Builder source(
                String source) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(source);
                final byte[] ip = inet.getAddress();
                addressInet4RW.source(s -> s.set(ip));
                return this;
            }

            public ProxyAddressInet4Builder destination(
                String destination) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(destination);
                final byte[] ip = inet.getAddress();
                addressInet4RW.destination(d -> d.set(ip));
                return this;
            }

            public ProxyAddressInet4Builder sourcePort(
                int sourcePort)
            {
                addressInet4RW.sourcePort(sourcePort);
                return this;
            }

            public ProxyAddressInet4Builder destinationPort(
                int destinationPort)
            {
                addressInet4RW.destinationPort(destinationPort);
                return this;
            }

            public ProxyBeginExBuilder build()
            {
                beginExRW.address(addressRW.inet4(addressInet4RW.build()).build());
                return ProxyBeginExBuilder.this;
            }
        }

        public final class ProxyAddressInet6Builder
        {
            private final ProxyAddressFW.Builder addressRW = new ProxyAddressFW.Builder();

            private final ProxyAddressInet6FW.Builder addressInet6RW = new ProxyAddressInet6FW.Builder();

            private ProxyAddressInet6Builder()
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[38]);
                addressRW.wrap(buffer, 0, buffer.capacity());
                addressInet6RW.wrap(buffer, 1, buffer.capacity());
            }

            public ProxyAddressInet6Builder protocol(
                String protocol)
            {
                addressInet6RW.protocol(p -> p.set(ProxyAddressProtocol.valueOf(protocol.toUpperCase())));
                return this;
            }

            public ProxyAddressInet6Builder source(
                String source) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(source);
                final byte[] ip = inet.getAddress();
                addressInet6RW.source(s -> s.set(ip));
                return this;
            }

            public ProxyAddressInet6Builder destination(
                String destination) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(destination);
                final byte[] ip = inet.getAddress();
                addressInet6RW.destination(d -> d.set(ip));
                return this;
            }

            public ProxyAddressInet6Builder sourcePort(
                int sourcePort)
            {
                addressInet6RW.sourcePort(sourcePort);
                return this;
            }

            public ProxyAddressInet6Builder destinationPort(
                int destinationPort)
            {
                addressInet6RW.destinationPort(destinationPort);
                return this;
            }

            public ProxyBeginExBuilder build()
            {
                beginExRW.address(addressRW.inet6(addressInet6RW.build()).build());
                return ProxyBeginExBuilder.this;
            }
        }

        public final class ProxyAddressUnixBuilder
        {
            private final ProxyAddressFW.Builder addressRW = new ProxyAddressFW.Builder();

            private final ProxyAddressUnixFW.Builder addressUnixRW = new ProxyAddressUnixFW.Builder();

            private ProxyAddressUnixBuilder()
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[218]);
                addressRW.wrap(buffer, 0, buffer.capacity());
                addressUnixRW.wrap(buffer, 1, buffer.capacity());
            }

            public ProxyAddressUnixBuilder protocol(
                String protocol)
            {
                addressUnixRW.protocol(p -> p.set(ProxyAddressProtocol.valueOf(protocol.toUpperCase())));
                return this;
            }

            public ProxyAddressUnixBuilder source(
                String source) throws UnknownHostException
            {
                MutableDirectBuffer sourceBuf = new UnsafeBuffer(new byte[108]);
                sourceBuf.putStringWithoutLengthUtf8(0, source);
                addressUnixRW.source(sourceBuf, 0, sourceBuf.capacity());
                return this;
            }

            public ProxyAddressUnixBuilder destination(
                String destination) throws UnknownHostException
            {
                MutableDirectBuffer destinationBuf = new UnsafeBuffer(new byte[108]);
                destinationBuf.putStringWithoutLengthUtf8(0, destination);
                addressUnixRW.destination(destinationBuf, 0, destinationBuf.capacity());
                return this;
            }

            public ProxyBeginExBuilder build()
            {
                beginExRW.address(addressRW.unix(addressUnixRW.build()).build());
                return ProxyBeginExBuilder.this;
            }
        }

        public final class ProxyAddressNoneBuilder
        {
            private final ProxyAddressFW.Builder addressRW = new ProxyAddressFW.Builder();

            private final ProxyAddressNoneFW.Builder addressNoneRW = new ProxyAddressNoneFW.Builder();

            private ProxyAddressNoneBuilder()
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1]);
                addressRW.wrap(buffer, 0, buffer.capacity());
                addressNoneRW.wrap(buffer, 1, buffer.capacity());
            }

            public ProxyBeginExBuilder build()
            {
                beginExRW.address(addressRW.none(addressNoneRW.build()).build());
                return ProxyBeginExBuilder.this;
            }
        }

        public final class ProxyInfoBuilder
        {
            private final Array32FW.Builder<ProxyInfoFW.Builder, ProxyInfoFW> infosRW =
                    new Array32FW.Builder<>(new ProxyInfoFW.Builder(), new ProxyInfoFW());

            private ProxyInfoBuilder()
            {
                final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
                infosRW.wrap(buffer, 0, buffer.capacity());
            }

            public ProxyInfoBuilder alpn(
                String alpn)
            {
                infosRW.item(i -> i.alpn(alpn));
                return this;
            }

            public ProxyInfoBuilder authority(
                String authority)
            {
                infosRW.item(i -> i.authority(authority));
                return this;
            }

            public ProxyInfoBuilder identity(
                byte[] identity)
            {
                infosRW.item(i -> i.identity(id -> id.value(v -> v.set(identity))));
                return this;
            }

            public ProxyInfoBuilder namespace(
                String namespace)
            {
                infosRW.item(i -> i.namespace(namespace));
                return this;
            }

            public ProxySecureInfoBuilder secure()
            {
                return new ProxySecureInfoBuilder();
            }

            public ProxyBeginExBuilder build()
            {
                beginExRW.infos(infosRW.build());
                return ProxyBeginExBuilder.this;
            }

            public final class ProxySecureInfoBuilder
            {
                private ProxySecureInfoBuilder()
                {
                }

                public ProxySecureInfoBuilder protocol(
                    String protocol)
                {
                    infosRW.item(i -> i.secure(s -> s.protocol(protocol)));
                    return this;
                }

                public ProxySecureInfoBuilder cipher(
                    String cipher)
                {
                    infosRW.item(i -> i.secure(s -> s.cipher(cipher)));
                    return this;
                }

                public ProxySecureInfoBuilder signature(
                    String signature)
                {
                    infosRW.item(i -> i.secure(s -> s.signature(signature)));
                    return this;
                }

                public ProxySecureInfoBuilder name(
                    String name)
                {
                    infosRW.item(i -> i.secure(s -> s.name(name)));
                    return this;
                }

                public ProxySecureInfoBuilder key(
                    String key)
                {
                    infosRW.item(i -> i.secure(s -> s.key(key)));
                    return this;
                }

                public ProxyInfoBuilder build()
                {
                    return ProxyInfoBuilder.this;
                }
            }
        }
    }

    public static final class ProxyBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final ProxyBeginExFW beginExRO = new ProxyBeginExFW();

        private Integer typeId;
        private Predicate<ProxyAddressFW> address;
        private Predicate<Array32FW<ProxyInfoFW>> infos;

        public ProxyBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public ProxyAddressInetMatcherBuilder addressInet()
        {
            final ProxyAddressInetMatcherBuilder matcher = new ProxyAddressInetMatcherBuilder();

            this.address = matcher::match;
            return matcher;
        }

        public ProxyAddressInet4MatcherBuilder addressInet4()
        {
            final ProxyAddressInet4MatcherBuilder matcher = new ProxyAddressInet4MatcherBuilder();

            this.address = matcher::match;
            return matcher;
        }

        public ProxyAddressInet6MatcherBuilder addressInet6()
        {
            final ProxyAddressInet6MatcherBuilder matcher = new ProxyAddressInet6MatcherBuilder();

            this.address = matcher::match;
            return matcher;
        }

        public ProxyAddressUnixMatcherBuilder addressUnix()
        {
            final ProxyAddressUnixMatcherBuilder matcher = new ProxyAddressUnixMatcherBuilder();

            this.address = matcher::match;
            return matcher;
        }

        public ProxyAddressNoneMatcherBuilder addressNone()
        {
            final ProxyAddressNoneMatcherBuilder matcher = new ProxyAddressNoneMatcherBuilder();

            this.address = matcher::match;
            return matcher;
        }

        public ProxyInfoMatcherBuilder info()
        {
            final ProxyInfoMatcherBuilder matcher = new ProxyInfoMatcherBuilder();

            this.infos = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null ? this::match : buf -> null;
        }

        private ProxyBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final ProxyBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchAddress(beginEx) &&
                matchInfos(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
        }

        private boolean matchTypeId(
            ProxyBeginExFW beginEx)
        {
            return typeId == beginEx.typeId();
        }

        private boolean matchAddress(
            ProxyBeginExFW beginEx)
        {
            return address == null || address.test(beginEx.address());
        }

        private boolean matchInfos(
            ProxyBeginExFW beginEx)
        {
            return infos == null || infos.test(beginEx.infos());
        }

        public final class ProxyAddressInetMatcherBuilder
        {
            private ProxyAddressProtocol protocol;
            private String16FW source;
            private String16FW destination;
            private Integer sourcePort;
            private Integer destinationPort;

            private ProxyAddressInetMatcherBuilder()
            {
            }

            public ProxyAddressInetMatcherBuilder protocol(
                String protocol)
            {
                this.protocol = ProxyAddressProtocol.valueOf(protocol.toUpperCase());
                return this;
            }

            public ProxyAddressInetMatcherBuilder source(
                String source) throws UnknownHostException
            {
                this.source = new String16FW(source);
                return this;
            }

            public ProxyAddressInetMatcherBuilder destination(
                String destination) throws UnknownHostException
            {
                this.destination = new String16FW(destination);
                return this;
            }

            public ProxyAddressInetMatcherBuilder sourcePort(
                int sourcePort)
            {
                this.sourcePort = sourcePort;
                return this;
            }

            public ProxyAddressInetMatcherBuilder destinationPort(
                int destinationPort)
            {
                this.destinationPort = destinationPort;
                return this;
            }

            public ProxyBeginExMatcherBuilder build()
            {
                return ProxyBeginExMatcherBuilder.this;
            }

            private boolean match(
                ProxyAddressFW address)
            {
                return address.kind() == INET && match(address.inet());
            }

            private boolean match(
                ProxyAddressInetFW inet)
            {
                return matchProtocol(inet) &&
                    matchSource(inet) &&
                    matchDestination(inet) &&
                    matchSourcePort(inet) &&
                    matchDestinationPort(inet);
            }

            private boolean matchProtocol(
                final ProxyAddressInetFW inet)
            {
                return protocol == null || protocol == inet.protocol().get();
            }

            private boolean matchSource(
                final ProxyAddressInetFW inet)
            {
                return source == null || source.equals(inet.source());
            }

            private boolean matchDestination(
                final ProxyAddressInetFW inet)
            {
                return destination == null || destination.equals(inet.destination());
            }

            private boolean matchSourcePort(
                final ProxyAddressInetFW inet)
            {
                return sourcePort == null || sourcePort == inet.sourcePort();
            }

            private boolean matchDestinationPort(
                final ProxyAddressInetFW inet)
            {
                return destinationPort == null || destinationPort == inet.destinationPort();
            }
        }

        public final class ProxyAddressInet4MatcherBuilder
        {
            private ProxyAddressProtocol protocol;
            private OctetsFW source;
            private OctetsFW destination;
            private Integer sourcePort;
            private Integer destinationPort;

            private ProxyAddressInet4MatcherBuilder()
            {
            }

            public ProxyAddressInet4MatcherBuilder protocol(
                String protocol)
            {
                this.protocol = ProxyAddressProtocol.valueOf(protocol.toUpperCase());
                return this;
            }

            public ProxyAddressInet4MatcherBuilder source(
                String source) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(source);
                final byte[] ip = inet.getAddress();
                this.source = new OctetsFW().wrap(new UnsafeBuffer(ip), 0, ip.length);
                return this;
            }

            public ProxyAddressInet4MatcherBuilder destination(
                String destination) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(destination);
                final byte[] ip = inet.getAddress();
                this.destination = new OctetsFW().wrap(new UnsafeBuffer(ip), 0, ip.length);
                return this;
            }

            public ProxyAddressInet4MatcherBuilder sourcePort(
                int sourcePort)
            {
                this.sourcePort = sourcePort;
                return this;
            }

            public ProxyAddressInet4MatcherBuilder destinationPort(
                int destinationPort)
            {
                this.destinationPort = destinationPort;
                return this;
            }

            public ProxyBeginExMatcherBuilder build()
            {
                return ProxyBeginExMatcherBuilder.this;
            }

            private boolean match(
                ProxyAddressFW address)
            {
                return address.kind() == INET4 && match(address.inet4());
            }

            private boolean match(
                ProxyAddressInet4FW inet4)
            {
                return matchProtocol(inet4) &&
                    matchSource(inet4) &&
                    matchDestination(inet4) &&
                    matchSourcePort(inet4) &&
                    matchDestinationPort(inet4);
            }

            private boolean matchProtocol(
                final ProxyAddressInet4FW inet4)
            {
                return protocol == null || protocol == inet4.protocol().get();
            }

            private boolean matchSource(
                final ProxyAddressInet4FW inet4)
            {
                return source == null || source.equals(inet4.source());
            }

            private boolean matchDestination(
                final ProxyAddressInet4FW inet4)
            {
                return destination == null || destination.equals(inet4.destination());
            }

            private boolean matchSourcePort(
                final ProxyAddressInet4FW inet4)
            {
                return sourcePort == null || sourcePort == inet4.sourcePort();
            }

            private boolean matchDestinationPort(
                final ProxyAddressInet4FW inet4)
            {
                return destinationPort == null || destinationPort == inet4.destinationPort();
            }
        }

        public final class ProxyAddressInet6MatcherBuilder
        {
            private ProxyAddressProtocol protocol;
            private OctetsFW source;
            private OctetsFW destination;
            private Integer sourcePort;
            private Integer destinationPort;

            private ProxyAddressInet6MatcherBuilder()
            {
            }

            public ProxyAddressInet6MatcherBuilder protocol(
                String protocol)
            {
                this.protocol = ProxyAddressProtocol.valueOf(protocol.toUpperCase());
                return this;
            }

            public ProxyAddressInet6MatcherBuilder source(
                String source) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(source);
                final byte[] ip = inet.getAddress();
                this.source = new OctetsFW().wrap(new UnsafeBuffer(ip), 0, ip.length);
                return this;
            }

            public ProxyAddressInet6MatcherBuilder destination(
                String destination) throws UnknownHostException
            {
                final InetAddress inet = InetAddress.getByName(destination);
                final byte[] ip = inet.getAddress();
                this.destination = new OctetsFW().wrap(new UnsafeBuffer(ip), 0, ip.length);
                return this;
            }

            public ProxyAddressInet6MatcherBuilder sourcePort(
                int sourcePort)
            {
                this.sourcePort = sourcePort;
                return this;
            }

            public ProxyAddressInet6MatcherBuilder destinationPort(
                int destinationPort)
            {
                this.destinationPort = destinationPort;
                return this;
            }

            public ProxyBeginExMatcherBuilder build()
            {
                return ProxyBeginExMatcherBuilder.this;
            }

            private boolean match(
                ProxyAddressFW address)
            {
                return address.kind() == INET6 && match(address.inet6());
            }

            private boolean match(
                ProxyAddressInet6FW inet6)
            {
                return matchProtocol(inet6) &&
                    matchSource(inet6) &&
                    matchDestination(inet6) &&
                    matchSourcePort(inet6) &&
                    matchDestinationPort(inet6);
            }

            private boolean matchProtocol(
                final ProxyAddressInet6FW inet6)
            {
                return protocol == null || protocol == inet6.protocol().get();
            }

            private boolean matchSource(
                final ProxyAddressInet6FW inet6)
            {
                return source == null || source.equals(inet6.source());
            }

            private boolean matchDestination(
                final ProxyAddressInet6FW inet6)
            {
                return destination == null || destination.equals(inet6.destination());
            }

            private boolean matchSourcePort(
                final ProxyAddressInet6FW inet6)
            {
                return sourcePort == null || sourcePort == inet6.sourcePort();
            }

            private boolean matchDestinationPort(
                final ProxyAddressInet6FW inet6)
            {
                return destinationPort == null || destinationPort == inet6.destinationPort();
            }
        }

        public final class ProxyAddressUnixMatcherBuilder
        {
            private ProxyAddressProtocol protocol;
            private DirectBuffer source;
            private DirectBuffer destination;

            private ProxyAddressUnixMatcherBuilder()
            {
            }

            public ProxyAddressUnixMatcherBuilder protocol(
                String protocol)
            {
                this.protocol = ProxyAddressProtocol.valueOf(protocol.toUpperCase());
                return this;
            }

            public ProxyAddressUnixMatcherBuilder source(
                String source)
            {
                final MutableDirectBuffer sourceBuf = new UnsafeBuffer(new byte[108]);
                sourceBuf.putStringWithoutLengthUtf8(0, source);
                this.source = sourceBuf;
                return this;
            }

            public ProxyAddressUnixMatcherBuilder destination(
                String destination)
            {
                final MutableDirectBuffer destinationBuf = new UnsafeBuffer(new byte[108]);
                destinationBuf.putStringWithoutLengthUtf8(0, destination);
                this.destination = destinationBuf;
                return this;
            }

            public ProxyBeginExMatcherBuilder build()
            {
                return ProxyBeginExMatcherBuilder.this;
            }

            private boolean match(
                ProxyAddressFW address)
            {
                return address.kind() == UNIX && match(address.unix());
            }

            private boolean match(
                ProxyAddressUnixFW unix)
            {
                return matchProtocol(unix) &&
                    matchSource(unix) &&
                    matchDestination(unix);
            }

            private boolean matchProtocol(
                final ProxyAddressUnixFW unix)
            {
                return protocol == null || protocol == unix.protocol().get();
            }

            private boolean matchSource(
                final ProxyAddressUnixFW unix)
            {
                return source == null || source.equals(unix.source().value());
            }

            private boolean matchDestination(
                final ProxyAddressUnixFW unix)
            {
                return destination == null || destination.equals(unix.destination().value());
            }
        }

        public final class ProxyAddressNoneMatcherBuilder
        {
            private ProxyAddressNoneMatcherBuilder()
            {
            }

            public ProxyBeginExMatcherBuilder build()
            {
                return ProxyBeginExMatcherBuilder.this;
            }

            private boolean match(
                ProxyAddressFW address)
            {
                return address.kind() == NONE;
            }
        }

        public final class ProxyInfoMatcherBuilder
        {
            private final Map<ProxyInfoType, Predicate<ProxyInfoFW>> matchers;
            private ProxySecureInfoMatcherBuilder secure;

            private ProxyInfoMatcherBuilder()
            {
                matchers = new EnumMap<>(ProxyInfoType.class);
            }

            public ProxyInfoMatcherBuilder alpn(
                String alpn)
            {
                final String8FW alpn8 = new String8FW(alpn);
                matchers.put(ALPN, info -> alpn8.equals(info.alpn()));
                return this;
            }

            public ProxyInfoMatcherBuilder authority(
                String authority)
            {
                final String16FW authority16 = new String16FW(authority);
                matchers.put(AUTHORITY, info -> authority16.equals(info.authority()));
                return this;
            }

            public ProxyInfoMatcherBuilder identity(
                byte[] identity)
            {
                final DirectBuffer identityBuf = new UnsafeBuffer(identity);
                matchers.put(IDENTITY, info -> identityBuf.equals(info.identity().value().value()));
                return this;
            }

            public ProxyInfoMatcherBuilder namespace(
                String namespace)
            {
                final String16FW namespace16 = new String16FW(namespace);
                matchers.put(NAMESPACE, info -> namespace16.equals(info.namespace()));
                return this;
            }

            public ProxySecureInfoMatcherBuilder secure()
            {
                if (secure == null)
                {
                    secure = new ProxySecureInfoMatcherBuilder();
                }
                matchers.put(SECURE, info -> secure.match(info.secure()));
                return secure;
            }

            public ProxyBeginExMatcherBuilder build()
            {
                return ProxyBeginExMatcherBuilder.this;
            }

            private boolean match(
                Array32FW<ProxyInfoFW> infos)
            {
                MutableInteger match = new MutableInteger(0);
                infos.forEach(info -> match.value += match(info) ? 1 : 0);
                return match.value == (matchers.containsKey(SECURE)
                        ? matchers.size() + secure.matchers.size() - 1
                        : matchers.size());
            }

            private boolean match(
                ProxyInfoFW info)
            {
                final Predicate<ProxyInfoFW> matcher = matchers.get(info.kind());
                return matcher != null && matcher.test(info);
            }

            public final class ProxySecureInfoMatcherBuilder
            {
                private final Map<ProxySecureInfoType, Predicate<ProxySecureInfoFW>> matchers;

                private ProxySecureInfoMatcherBuilder()
                {
                    matchers = new EnumMap<>(ProxySecureInfoType.class);
                }

                public ProxySecureInfoMatcherBuilder protocol(
                    String protocol)
                {
                    final String8FW protocol8 = new String8FW(protocol);
                    matchers.put(PROTOCOL, info -> protocol8.equals(info.protocol()));
                    return this;
                }

                public ProxySecureInfoMatcherBuilder cipher(
                    String cipher)
                {
                    final String8FW cipher8 = new String8FW(cipher);
                    matchers.put(CIPHER, info -> cipher8.equals(info.cipher()));
                    return this;
                }

                public ProxySecureInfoMatcherBuilder signature(
                    String signature)
                {
                    final String8FW signature8 = new String8FW(signature);
                    matchers.put(SIGNATURE, info -> signature8.equals(info.signature()));
                    return this;
                }

                public ProxySecureInfoMatcherBuilder name(
                    String name)
                {
                    final String16FW name16 = new String16FW(name);
                    matchers.put(NAME, info -> name16.equals(info.name()));
                    return this;
                }

                public ProxySecureInfoMatcherBuilder key(
                    String key)
                {
                    final String8FW key8 = new String8FW(key);
                    matchers.put(KEY, info -> key8.equals(info.key()));
                    return this;
                }

                public ProxyInfoMatcherBuilder build()
                {
                    return ProxyInfoMatcherBuilder.this;
                }

                private boolean match(
                    ProxySecureInfoFW secureInfo)
                {
                    final Predicate<ProxySecureInfoFW> matcher = matchers.get(secureInfo.kind());
                    return matcher != null && matcher.test(secureInfo);
                }
            }
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(ProxyFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "proxy";
        }
    }

    private ProxyFunctions()
    {
        // utility
    }
}
