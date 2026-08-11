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
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static io.aklivity.zilla.runtime.common.x509.X509Fields.SUBJECT_CN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.config.binding.tls.TlsWithConfig;
import io.aklivity.zilla.config.engine.RouteConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class TlsRouteConfigTest
{
    private static final long SESSION_ID = 7L;

    @Test
    public void shouldResolveCertificateFromGuardedIdentity()
    {
        GuardHandler guard = mock(GuardHandler.class);
        when(guard.identity(anyLong())).thenReturn("client1");

        EngineContext context = mock(EngineContext.class);
        when(context.supplyGuard(anyLong())).thenReturn(guard);

        TlsRouteConfig route = new TlsRouteConfig(context, "test:app0",
            newRoute("${guarded['x509_0'].identity}"));

        assertThat(route.with, not(nullValue()));
        assertThat(route.with.resolve(SESSION_ID), equalTo(Map.of(SUBJECT_CN, "client1")));
    }

    @Test
    public void shouldRejectCertificateReferringToUnresolvedGuard()
    {
        EngineContext context = mock(EngineContext.class);
        when(context.supplyGuard(anyLong())).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> new TlsRouteConfig(context, "test:app0", newRoute("${guarded['x509_0'].identity}")));

        assertThat(ex.getMessage(), equalTo(
            "binding test:app0 route with.certificate refers to unresolved guard: x509_0"));
    }

    @Test
    public void shouldResolveCertificateWithoutGuardWhenLiteral()
    {
        EngineContext context = mock(EngineContext.class);

        TlsRouteConfig route = new TlsRouteConfig(context, "test:app0", newRoute("client1"));

        assertThat(route.with.resolve(SESSION_ID), equalTo(Map.of(SUBJECT_CN, "client1")));
    }

    @Test
    public void shouldResolveNoCertificateWithoutWith()
    {
        EngineContext context = mock(EngineContext.class);

        RouteConfig config = RouteConfig.builder()
            .exit("net0")
            .build();
        config.resolveId = name -> 0L;

        TlsRouteConfig route = new TlsRouteConfig(context, "test:app0", config);

        assertThat(route.with, nullValue());
    }

    private static RouteConfig newRoute(
        String subjectCommonName)
    {
        RouteConfig route = RouteConfig.builder()
            .exit("net0")
            .with(TlsWithConfig.builder()
                .certificate()
                    .subjectCommonName(subjectCommonName)
                    .build()
                .build())
            .build();
        route.resolveId = name -> 0L;

        return route;
    }
}
