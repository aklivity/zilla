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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.common.openapi.model.Openapi;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiView;

public class OpenapiGuardResolverTest
{
    private static final String SPEC =
        """
        {
          "openapi": "3.1.0",
          "info": { "title": "test", "version": "1.0.0" },
          "servers": [ { "url": "https://api.example.com" } ],
          "components": { "securitySchemes": {
            "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "jwt" },
            "oauthScheme": { "type": "oauth2", "flows": {} }
          } },
          "paths": {
            "/mapped": {
              "get": {
                "operationId": "mapped",
                "security": [ { "bearerAuth": [ "read" ] } ],
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/unmapped": {
              "get": {
                "operationId": "unmapped",
                "security": [ { "oauthScheme": [ "read" ] } ],
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/mixed": {
              "get": {
                "operationId": "mixed",
                "security": [ { "bearerAuth": [ "read" ], "oauthScheme": [ "write" ] } ],
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/oralternative": {
              "get": {
                "operationId": "oralternative",
                "security": [ { "bearerAuth": [ "read" ] }, { "oauthScheme": [ "read" ] } ],
                "responses": { "200": { "description": "ok" } }
              }
            },
            "/nosecurity": {
              "get": {
                "operationId": "nosecurity",
                "responses": { "200": { "description": "ok" } }
              }
            }
          }
        }
        """;

    private static OpenapiOperationView operation(
        String operationId) throws Exception
    {
        Openapi model = new OpenapiParser().parse(SPEC);
        OpenapiView view = OpenapiView.of(model);
        return view.operations.get(operationId);
    }

    @Test
    public void shouldAllowUnguardedWhenNoSecurityDeclared() throws Exception
    {
        OpenapiOperationView operation = operation("nosecurity");

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, empty());
    }

    @Test
    public void shouldGuardWhenSchemeMapped() throws Exception
    {
        OpenapiOperationView operation = operation("mapped");

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, hasSize(1));
        assertThat(resolution.guarded.get(0).qname, equalTo("guard0"));
        assertThat(resolution.guarded.get(0).roles, contains("read"));
    }

    @Test
    public void shouldAllowUnguardedWhenSchemeNotMapped() throws Exception
    {
        OpenapiOperationView operation = operation("unmapped");

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, empty());
    }

    @Test
    public void shouldGuardByMappedSchemeOnlyWhenMixed() throws Exception
    {
        OpenapiOperationView operation = operation("mixed");

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, "test", operation.security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, hasSize(1));
        assertThat(resolution.guarded.get(0).qname, equalTo("guard0"));
        assertThat(resolution.guarded.get(0).roles, contains("read"));
    }

    @Test
    public void shouldDenyOrAlternativeSecurity() throws Exception
    {
        OpenapiOperationView operation = operation("oralternative");

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, "test", operation.security,
            Map.of("bearerAuth", "guard0", "oauthScheme", "guard1"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(true));
        assertThat(resolution.reason, containsString("OR-alternative"));
    }

    @Test
    public void shouldDenyMultipleDistinctGuardsRequiredSimultaneously() throws Exception
    {
        OpenapiOperationView operation = operation("mixed");

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, "test", operation.security,
            Map.of("bearerAuth", "guard0", "oauthScheme", "guard1"),
            name -> "guard0".equals(name) ? 1L : 2L, id -> id == 1L ? "guard0" : "guard1");

        assertThat(resolution.denied(), equalTo(true));
        assertThat(resolution.reason, containsString("multiple distinct guards"));
    }

    @Test
    public void shouldAllowUnguardedWhenSecurityMapAbsent() throws Exception
    {
        OpenapiOperationView operation = operation("mapped");

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            operation.id, "test", operation.security, null,
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, empty());
    }

    @Test
    public void shouldAllowUnguardedWhenSecurityListEmpty() throws Exception
    {
        List<List<io.aklivity.zilla.runtime.common.openapi.view.OpenapiSecurityRequirementView>> security = List.of();

        GuardedResolution resolution = OpenapiGuardResolver.resolve(
            "op", "test", security, Map.of("bearerAuth", "guard0"),
            name -> 1L, id -> "guard0");

        assertThat(resolution.denied(), equalTo(false));
        assertThat(resolution.guarded, empty());
    }
}
