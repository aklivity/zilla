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
package io.aklivity.zilla.runtime.binding.ws.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WsConditionMatcher
{
    private final Matcher protocol;
    private final Matcher scheme;
    private final Matcher authority;
    private final Matcher path;

    public WsConditionMatcher(
        WsConditionConfig condition)
    {
        this.protocol = condition.protocol != null ? asMatcher(condition.protocol) : null;
        this.scheme = condition.scheme != null ? asMatcher(condition.scheme) : null;
        this.authority = condition.authority != null ? asMatcher(condition.authority) : null;
        this.path = condition.path != null ? asMatcher(condition.path) : null;
    }

    public boolean matches(
        String protocol,
        String scheme,
        String authority,
        String path)
    {
        return matchProtocol(protocol) &&
                matchScheme(scheme) &&
                matchAuthority(authority) &&
                matchPath(path);
    }

    private boolean matchProtocol(
        String protocol)
    {
        return this.protocol == null || protocol != null && this.protocol.reset(protocol).matches();
    }

    private boolean matchScheme(
        String scheme)
    {
        return this.scheme == null || scheme != null && this.scheme.reset(scheme).matches();
    }

    private boolean matchAuthority(
        String authority)
    {
        return this.authority == null || authority != null && this.authority.reset(authority).matches();
    }

    private boolean matchPath(
        String path)
    {
        return this.path == null || path != null && this.path.reset(path).matches();
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*")).matcher("");
    }
}
