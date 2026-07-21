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
package io.aklivity.zilla.runtime.common.asyncapi.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiSecuritySchemeView;

public final class AsyncapiGuardResolver
{
    public static GuardedResolution resolve(
        String operationId,
        String specLabel,
        List<AsyncapiSecuritySchemeView> security,
        Map<String, String> securityByScheme,
        ToLongFunction<String> resolveId,
        LongFunction<String> supplyQName)
    {
        GuardedResolution result = GuardedResolution.allowed(List.of());

        if (security != null && !security.isEmpty())
        {
            final List<GuardedRef> refs = new ArrayList<>();

            for (AsyncapiSecuritySchemeView scheme : security)
            {
                final String guard = securityByScheme != null ? securityByScheme.get(scheme.name) : null;
                if (guard == null)
                {
                    continue;
                }

                final String qname = supplyQName.apply(resolveId.applyAsLong(guard));
                final List<String> roles = scheme.scopes != null ? scheme.scopes : List.of();
                refs.add(new GuardedRef(qname, roles));
            }

            if (!refs.isEmpty())
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
        }

        return result;
    }

    private AsyncapiGuardResolver()
    {
    }
}
