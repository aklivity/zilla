/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.config;

import static io.aklivity.zilla.runtime.binding.tcp.internal.types.ProxyInfoType.AUTHORITY;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.internal.TcpEventContext;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.ProxyAddressFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.ProxyAddressInet4FW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.ProxyAddressInet6FW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.ProxyAddressInetFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.binding.tcp.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;

public final class TcpBindingConfig
{
    public static final List<TcpRouteConfig> DEFAULT_CLIENT_ROUTES = initDefaultClientRoutes();

    private final byte[] ipv4RO = new byte[4];
    private final byte[] ipv6ros = new byte[16];

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final TcpOptionsConfig options;
    public final List<TcpRouteConfig> routes;

    private final Function<String, InetAddress[]> resolveHost;
    private final TcpEventContext event;

    private PollerKey[] attached;

    public TcpBindingConfig(
        BindingConfig binding,
        TcpEventContext event)
    {
        this(binding, null, event);
    }

    public TcpBindingConfig(
        BindingConfig binding,
        Function<String, InetAddress[]> resolveHost,
        TcpEventContext event)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = TcpOptionsConfig.class.cast(binding.options);
        this.routes = binding.kind == KindConfig.CLIENT && binding.routes.isEmpty()
                ? DEFAULT_CLIENT_ROUTES
                : binding.routes.stream().map(TcpRouteConfig::new).collect(toList());

