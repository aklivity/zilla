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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import java.util.List;

public final class A2aDialects
{
    private final A2aDialect v10 = new A2aV10Dialect();
    private final A2aDialect v03 = new A2aV03Dialect();
    private final boolean v10Allowed;
    private final boolean v03Allowed;

    public A2aDialects(
        List<String> versions)
    {
        final boolean any = versions == null || versions.isEmpty();
        this.v10Allowed = any || versions.contains(A2aV10Dialect.VERSION);
        this.v03Allowed = any || versions.contains(A2aV03Dialect.VERSION);
    }

    public A2aDialect legacy()
    {
        return v03Allowed ? v03 : null;
    }

    /**
     * Resolves the dialect for a request, or {@code null} when the requested version is not supported.
     * An explicit version selects its dialect; an absent version is 0.3 unless the method is only named in 1.0.
     */
    public A2aDialect resolve(
        String version,
        String method)
    {
        A2aDialect dialect;

        if (version != null && !version.isBlank())
        {
            dialect = select(version.trim());
        }
        else if (method != null && v03.operation(method) == null && v10.operation(method) != null)
        {
            dialect = v10Allowed ? v10 : null;
        }
        else if (method != null && v03.operation(method) == null && v10.operation(method) == null)
        {
            dialect = v03Allowed ? v03 : v10Allowed ? v10 : null;
        }
        else
        {
            dialect = v03Allowed ? v03 : null;
        }

        return dialect;
    }

    /**
     * Resolves the dialect for an agent card request: an explicit version selects its dialect,
     * otherwise the card is served in the newest supported version.
     */
    public A2aDialect resolveCard(
        String version)
    {
        final A2aDialect dialect;

        if (version != null && !version.isBlank())
        {
            dialect = select(version.trim());
        }
        else
        {
            dialect = v10Allowed ? v10 : v03Allowed ? v03 : null;
        }

        return dialect;
    }

    private A2aDialect select(
        String version)
    {
        A2aDialect dialect = null;

        if (matches(version, A2aV10Dialect.VERSION))
        {
            dialect = v10Allowed ? v10 : null;
        }
        else if (matches(version, A2aV03Dialect.VERSION))
        {
            dialect = v03Allowed ? v03 : null;
        }

        return dialect;
    }

    private static boolean matches(
        String requested,
        String supported)
    {
        return requested.equals(supported) || requested.startsWith(supported + ".");
    }
}
