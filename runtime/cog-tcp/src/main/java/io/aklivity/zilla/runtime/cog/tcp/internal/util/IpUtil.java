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
package io.aklivity.zilla.runtime.cog.tcp.internal.util;

import static io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyAddressProtocol.STREAM;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.cog.tcp.internal.types.ProxyAddressFW;

public final class IpUtil
{
    public static final Pattern ACCEPT_HOST_AND_PORT_PATTERN = Pattern.compile("tcp#([^:]+):(\\d+)");
    public static final Pattern CONNECT_HOST_AND_PORT_PATTERN = Pattern.compile("([^:]+):(\\d+)");

    private IpUtil()
    {
        // no instances
    }

    public static int compareAddresses(
        SocketAddress address1,
        SocketAddress address2)
    {
        boolean same = false;
        if (address1.equals(address2))
        {
            same = true;
        }
        else
        {
            if (address1 instanceof InetSocketAddress && address2 instanceof InetSocketAddress)
            {
                InetSocketAddress inet1 = (InetSocketAddress) address1;
                InetSocketAddress inet2 = (InetSocketAddress) address2;
                same = inet1.getPort() == inet2.getPort() &&
                         ((inet1.getAddress() != null && inet1.getAddress().isAnyLocalAddress()) ||
                         (inet2.getAddress() != null && inet2.getAddress().isAnyLocalAddress()));
            }

        }
        return same ? 0 : 1;
    }

    public static void proxyAddress(
        ProxyAddressFW.Builder builder,
        InetSocketAddress source,
        InetSocketAddress destination)
    {
        InetAddress sourceAddress = source.getAddress();
        InetAddress destinationAddress = destination.getAddress();

        if (sourceAddress instanceof Inet4Address &&
            destinationAddress instanceof Inet4Address)
        {
            builder.inet4(inet4 -> inet4.protocol(p -> p.set(STREAM))
                                        .source(s -> s.set(sourceAddress.getAddress()))
                                        .destination(d -> d.set(destinationAddress.getAddress()))
                                        .sourcePort(source.getPort())
                                        .destinationPort(destination.getPort()));
        }
        else if (sourceAddress instanceof Inet6Address &&
            destinationAddress instanceof Inet6Address)
        {
            builder.inet6(inet6 -> inet6.protocol(p -> p.set(STREAM))
                                        .source(s -> s.set(sourceAddress.getAddress()))
                                        .destination(d -> d.set(destinationAddress.getAddress()))
                                        .sourcePort(source.getPort())
                                        .destinationPort(destination.getPort()));
        }
        else
        {
            throw new IllegalArgumentException("Unexpected address types: " + Arrays.asList(source, destination));
        }
    }
}
