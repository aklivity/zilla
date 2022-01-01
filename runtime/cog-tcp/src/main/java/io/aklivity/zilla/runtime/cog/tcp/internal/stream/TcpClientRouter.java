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
package io.aklivity.zilla.runtime.cog.tcp.internal.stream;

import static io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyInfoType.AUTHORITY;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.cog.tcp.internal.config.TcpBinding;
import io.aklivity.zilla.runtime.cog.tcp.internal.config.TcpOptions;
import io.aklivity.zilla.runtime.cog.tcp.internal.config.TcpRoute;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.Array32FW;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyAddressInet4FW;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyAddressInet6FW;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.cog.tcp.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;

public final class TcpClientRouter
{
    private final byte[] ipv4RO = new byte[4];
    private final byte[] ipv6ros = new byte[16];

    private final Function<String, InetAddress[]> resolveHost;
    private final Long2ObjectHashMap<TcpBinding> bindings;

    public TcpClientRouter(
        AxleContext context)
    {
        this.resolveHost = context::resolveHost;
        this.bindings = new Long2ObjectHashMap<>();
    }

    public void attach(
        TcpBinding binding)
    {
        bindings.put(binding.routeId, binding);
    }

    public TcpBinding lookup(
        long routeId)
    {
        return bindings.get(routeId);
    }

    public InetSocketAddress resolve(
        TcpBinding binding,
        long authorization,
        ProxyBeginExFW beginEx)
    {
        final TcpOptions options = binding.options;

        InetSocketAddress resolved = null;

        if (beginEx == null)
        {
            resolved = new InetSocketAddress(options.host, options.port);
        }
        else
        {
            final ProxyAddressFW address = beginEx.address();

            for (TcpRoute route : binding.routes)
            {
                Array32FW<ProxyInfoFW> infos = beginEx.infos();
                ProxyInfoFW authorityInfo = infos.matchFirst(i -> i.kind() == AUTHORITY);
                if (authorityInfo != null && route.when.stream().anyMatch(r -> r.authority != null))
                {
                    final List<InetAddress> authorities = Arrays.asList(resolveHost.apply(authorityInfo.authority().asString()));
                    for (InetAddress authority : authorities)
                    {
                        resolved = route.when.stream().anyMatch(m -> m.matches(authority))
                                ? new InetSocketAddress(authority, options.port)
                                : null;

                        if (resolved != null)
                        {
                            break;
                        }
                    }
                }

                if (resolved == null)
                {
                    Predicate<? super InetAddress> matches = a -> route.when.stream().anyMatch(m -> m.matches(a));

                    resolved = resolve(address, authorization, matches);
                }

                if (resolved != null)
                {
                    break;
                }
            }

            if (resolved == null && binding.exit != null)
            {
                final List<InetAddress> host = Arrays.asList(resolveHost.apply(options.host));

                resolved = resolve(address, authorization, host::contains);
            }
        }

        return resolved;
    }

    public void detach(
        long routeId)
    {
        bindings.remove(routeId);
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), bindings);
    }

    private InetSocketAddress resolve(
        ProxyAddressFW address,
        long authorization,
        Predicate<? super InetAddress> filter)
    {
        InetSocketAddress resolved = null;

        try
        {
            switch (address.kind())
            {
            case INET:
                ProxyAddressInetFW addressInet = address.inet();
                resolved = resolveInet(addressInet, filter);
                break;
            case INET4:
                ProxyAddressInet4FW addressInet4 = address.inet4();
                resolved = resolveInet4(addressInet4, filter);
                break;
            case INET6:
                ProxyAddressInet6FW addressInet6 = address.inet6();
                resolved = resolveInet6(addressInet6, filter);
                break;
            default:
                throw new RuntimeException("Unexpected address kind");
            }
        }
        catch (UnknownHostException ex)
        {
           // ignore
        }

        return resolved;
    }

    private InetSocketAddress resolveInet(
        ProxyAddressInetFW address,
        Predicate<? super InetAddress> filter)
    {
        return Arrays
                .stream(resolveHost.apply(address.destination().asString()))
                .filter(filter)
                .findFirst()
                .map(a -> new InetSocketAddress(a, address.destinationPort()))
                .orElse(null);
    }

    private InetSocketAddress resolveInet4(
        ProxyAddressInet4FW address,
        Predicate<? super InetAddress> filter) throws UnknownHostException
    {
        OctetsFW destination = address.destination();
        int destinationPort = address.destinationPort();

        byte[] ipv4 = ipv4RO;
        destination.buffer().getBytes(destination.offset(), ipv4);

        return Optional
                .of(InetAddress.getByAddress(ipv4))
                .filter(filter)
                .map(a -> new InetSocketAddress(a, destinationPort))
                .orElse(null);
    }

    private InetSocketAddress resolveInet6(
        ProxyAddressInet6FW address,
        Predicate<? super InetAddress> filter) throws UnknownHostException
    {
        OctetsFW destination = address.destination();
        int destinationPort = address.destinationPort();

        byte[] ipv6 = ipv6ros;
        destination.buffer().getBytes(destination.offset(), ipv6);

        return Optional
                .of(InetAddress.getByAddress(ipv6))
                .filter(filter)
                .map(a -> new InetSocketAddress(a, destinationPort))
                .orElse(null);
    }
}
