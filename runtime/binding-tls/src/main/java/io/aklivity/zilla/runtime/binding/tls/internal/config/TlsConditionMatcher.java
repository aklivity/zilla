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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.collections.IntHashSet;

import io.aklivity.zilla.runtime.binding.tls.config.TlsConditionConfig;

public final class TlsConditionMatcher
{
    public final Matcher authorityMatch;
    public final Matcher alpnMatch;
    public final IntHashSet ports;

    public TlsConditionMatcher(
        TlsConditionConfig condition)
    {
        this.authorityMatch = condition.authority != null ? asMatcher(condition.authority) : null;
        this.alpnMatch = condition.alpn != null ? asMatcher(condition.alpn) : null;
        this.ports = condition.ports != null ? asIntHashSet(condition.ports) : null;
    }

    public boolean matches(
        String authority,
        String alpn,
        int port)
    {
        return matchesAuthority(authority) &&
                matchesAlpn(alpn) &&
                matchesPort(port);
    }

    public boolean matchesPortOnly(
        int port)
    {
        return matchesPort(port);
    }

    private boolean matchesAuthority(
        String sni)
    {
        return authorityMatch == null || sni != null && authorityMatch.reset(sni).matches();
    }

    private boolean matchesAlpn(
        String alpn)
    {
        return alpnMatch == null || alpn != null && alpnMatch.reset(alpn).matches();
    }

    private boolean matchesPort(
        int port)
    {
        return ports == null || ports.contains(port);
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*")).matcher("");
    }

    private static IntHashSet asIntHashSet(
        int[] ports)
    {
        IntHashSet set = new IntHashSet(ports.length);
        Arrays.stream(ports).forEach(set::add);
        return set;
    }
}
