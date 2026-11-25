/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine;

import java.util.ArrayList;
import java.util.List;

final class EngineAffinity
{
    final String namespace;
    final String binding;
    final long mask;

    EngineAffinity(
        String namespace,
        String binding,
        long mask)
    {
        this.namespace = namespace;
        this.binding = binding;
        this.mask = mask;
    }

    static List<EngineAffinity> parse(
        String value,
        int workers)
    {
        final List<EngineAffinity> affinities = new ArrayList<>();

        if (value != null && !value.isBlank())
        {
            String namespace = null;
            String binding = null;
            long mask = 0L;

            for (String token : value.split(",", -1))
            {
                final int equalsAt = token.indexOf('=');
                String cores = token;

                if (equalsAt != -1)
                {
                    if (binding != null)
                    {
                        affinities.add(new EngineAffinity(namespace, binding, mask));
                    }

                    final String name = token.substring(0, equalsAt).trim();
                    final int dotAt = name.indexOf('.');
                    if (dotAt < 1 || dotAt == name.length() - 1)
                    {
                        throw new IllegalArgumentException(
                            String.format("affinity binding must be namespace.binding: '%s'", name));
                    }

                    namespace = name.substring(0, dotAt);
                    binding = name.substring(dotAt + 1);
                    mask = 0L;
                    cores = token.substring(equalsAt + 1);
                }
                else if (binding == null)
                {
                    throw new IllegalArgumentException(String.format("affinity cores without a binding: '%s'", token.trim()));
                }

                mask |= parseCores(cores.trim(), workers);
            }

            affinities.add(new EngineAffinity(namespace, binding, mask));
        }

        return affinities;
    }

    private static long parseCores(
        String cores,
        int workers)
    {
        final int dashAt = cores.indexOf('-');
        final int first = parseCore(dashAt != -1 ? cores.substring(0, dashAt) : cores, workers);
        final int last = dashAt != -1 ? parseCore(cores.substring(dashAt + 1), workers) : first;

        if (last < first)
        {
            throw new IllegalArgumentException(String.format("affinity core range is reversed: '%s'", cores));
        }

        long mask = 0L;
        for (int core = first; core <= last; core++)
        {
            mask |= 1L << core;
        }

        return mask;
    }

    private static int parseCore(
        String core,
        int workers)
    {
        final String trimmed = core.trim();
        int index;

        try
        {
            index = Integer.parseInt(trimmed);
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException(String.format("affinity core is not a number: '%s'", trimmed), ex);
        }

        if (index < 0 || index >= workers)
        {
            throw new IllegalArgumentException(String.format("affinity core %d is outside the %d workers", index, workers));
        }

        return index;
    }
}
