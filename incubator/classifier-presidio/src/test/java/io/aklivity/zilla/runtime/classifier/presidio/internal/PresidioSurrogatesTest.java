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


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class PresidioSurrogatesTest
{
    private static final long SCOPE = 42L;

    private final PresidioSurrogates surrogates = new PresidioSurrogates();

    @Test
    public void shouldMintDelimitedTokenWithOrdinalAndScopeNonce()
    {
        PresidioSurrogate surrogate = surrogates.mint(SCOPE, "EMAIL_ADDRESS", "email", "jane.doe@example.com");

        assertThat(surrogate.token, matchesPattern("⟦EMAIL_ADDRESS_1:[0-9a-f]{8}⟧"));
    }

    @Test
    public void shouldExposeTokenAndOriginalBytes()
    {
        PresidioSurrogate surrogate = surrogates.mint(SCOPE, "PERSON", "person", "Zoë");

        assertThat(surrogate.tokenBuffer.capacity(), equalTo(surrogate.token.getBytes(StandardCharsets.UTF_8).length));
        assertThat(surrogate.originalBuffer.capacity(), equalTo("Zoë".getBytes(StandardCharsets.UTF_8).length));
        assertThat(surrogate.original, equalTo("Zoë"));
        assertThat(surrogate.alias, equalTo("person"));
    }

    @Test
    public void shouldMintSameSurrogateForSameOriginalInScope()
    {
        PresidioSurrogate first = surrogates.mint(SCOPE, "PERSON", "person", "Jane");
        PresidioSurrogate second = surrogates.mint(SCOPE, "PERSON", "person", "Jane");

        assertThat(second, sameInstance(first));
    }

    @Test
    public void shouldMintDistinctSurrogatesForDistinctOriginals()
    {
        PresidioSurrogate jane = surrogates.mint(SCOPE, "PERSON", "person", "Jane");
        PresidioSurrogate john = surrogates.mint(SCOPE, "PERSON", "person", "John");

        assertThat(jane.token, matchesPattern("⟦PERSON_1:[0-9a-f]{8}⟧"));
        assertThat(john.token, matchesPattern("⟦PERSON_2:[0-9a-f]{8}⟧"));
    }

    @Test
    public void shouldCountOrdinalsPerEntity()
    {
        surrogates.mint(SCOPE, "PERSON", "person", "Jane");
        PresidioSurrogate email = surrogates.mint(SCOPE, "EMAIL_ADDRESS", "email", "jane@example.com");

        assertThat(email.token, matchesPattern("⟦EMAIL_ADDRESS_1:[0-9a-f]{8}⟧"));
    }

    @Test
    public void shouldNotShareTokensAcrossScopes()
    {
        PresidioSurrogate here = surrogates.mint(1L, "PERSON", "person", "Jane");
        PresidioSurrogate there = surrogates.mint(2L, "PERSON", "person", "Jane");

        assertThat(there.token, not(equalTo(here.token)));
    }

    @Test
    public void shouldLookupMintedTokenInScope()
    {
        PresidioSurrogate minted = surrogates.mint(SCOPE, "PERSON", "person", "Jane");

        assertThat(surrogates.lookup(SCOPE, minted.token), sameInstance(minted));
    }

    @Test
    public void shouldNotLookupTokenMintedInAnotherScope()
    {
        PresidioSurrogate minted = surrogates.mint(1L, "PERSON", "person", "Jane");

        assertThat(surrogates.lookup(2L, minted.token), nullValue());
    }

    @Test
    public void shouldNotLookupUnknownToken()
    {
        surrogates.mint(SCOPE, "PERSON", "person", "Jane");

        assertThat(surrogates.lookup(SCOPE, "⟦PERSON_9:00000000⟧"), nullValue());
    }

    @Test
    public void shouldNotLookupInUnknownScope()
    {
        assertThat(surrogates.lookup(SCOPE, "⟦PERSON_1:00000000⟧"), nullValue());
    }
}
