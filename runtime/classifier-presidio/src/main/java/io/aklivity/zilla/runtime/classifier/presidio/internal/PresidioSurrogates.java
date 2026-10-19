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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import java.util.HashMap;
import java.util.Map;

final class PresidioSurrogates
{
    static final String OPEN = "⟦";
    static final String CLOSE = "⟧";

    private final Map<Long, Scope> scopes;

    PresidioSurrogates()
    {
        this.scopes = new HashMap<>();
    }

    PresidioSurrogate mint(
        long scopeId,
        String entity,
        String alias,
        String original)
    {
        return scopes.computeIfAbsent(scopeId, Scope::new).mint(entity, alias, original);
    }

    PresidioSurrogate lookup(
        long scopeId,
        String token)
    {
        Scope scope = scopes.get(scopeId);

        return scope != null ? scope.byToken.get(token) : null;
    }

    private static final class Scope
    {
        private final String nonce;
        private final Map<String, PresidioSurrogate> byToken;
        private final Map<String, PresidioSurrogate> byOriginal;
        private final Map<String, Integer> ordinals;

        private Scope(
            long scopeId)
        {
            this.nonce = String.format("%08x", (int) ((scopeId * 0x9E3779B97F4A7C15L) >>> 32));
            this.byToken = new HashMap<>();
            this.byOriginal = new HashMap<>();
            this.ordinals = new HashMap<>();
        }

        private PresidioSurrogate mint(
            String entity,
            String alias,
            String original)
        {
            return byOriginal.computeIfAbsent(entity + '\u0000' + original, key -> create(entity, alias, original));
        }

        private PresidioSurrogate create(
            String entity,
            String alias,
            String original)
        {
            int ordinal = ordinals.merge(entity, 1, Integer::sum);
            String token = OPEN + entity + "_" + ordinal + ":" + nonce + CLOSE;
            PresidioSurrogate surrogate = new PresidioSurrogate(alias, original, token);

            byToken.put(token, surrogate);

            return surrogate;
        }
    }
}
