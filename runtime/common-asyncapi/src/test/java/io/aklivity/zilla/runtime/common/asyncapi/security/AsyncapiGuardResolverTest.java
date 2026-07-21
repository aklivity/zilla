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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.common.asyncapi.model.Asyncapi;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiOperationView;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiView;

public class AsyncapiGuardResolverTest
{
    private static final String SPEC =
        """
        asyncapi: 3.0.0
        info:
          title: test
          version: 1.0.0
        servers:
          prod:
            host: localhost:9090
            protocol: http
        channels:
          send:
            messages:
              note:
                payload:
                  type: string
        operations:
          mapped:
            action: send
            channel:
              $ref: '#/channels/send'
            security:
              - $ref: '#/components/securitySchemes/bearerAuth'
          unmapped:
            action: send
            channel:
              $ref: '#/channels/send'
            security:
              - $ref: '#/components/securitySchemes/oauthScheme'
          mixed:
            action: send
            channel:
              $ref: '#/channels/send'
            security:
              - $ref: '#/components/securitySchemes/bearerAuth'
              - $ref: '#/components/securitySchemes/oauthScheme'
          nosecurity:
            action: send
            channel:
              $ref: '#/channels/send'
        components:
          securitySchemes:
            bearerAuth:
              type: http
              scheme: bearer
            oauthScheme:
              type: oauth2
              flows: {}
        """;

    private static AsyncapiOperationView operation(
        String operationId) throws Exception
    {
        Asyncapi model = new AsyncapiParser().parse(SPEC);
        AsyncapiView view = AsyncapiView.of(model);
        return view.operations.get(operationId);
    }

    @Test
    public void shouldAllowUnguardedWhenNoSecurityDeclared() throws Exception
    {
        AsyncapiOperationView operation = operation("nosecurity");

        GuardedResolution resolution = AsyncapiGuardResolver.resolve(
            operation.name, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, empty());
    }

    @Test
    public void shouldGuardWhenSchemeMapped() throws Exception
    {
        AsyncapiOperationView operation = operation("mapped");

        GuardedResolution resolution = AsyncapiGuardResolver.resolve(
            operation.name, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, hasSize(1));
        assertThat(resolution.guarded.get(0).qname, equalTo("guard0"));
    }

    @Test
    public void shouldAllowUnguardedWhenSchemeNotMapped() throws Exception
    {
        AsyncapiOperationView operation = operation("unmapped");

        GuardedResolution resolution = AsyncapiGuardResolver.resolve(
            operation.name, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, empty());
    }

    @Test
    public void shouldGuardByMappedSchemeOnlyWhenMixed() throws Exception
    {
        AsyncapiOperationView operation = operation("mixed");

        GuardedResolution resolution = AsyncapiGuardResolver.resolve(
            operation.name, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, hasSize(1));
        assertThat(resolution.guarded.get(0).qname, equalTo("guard0"));
    }

    @Test
    public void shouldDenyMultipleDistinctGuardsRequiredSimultaneously() throws Exception
    {
        AsyncapiOperationView operation = operation("mixed");

        GuardedResolution resolution = AsyncapiGuardResolver.resolve(
            operation.name, "test", operation.security,
            Map.of("bearerAuth", "guard0", "oauthScheme", "guard1"),
            name -> "guard0".equals(name) ? 1L : 2L, id -> id == 1L ? "guard0" : "guard1");

        assertThat(resolution.denied(), equalTo(true));
        assertThat(resolution.reason, containsString("multiple distinct guards"));
    }

    @Test
    public void shouldAllowUnguardedWhenSecurityMapAbsent() throws Exception
    {
        AsyncapiOperationView operation = operation("mapped");

        GuardedResolution resolution = AsyncapiGuardResolver.resolve(
            operation.name, "test", operation.security, null,
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, empty());
    }
}
