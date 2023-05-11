/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressFamily.INET;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressFamily.INET4;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressFamily.INET6;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressFamily.UNIX;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyInfoType.ALPN;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyInfoType.AUTHORITY;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyInfoType.IDENTITY;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyInfoType.NAMESPACE;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyInfoType.SECURE;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxySecureInfoType.CIPHER;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxySecureInfoType.KEY;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxySecureInfoType.NAME;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxySecureInfoType.SIGNATURE;
import static io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxySecureInfoType.VERSION;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.proxy.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressFamily;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyAddressProtocol;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.ProxyInfoType;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.proxy.internal.types.stream.ProxyBeginExFW;

public final class ProxyConditionMatcher
{
    private final Predicate<ProxyAddressFW> matchAddress;
    private final Predicate<Array32FW<ProxyInfoFW>> matchInfos;

    public ProxyConditionMatcher(
        ProxyConditionConfig condition)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (condition.family != null)
        {
            Predicate<ProxyAddressFW> matchFamily = matchFamily(condition.family);
            matchAddress = matchAddress != null ? matchAddress.and(matchFamily) : matchFamily;
        }

        if (condition.source != null)
        {
            Predicate<ProxyAddressFW> matchSource = matchSource(condition.source);
            matchAddress = matchAddress != null ? matchAddress.and(matchSource) : matchSource;
        }

        if (condition.destination != null)
        {
            Predicate<ProxyAddressFW> matchDestination = matchDestination(condition.destination);
            matchAddress = matchAddress != null ? matchAddress.and(matchDestination) : matchDestination;
        }

        if (condition.transport != null)
        {
            Predicate<ProxyAddressFW> matchTransport = matchTransport(condition.transport);
            matchAddress = matchAddress != null ? matchAddress.and(matchTransport) : matchTransport;
        }

