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
package io.aklivity.zilla.runtime.cog.tls.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TlsConditionMatcher
{
    public final Matcher authorityMatch;
    public final Matcher alpnMatch;

    public TlsConditionMatcher(
        TlsConditionConfig condition)
    {
        this.authorityMatch = condition.authority != null ? asMatcher(condition.authority) : null;
        this.alpnMatch = condition.alpn != null ? asMatcher(condition.alpn) : null;
    }

    public boolean matches(
        String authority,
        String alpn)
    {
        return matchesAuthority(authority) &&
                matchesAlpn(alpn);
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

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*")).matcher("");
    }
}
