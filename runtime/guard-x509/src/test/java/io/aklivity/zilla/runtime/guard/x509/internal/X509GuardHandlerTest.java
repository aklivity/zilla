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
package io.aklivity.zilla.runtime.guard.x509.internal;

import static io.aklivity.zilla.runtime.engine.guard.GuardHandler.NOT_AUTHORIZED;
import static io.aklivity.zilla.specs.guard.x509.certificates.X509Certificates.PARTNER_CHAIN;
import static io.aklivity.zilla.specs.guard.x509.certificates.X509Certificates.PLATFORM;
import static io.aklivity.zilla.specs.guard.x509.certificates.X509Certificates.PLATFORM_CHAIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.agrona.collections.MutableLong;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.guard.x509.X509OptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler.LongCompletionCallback;

public class X509GuardHandlerTest
{
    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        when(context.clock()).thenReturn(mock(Clock.class));
        when(context.supplyEventWriter()).thenReturn(mock(MessageConsumer.class));
    }

    @Test
    public void shouldIdentifyByCanonicalSubjectByDefault()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder().build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(sessionId, not(equalTo(NOT_AUTHORIZED)));
        assertThat(guard.identity(sessionId),
            equalTo("cn=platform.example.com,ou=engineering,ou=platform,o=example inc,c=us"));
    }

    @Test
    public void shouldIdentifyBySubjectCommonName()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .identity("subject.cn")
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(guard.identity(sessionId), equalTo("platform.example.com"));
    }

    @Test
    public void shouldIdentifyBySubjectAltName()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .identity("san.uri")
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(guard.identity(sessionId), equalTo("spiffe://example.com/ns/prod/sa/platform"));
    }

    @Test
    public void shouldResolveAttributes()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .identity("subject.cn")
            .attribute("organization", "subject.o")
            .attribute("tenant", "san.uri")
            .attribute("mail", "san.email")
            .attribute("thumbprint", "x5t.s256")
            .attribute("issuer", "issuer.cn")
            .attribute("missing", "san.ip")
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(guard.attribute(sessionId, "organization"), equalTo("Example Inc"));
        assertThat(guard.attribute(sessionId, "tenant"), equalTo("spiffe://example.com/ns/prod/sa/platform"));
        assertThat(guard.attribute(sessionId, "mail"), equalTo("platform@example.com"));
        assertThat(guard.attribute(sessionId, "thumbprint"), equalTo("iFZQBQX3Dn3aMFZDLDgrYWIWKJz-_yqy9YLGLcxkUO8"));
        assertThat(guard.attribute(sessionId, "issuer"), equalTo("Internal CA"));
        assertThat(guard.attribute(sessionId, "missing"), nullValue());
        assertThat(guard.attribute(sessionId, "unconfigured"), nullValue());
    }

    @Test
    public void shouldResolveRoleWhenSingleFieldMatches()
    {
        X509GuardHandler guard = newGuard(roles());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PARTNER_CHAIN);

        assertTrue(guard.verify(sessionId, List.of("partner")));
        assertFalse(guard.verify(sessionId, List.of("internal")));
    }

    // properties within an object are AND'd, and subject.ou is multi-valued so matching is existential
    @Test
    public void shouldResolveRoleWhenEveryFieldInMatchMatches()
    {
        X509GuardHandler guard = newGuard(roles());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertTrue(guard.verify(sessionId, List.of("internal")));
        assertFalse(guard.verify(sessionId, List.of("partner")));
    }

    // array members are OR'd, so the second match alone is enough
    @Test
    public void shouldResolveRoleWhenLaterMatchGlobMatches()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .match("internal")
                .field("subject.ou", "Finance")
                .build()
            .match("internal")
                .field("san.dns", "*.internal.example.com")
                .build()
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertTrue(guard.verify(sessionId, List.of("internal")));
    }

    @Test
    public void shouldNotResolveRoleWhenAnyFieldInMatchDiffers()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .match("internal")
                .field("issuer.cn", "Internal CA")
                .field("subject.ou", "Finance")
                .build()
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(sessionId, not(equalTo(NOT_AUTHORIZED)));
        assertFalse(guard.verify(sessionId, List.of("internal")));
    }

    @Test
    public void shouldNotResolveRoleWhenFieldAbsent()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .match("anywhere")
                .field("san.ip", "*")
                .build()
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(sessionId, not(equalTo(NOT_AUTHORIZED)));
        assertFalse(guard.verify(sessionId, List.of("anywhere")));
    }

    // a chain that matches under more than one role holds their union
    @Test
    public void shouldResolveRolesAsUnion()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .match("internal")
                .field("issuer.cn", "Internal CA")
                .build()
            .match("platform")
                .field("subject.ou", "Platform")
                .build()
            .match("partner")
                .field("issuer.cn", "Partner Issuing CA")
                .build()
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertTrue(guard.verify(sessionId, List.of("internal", "platform")));
        assertFalse(guard.verify(sessionId, List.of("internal", "partner")));
    }

    @Test
    public void shouldMatchFieldCaseInsensitively()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .match("internal")
                .field("issuer.cn", "internal ca")
                .build()
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertTrue(guard.verify(sessionId, List.of("internal")));
    }

    // the glob is anchored, so a substring of a longer value must not match
    @Test
    public void shouldNotMatchUnanchoredGlob()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .match("internal")
                .field("issuer.cn", "Internal")
                .build()
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertFalse(guard.verify(sessionId, List.of("internal")));
    }

    @Test
    public void shouldVerifyWhenNoRolesRequired()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder().build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertTrue(guard.verify(sessionId, List.of()));
    }

    @Test
    public void shouldNotVerifyUnknownSession()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder().build());

        assertFalse(guard.verify(42L, List.of()));
        assertThat(guard.identity(42L), nullValue());
        assertThat(guard.attribute(42L, "organization"), nullValue());
        assertThat(guard.credentials(42L), nullValue());
        assertThat(guard.expiresAt(42L), equalTo(Long.MAX_VALUE));
        assertThat(guard.expiringAt(42L), equalTo(Long.MAX_VALUE));
    }

    @Test
    public void shouldAuthorizeLeafOnlyChain()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .identity("subject.cn")
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM);

        assertThat(guard.identity(sessionId), equalTo("platform.example.com"));
    }

    @Test
    public void shouldAuthorizeCredentialsWithEscapedNewlines()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .identity("subject.cn")
            .build());

        String escaped = "  " + PLATFORM_CHAIN.replace("\n", "\\n") + "  ";

        long sessionId = guard.reauthorize(0L, 0L, 101L, escaped);

        assertThat(guard.identity(sessionId), equalTo("platform.example.com"));
    }

    @Test
    public void shouldNotAuthorizeMissingCredentials()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder().build());

        assertThat(guard.reauthorize(0L, 0L, 101L, null), equalTo(NOT_AUTHORIZED));
    }

    @Test
    public void shouldNotAuthorizeUnparseableCredentials()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder().build());

        assertThat(guard.reauthorize(0L, 0L, 101L, "not-a-certificate-chain"), equalTo(NOT_AUTHORIZED));
    }

    @Test
    public void shouldReportCredentialsAndExpiry()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder().build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(guard.credentials(sessionId), equalTo(PLATFORM_CHAIN));
        assertThat(guard.expiresAt(sessionId), equalTo(guard.expiringAt(sessionId)));
        assertTrue(guard.expiresAt(sessionId) > System.currentTimeMillis());
        assertFalse(guard.challenge(sessionId, System.currentTimeMillis()));
    }

    @Test
    public void shouldDeauthorize()
    {
        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .identity("subject.cn")
            .build());

        long sessionId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);
        long sharedId = guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN);

        assertThat(sharedId, equalTo(sessionId));

        guard.deauthorize(sessionId);

        assertThat(guard.identity(sessionId), equalTo("platform.example.com"));

        guard.deauthorize(sharedId);

        assertThat(guard.identity(sessionId), nullValue());
    }

    @Test
    public void shouldDeferAsyncReauthorize()
    {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        doAnswer(invocation -> dispatched.add(invocation.getArgument(0))).when(context).dispatch(any());

        X509GuardHandler guard = newGuard(X509OptionsConfig.builder()
            .identity("subject.cn")
            .build());

        long[] completed = new long[] { Long.MIN_VALUE, Long.MIN_VALUE };
        LongCompletionCallback completion = new LongCompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                long sessionId)
            {
                completed[0] = contextId;
                completed[1] = sessionId;
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                throw new AssertionError("unexpected failure", ex);
            }
        };

        guard.reauthorize(0L, 0L, 101L, PLATFORM_CHAIN, completion);

        // a verified chain carries everything needed to decide, but the contract says
        // the caller still must not observe the result before this returns
        assertThat(completed[1], equalTo(Long.MIN_VALUE));
        assertThat(dispatched.size(), equalTo(1));

        dispatched.remove().run();

        assertThat(completed[0], equalTo(101L));
        assertThat(completed[1], not(equalTo(Long.MIN_VALUE)));
        assertThat(guard.identity(completed[1]), equalTo("platform.example.com"));
    }

    private static X509OptionsConfig roles()
    {
        return X509OptionsConfig.builder()
            .identity("subject.cn")
            .match("partner")
                .field("issuer.cn", "Partner Issuing CA")
                .build()
            .match("internal")
                .field("issuer.cn", "Internal CA")
                .field("subject.ou", "Platform")
                .build()
            .build();
    }

    private X509GuardHandler newGuard(
        X509OptionsConfig options)
    {
        return new X509GuardHandler(options, context, new MutableLong(1L)::getAndIncrement);
    }
}