        this.matchAddress = matchAddress != null ? matchAddress : a -> true;
        this.matchInfos = condition.info != null ? matchInfos(condition.info) : i -> true;
    }

    public boolean matches(
        ProxyBeginExFW beginEx)
    {
        return matchAddress.test(beginEx.address()) && matchInfos.test(beginEx.infos());
    }

    private static Predicate<ProxyAddressFW> matchFamily(
        String family)
    {
        ProxyAddressFamily expected = ProxyAddressFamily.valueOf(family.toUpperCase());
        return a -> a.kind() == expected;
    }

    private static Predicate<ProxyAddressFW> matchSource(
        ProxyAddressConfig address)
    {
        Map<ProxyAddressFamily, Predicate<ProxyAddressFW>> matchers = new EnumMap<>(ProxyAddressFamily.class);
        matchers.put(INET, matchInetSource(address));
        matchers.put(INET4, matchInet4Source(address));
        matchers.put(INET6, matchInet6Source(address));
        matchers.put(UNIX, matchUnixSource(address));
        Predicate<ProxyAddressFW> reject = a -> false;
        return a -> matchers.getOrDefault(a.kind(), reject).test(a);
    }

    private static Predicate<ProxyAddressFW> matchInetSource(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final String regex = address.host.replaceAll("\\*", ".*").replaceAll("\\.", "\\.");
            final Matcher matcher = Pattern.compile(regex).matcher("");
            Predicate<ProxyAddressFW> matchHost = a -> matcher.reset(a.inet().source().asString()).matches();
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        if (address.port != null)
        {
            Predicate<ProxyAddressFW> matchPort = a -> a.inet().sourcePort() == address.port;
            matchAddress = matchAddress != null ? matchAddress.and(matchPort) : matchPort;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static Predicate<ProxyAddressFW> matchInet4Source(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final String[] cidr = address.host.split("/");
            final byte[] prefix = resolveHost(cidr[0]).getAddress();
            final int length = cidr.length == 2 ? parseInt(cidr[1]) : 32;
            Predicate<ProxyAddressFW> matchHost = a -> matchesAddressPrefix(a.inet4().source(), prefix, length);
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        if (address.port != null)
        {
            final int port = address.port;
            Predicate<ProxyAddressFW> matchPort = a -> a.inet4().sourcePort() == port;
            matchAddress = matchAddress != null ? matchAddress.and(matchPort) : matchPort;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static Predicate<ProxyAddressFW> matchInet6Source(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final String[] cidr = address.host.split("/");
            final byte[] prefix = resolveHost(cidr[0]).getAddress();
            final int length = cidr.length == 2 ? parseInt(cidr[1]) : 32;
            Predicate<ProxyAddressFW> matchHost = a -> matchesAddressPrefix(a.inet6().source(), prefix, length);
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        if (address.port != null)
        {
            Predicate<ProxyAddressFW> matchPort = a -> a.inet6().sourcePort() == address.port;
            matchAddress = matchAddress != null ? matchAddress.and(matchPort) : matchPort;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static Predicate<ProxyAddressFW> matchUnixSource(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final byte[] prefix = address.host.getBytes(UTF_8);
            final int length = prefix.length;
            Predicate<ProxyAddressFW> matchHost = a -> matchesAddressPrefix(a.unix().source(), prefix, length);
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static Predicate<ProxyAddressFW> matchDestination(
        ProxyAddressConfig address)
    {
        Map<ProxyAddressFamily, Predicate<ProxyAddressFW>> matchers = new EnumMap<>(ProxyAddressFamily.class);
        matchers.put(INET, matchInetDestination(address));
        matchers.put(INET4, matchInet4Destination(address));
        matchers.put(INET6, matchInet6Destination(address));
        matchers.put(UNIX, matchUnixDestination(address));
        Predicate<ProxyAddressFW> reject = a -> false;
        return a -> matchers.getOrDefault(a.kind(), reject).test(a);
    }

    private static Predicate<ProxyAddressFW> matchInetDestination(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final String regex = address.host.replaceAll("\\*", ".*").replaceAll("\\.", "\\.");
            final Matcher matcher = Pattern.compile(regex).matcher("");
            Predicate<ProxyAddressFW> matchHost = a -> matcher.reset(a.inet().destination().asString()).matches();
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        if (address.port != null)
        {
            Predicate<ProxyAddressFW> matchPort = a -> a.inet().destinationPort() == address.port;
            matchAddress = matchAddress != null ? matchAddress.and(matchPort) : matchPort;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static Predicate<ProxyAddressFW> matchInet4Destination(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final String[] cidr = address.host.split("/");
            final byte[] prefix = resolveHost(cidr[0]).getAddress();
            final int length = cidr.length == 2 ? parseInt(cidr[1]) : 32;
            Predicate<ProxyAddressFW> matchHost = a -> matchesAddressPrefix(a.inet4().destination(), prefix, length);
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        if (address.port != null)
        {
            Predicate<ProxyAddressFW> matchPort = a -> a.inet4().destinationPort() == address.port;
            matchAddress = matchAddress != null ? matchAddress.and(matchPort) : matchPort;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static Predicate<ProxyAddressFW> matchInet6Destination(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final String[] cidr = address.host.split("/");
            final byte[] prefix = resolveHost(cidr[0]).getAddress();
            final int length = cidr.length == 2 ? parseInt(cidr[1]) : 32;
            Predicate<ProxyAddressFW> matchHost = a -> matchesAddressPrefix(a.inet6().destination(), prefix, length);
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        if (address.port != null)
        {
            Predicate<ProxyAddressFW> matchPort = a -> a.inet6().destinationPort() == address.port;
            matchAddress = matchAddress != null ? matchAddress.and(matchPort) : matchPort;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static Predicate<ProxyAddressFW> matchUnixDestination(
        ProxyAddressConfig address)
    {
        Predicate<ProxyAddressFW> matchAddress = null;

        if (address.host != null)
        {
            final byte[] prefix = address.host.getBytes(UTF_8);
            final int length = prefix.length;
            Predicate<ProxyAddressFW> matchHost = a -> matchesAddressPrefix(a.unix().destination(), prefix, length);
            matchAddress = matchAddress != null ? matchAddress.and(matchHost) : matchHost;
        }

        return matchAddress != null ? matchAddress : a -> true;
    }

    private static boolean matchesAddressPrefix(
        OctetsFW address,
        byte[] prefix,
        int length)
    {
        boolean match = true;

        for (int i = 0; length > 0; i++, length -= Byte.SIZE)
        {
            byte addressByte = address.buffer().getByte(address.offset() + i);
            byte prefixByte = prefix[i];
            int compareBits = Math.min(length, Byte.SIZE);
            int compareMask = ((1 << compareBits) - 1) << (Byte.SIZE - compareBits);

            match &= (addressByte & compareMask) == (prefixByte & compareMask);
        }

        return match;
    }

    private static Predicate<ProxyAddressFW> matchTransport(
        String transport)
    {
        ProxyAddressProtocol protocol = ProxyAddressProtocol.valueOf(transport.toUpperCase());
        Map<ProxyAddressFamily, Predicate<ProxyAddressFW>> matchers = new EnumMap<>(ProxyAddressFamily.class);
        matchers.put(INET, matchInetTransport(protocol));
        matchers.put(INET4, matchInet4Transport(protocol));
        matchers.put(INET6, matchInet6Transport(protocol));
        matchers.put(UNIX, matchUnixTransport(protocol));
        Predicate<ProxyAddressFW> reject = a -> false;
        return a -> matchers.getOrDefault(a.kind(), reject).test(a);
    }

    private static Predicate<ProxyAddressFW> matchInetTransport(
        ProxyAddressProtocol protocol)
    {
        return a -> a.inet().protocol().get() == protocol;
    }

    private static Predicate<ProxyAddressFW> matchInet4Transport(
        ProxyAddressProtocol protocol)
    {
        return a -> a.inet4().protocol().get() == protocol;
    }

    private static Predicate<ProxyAddressFW> matchInet6Transport(
        ProxyAddressProtocol protocol)
    {
        return a -> a.inet6().protocol().get() == protocol;
    }

    private static Predicate<ProxyAddressFW> matchUnixTransport(
        ProxyAddressProtocol protocol)
    {
        return a -> a.unix().protocol().get() == protocol;
    }

    private static Predicate<Array32FW<ProxyInfoFW>> matchInfos(
        ProxyInfoConfig info)
    {
        Int2ObjectHashMap<Predicate<ProxyInfoFW>> matchers = new Int2ObjectHashMap<>();

        if (info.alpn != null)
        {
            String8FW alpn = new String8FW(info.alpn);
            matchers.put(ALPN.value(), i -> alpn.equals(i.alpn()));
        }

        if (info.authority != null)
        {
            String16FW authority = new String16FW(info.authority);
            matchers.put(AUTHORITY.value(), i -> authority.equals(i.authority()));
        }

        if (info.identity != null)
        {
            DirectBuffer buffer = new UnsafeBuffer(info.identity);
            OctetsFW identity = new OctetsFW().wrap(buffer, 0, buffer.capacity());
            matchers.put(IDENTITY.value(), i -> identity.equals(i.identity().value()));
        }

        if (info.namespace != null)
        {
            String16FW namespace = new String16FW(info.namespace);
            matchers.put(NAMESPACE.value(), i -> namespace.equals(i.namespace()));
        }

        if (info.secure != null)
        {
            if (info.secure.version != null)
            {
                String8FW version = new String8FW(info.secure.version);
                matchers.put(VERSION.value(), i -> version.equals(i.secure().version()));
            }

            if (info.secure.cipher != null)
            {
                String8FW cipher = new String8FW(info.secure.cipher);
                matchers.put(CIPHER.value(), i -> cipher.equals(i.secure().cipher()));
            }

            if (info.secure.key != null)
            {
                String8FW key = new String8FW(info.secure.key);
                matchers.put(KEY.value(), i -> key.equals(i.secure().key()));
            }

            if (info.secure.name != null)
            {
                String16FW name = new String16FW(info.secure.name);
                matchers.put(NAME.value(), i -> name.equals(i.secure().name()));
            }

            if (info.secure.signature != null)
            {
                String8FW signature = new String8FW(info.secure.signature);
                matchers.put(SIGNATURE.value(), i -> signature.equals(i.secure().signature()));
            }
        }

        MutableInteger matched = new MutableInteger();
        Predicate<ProxyInfoFW> matchItem = i -> matchers.getOrDefault(matcherKey(i), x -> false).test(i);
        return is ->
        {
            matched.value = 0;
            is.forEach(i -> matched.value += matchItem.test(i) ? 1 : 0);
            return matched.value == matchers.size();
        };
    }

    private static int matcherKey(
        ProxyInfoFW info)
    {
        ProxyInfoType kind = info.kind();
        return kind == SECURE ? info.secure().kind().value() : kind.value();
    }

    private static InetAddress resolveHost(
        String host)
    {
        InetAddress address = null;

        try
        {
            address = InetAddress.getByName(host);
        }
        catch (UnknownHostException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return address;
    }
}
