/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.aklivity.zilla.runtime.binding.a2a.internal.push;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class A2aPushPolicy
{
    private static final Pattern PATTERN_ALLOW = Pattern.compile(
        "(?<scheme>[a-zA-Z][a-zA-Z0-9+.-]*)://(?<host>[^/:]*)(?::(?<port>[0-9]+))?(?<path>/.*)?");
    private static final Pattern PATTERN_IPV4 = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
    private static final String LOCALHOST = "localhost";

    private final List<Allow> allow;

    public A2aPushPolicy(
        List<String> allow)
    {
        this.allow = allow != null ? allow.stream().map(Allow::new).toList() : List.of();
    }

    public boolean permits(
        String url)
    {
        return permits(url, null);
    }

    public boolean permits(
        String url,
        InetAddress[] resolved)
    {
        final URI target = target(url);

        boolean permitted = target != null;

        if (permitted)
        {
            final String host = hostOf(target);

            permitted = !ambiguous(host);
        }

        if (permitted)
        {
            final String host = hostOf(target);
            final boolean listed = allow.isEmpty() || allow.stream().anyMatch(a -> a.matches(target, host));
            final boolean explicit = allow.stream().anyMatch(a -> a.explicit() && a.matches(target, host));

            permitted = listed && (explicit || !restricted(host, resolved));
        }

        return permitted;
    }

    private static URI target(
        String url)
    {
        URI target = null;

        try
        {
            final URI candidate = URI.create(url);
            final String scheme = candidate.getScheme();

            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) &&
                candidate.getHost() != null &&
                candidate.getUserInfo() == null)
            {
                target = candidate;
            }
        }
        catch (IllegalArgumentException ex)
        {
            target = null;
        }

        return target;
    }

    private static String hostOf(
        URI target)
    {
        final String host = target.getHost().toLowerCase();
        return host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
    }

    private static boolean ambiguous(
        String host)
    {
        final String tail = host.substring(host.lastIndexOf('.') + 1);

        return host.indexOf(':') < 0 && !tail.isEmpty() && Character.isDigit(tail.charAt(0)) &&
            !PATTERN_IPV4.matcher(host).matches();
    }

    private static boolean restricted(
        String host,
        InetAddress[] resolved)
    {
        boolean restricted;

        if (resolved != null)
        {
            restricted = resolved.length == 0;
            for (InetAddress address : resolved)
            {
                restricted |= restricted(address);
            }
        }
        else if (LOCALHOST.equals(host) || host.endsWith("." + LOCALHOST))
        {
            restricted = true;
        }
        else
        {
            final InetAddress address = literal(host);
            restricted = address != null && restricted(address);
        }

        return restricted;
    }

    private static InetAddress literal(
        String host)
    {
        InetAddress address = null;

        if (host.indexOf(':') >= 0 || PATTERN_IPV4.matcher(host).matches())
        {
            try
            {
                address = InetAddress.getByName(host);
            }
            catch (UnknownHostException ex)
            {
                address = null;
            }
        }

        return address;
    }

    private static boolean restricted(
        InetAddress address)
    {
        final byte[] octets = address.getAddress();

        return address.isLoopbackAddress() ||
            address.isAnyLocalAddress() ||
            address.isLinkLocalAddress() ||
            address.isSiteLocalAddress() ||
            address.isMulticastAddress() ||
            octets.length == 4 && ((octets[0] & 0xff) == 100 && (octets[1] & 0xc0) == 64 || octets[0] == 0) ||
            octets.length == 16 && (octets[0] & 0xfe) == 0xfc;
    }

    private static final class Allow
    {
        private final String scheme;
        private final Pattern host;
        private final boolean explicit;
        private final int port;
        private final Pattern path;

        private Allow(
            String pattern)
        {
            final Matcher matcher = PATTERN_ALLOW.matcher(pattern);
            if (!matcher.matches())
            {
                throw new IllegalArgumentException("invalid push allow pattern: " + pattern);
            }

            this.scheme = matcher.group("scheme").toLowerCase();
            this.explicit = matcher.group("host").indexOf('*') < 0;
            this.host = glob(matcher.group("host").toLowerCase());
            this.port = matcher.group("port") != null ? Integer.parseInt(matcher.group("port")) : -1;
            this.path = glob(matcher.group("path") != null ? matcher.group("path") : "/*");
        }

        private boolean explicit()
        {
            return explicit;
        }

        private boolean matches(
            URI target,
            String targetHost)
        {
            final String rawPath = target.getRawPath();

            return scheme.equalsIgnoreCase(target.getScheme()) &&
                host.matcher(targetHost).matches() &&
                (port == -1 || port == target.getPort()) &&
                path.matcher(rawPath.isEmpty() ? "/" : rawPath).matches();
        }

        private static Pattern glob(
            String text)
        {
            final StringBuilder regex = new StringBuilder();
            for (String part : text.split("\\*", -1))
            {
                if (regex.length() > 0)
                {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(part));
            }
            return Pattern.compile(regex.toString());
        }
    }
}