        this.resolveHost = resolveHost;
        this.event = event;
    }

    public PollerKey[] attach(
        PollerKey[] attachment)
    {
        PollerKey[] detached = attached;
        attached = attachment;
        return detached;
    }

    public TcpRouteConfig resolve(
        InetSocketAddress address)
    {
        return routes.stream()
            .filter(r -> r.matches(address))
            .findFirst()
            .orElse(null);
    }

    public InetSocketAddress resolve(
        long traceId,
        long authorization,
        ProxyBeginExFW beginEx)
    {
        final int port = options != null && options.ports != null && options.ports.length > 0 ? options.ports[0] : 0;

        InetSocketAddress resolved = null;

        resolve:
        try
        {
            if (beginEx == null)
            {
                InetAddress[] addresses = options != null ? resolveHost(options.host) : null;
                resolved = addresses != null ? new InetSocketAddress(addresses[0], port) : null;
            }
            else if (routes == TcpBindingConfig.DEFAULT_CLIENT_ROUTES)
            {
                ProxyAddressFW address = beginEx.address();
                resolved = resolveInetSocketAddress(address);
            }
            else
            {
                final ProxyAddressFW address = beginEx.address();

                for (TcpRouteConfig route : routes)
                {
                    if (!route.authorized(authorization))
                    {
                        continue;
                    }

                    Array32FW<ProxyInfoFW> infos = beginEx.infos();
                    ProxyInfoFW authorityInfo = infos.matchFirst(i -> i.kind() == AUTHORITY);
                    if (authorityInfo != null && route.matchesExplicit(r -> r.authority != null))
                    {
                        final List<InetSocketAddress> authorities = Arrays
                            .stream(resolveHost(authorityInfo.authority().asString()))
                            .map(a -> new InetSocketAddress(a, port))
                            .toList();

                        for (InetSocketAddress authority : authorities)
                        {
                            if (route.matchesExplicit(authority))
                            {
                                resolved = authority;
                                break;
                            }
                        }
                    }

                    if (resolved == null)
                    {
                        resolved = resolve(address, authorization, route::matchesExplicit);
                    }

                    if (resolved != null)
                    {
                        break;
                    }
                }

                if (resolved == null &&
                    options != null &&
                    options.host != null &&
                    !"*".equals(options.host))
                {
                    final List<InetSocketAddress> host = Arrays
                        .stream(resolveHost(options.host))
                        .map(a -> new InetSocketAddress(a, port))
                        .toList();

                    for (TcpRouteConfig route : routes)
                    {
                        if (!route.authorized(authorization))
                        {
                            continue;
                        }

                        resolved = resolve(address, authorization, host::contains);

                        if (resolved != null)
                        {
                            break;
                        }
                    }
                }
            }
        }
        catch (TcpDnsFailedException ex)
        {
            event.dnsFailed(traceId, id, ex.hostname);
        }

        return resolved;
    }

    private InetAddress[] resolveHost(
        String hostname)
    {
        try
        {
            return resolveHost.apply(hostname);
        }
        catch (Throwable ex)
        {
            throw new TcpDnsFailedException(ex, hostname);
        }
    }

    private InetSocketAddress resolve(
        ProxyAddressFW address,
        long authorization,
        Predicate<? super InetSocketAddress> filter)
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
        Predicate<? super InetSocketAddress> filter)
    {
        return Arrays
                .stream(resolveHost.apply(address.destination().asString()))
                .map(a -> new InetSocketAddress(a, address.destinationPort()))
                .filter(filter)
                .findFirst()
                .orElse(null);
    }

    private InetSocketAddress resolveInet4(
        ProxyAddressInet4FW address,
        Predicate<? super InetSocketAddress> filter) throws UnknownHostException
    {
        OctetsFW destination = address.destination();
        int destinationPort = address.destinationPort();

        byte[] ipv4 = ipv4RO;
        destination.buffer().getBytes(destination.offset(), ipv4);

        return Optional
                .of(new InetSocketAddress(InetAddress.getByAddress(ipv4), destinationPort))
                .filter(filter)
                .orElse(null);
    }

    private InetSocketAddress resolveInet6(
        ProxyAddressInet6FW address,
        Predicate<? super InetSocketAddress> filter) throws UnknownHostException
    {
        OctetsFW destination = address.destination();
        int destinationPort = address.destinationPort();

        byte[] ipv6 = ipv6ros;
        destination.buffer().getBytes(destination.offset(), ipv6);

        return Optional
                .of(new InetSocketAddress(InetAddress.getByAddress(ipv6), destinationPort))
                .filter(filter)
                .orElse(null);
    }

    private InetSocketAddress resolveInetSocketAddress(
        ProxyAddressFW address)
    {
        InetSocketAddress resolved = null;

        try
        {
            switch (address.kind())
            {
            case INET:
                ProxyAddressInetFW addressInet = address.inet();
                resolved = new InetSocketAddress(addressInet.destination().asString(), addressInet.destinationPort());
                break;
            case INET4:
                ProxyAddressInet4FW addressInet4 = address.inet4();
                OctetsFW destinationInet4 = addressInet4.destination();
                int destinationPortInet4 = addressInet4.destinationPort();

                byte[] ipv4 = ipv4RO;
                destinationInet4.buffer().getBytes(destinationInet4.offset(), ipv4);
                resolved = new InetSocketAddress(InetAddress.getByAddress(ipv4), destinationPortInet4);
                break;
            case INET6:
                ProxyAddressInet6FW addressInet6 = address.inet6();

                OctetsFW destinationInet6 = addressInet6.destination();
                int destinationPortInet6 = addressInet6.destinationPort();

                byte[] ipv6 = ipv6ros;
                destinationInet6.buffer().getBytes(destinationInet6.offset(), ipv6);
                resolved = new InetSocketAddress(InetAddress.getByAddress(ipv6), destinationPortInet6);
                break;
            default:
                break;
            }
        }
        catch (UnknownHostException e)
        {
            //Ignore
        }

        return resolved;
    }

    private static List<TcpRouteConfig> initDefaultClientRoutes()
    {
        final RouteConfig route = RouteConfig.builder().build();
        route.authorized = (id, resolve) -> true;

        return singletonList(new TcpRouteConfig(route));
    }

    private static final class TcpDnsFailedException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private final String hostname;

        TcpDnsFailedException(
            Throwable cause,
            String hostname)
        {
            super(cause);
            this.hostname = hostname;
        }
    }
}
