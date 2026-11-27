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
package io.aklivity.zilla.runtime.binding.a2a.internal.card;

import java.util.List;

public final class A2aAgentCard
{
    public final boolean legacy;
    public final List<A2aAgentInterface> interfaces;
    public final List<String> skills;

    A2aAgentCard(
        boolean legacy,
        List<A2aAgentInterface> interfaces,
        List<String> skills)
    {
        this.legacy = legacy;
        this.interfaces = interfaces;
        this.skills = skills;
    }

    public boolean offersJsonRpc()
    {
        return interfaces.stream().anyMatch(A2aAgentInterface::jsonRpc);
    }

    public A2aAgentInterface select(
        List<String> versions)
    {
        A2aAgentInterface selected = null;

        for (int i = 0; selected == null && i < versions.size(); i++)
        {
            final String version = versions.get(i);
            selected = interfaces.stream()
                .filter(A2aAgentInterface::jsonRpc)
                .filter(iface -> version.equals(versionOf(iface.version)))
                .findFirst()
                .orElse(null);
        }

        return selected;
    }

    public static String versionOf(
        String version)
    {
        final String[] parts = version.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0] + ".0";
    }
}
