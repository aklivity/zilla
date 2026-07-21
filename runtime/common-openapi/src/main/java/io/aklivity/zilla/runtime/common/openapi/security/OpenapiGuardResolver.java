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
package io.aklivity.zilla.runtime.common.openapi.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.common.openapi.view.OpenapiSecurityRequirementView;

public final class OpenapiGuardResolver
{
    public static GuardedResolution resolve(
        String operationId,
        String specLabel,
        List<List<OpenapiSecurityRequirementView>> security,
        Map<String, String> securityByScheme,
        ToLongFunction<String> resolveId,
        LongFunction<String> supplyQName)
    {
        GuardedResolution result = GuardedResolution.allowed(List.of());

        if (security != null && !security.isEmpty())
        {
            if (security.size() > 1)
            {
                result = GuardedResolution.denied(
                    "operation \"%s\" declares %d alternative security requirements; "
                        .formatted(operationId, security.size()) +
                    "OR-alternative security is not supported because a route can reference only one guard");
            }
            else
            {
                final List<OpenapiSecurityRequirementView> alternative = security.get(0);
                if (!alternative.isEmpty())
                {
                    result = resolveAlternative(operationId, specLabel, alternative, securityByScheme, resolveId, supplyQName);
                }
            }
        }

        return result;
    }

    private static GuardedResolution resolveAlternative(
        String operationId,
        String specLabel,
        List<OpenapiSecurityRequirementView> alternative,
        Map<String, String> securityByScheme,
        ToLongFunction<String> resolveId,
        LongFunction<String> supplyQName)
    {
        final List<GuardedRef> refs = new ArrayList<>();

        for (OpenapiSecurityRequirementView requirement : alternative)
        {
            final String guard = securityByScheme != null ? securityByScheme.get(requirement.name) : null;
            if (guard == null)
            {
                continue;
            }

            final String qname = supplyQName.apply(resolveId.applyAsLong(guard));
            final List<String> roles = requirement.scopes != null ? requirement.scopes : List.of();
            refs.add(new GuardedRef(qname, roles));
        }

        GuardedResolution result;
        if (refs.isEmpty())
        {
            result = GuardedResolution.allowed(List.of());
        }
        else
        {
            final List<String> qnames = refs.stream().map(r -> r.qname).distinct().toList();
            if (qnames.size() > 1)
            {
                result = GuardedResolution.denied(
                    "operation \"%s\" in spec \"%s\" requires multiple distinct guards (%s) simultaneously, "
                        .formatted(operationId, specLabel, String.join(", ", qnames)) +
                    "which is not supported because Zilla guards cannot be combined with AND semantics");
            }
            else
            {
                final List<String> roles = refs.stream()
                    .flatMap(r -> r.roles.stream())
                    .distinct()
                    .toList();
                result = GuardedResolution.allowed(List.of(new GuardedRef(refs.get(0).qname, roles)));
            }
        }

        return result;
    }

    private OpenapiGuardResolver()
    {
    }
}
