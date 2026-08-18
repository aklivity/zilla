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
package io.aklivity.zilla.runtime.catalog.inline.internal;

import static io.aklivity.zilla.runtime.engine.catalog.CatalogHandler.NO_SCHEMA_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class InlineCatalogHandlerTest
{
    private static final String SCHEMA_A = "{\"type\":\"object\",\"required\":[\"a\"]}";
    private static final String SCHEMA_B = "{\"type\":\"object\",\"required\":[\"b\"]}";

    private InlineCatalogHandler newHandler()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        return new InlineCatalogHandler(options, null);
    }

    @Test
    public void shouldRegisterAndResolveSchema()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);

        assertThat(schemaId, not(equalTo(NO_SCHEMA_ID)));
        assertThat(handler.resolve(schemaId), equalTo(SCHEMA_A));
        assertThat(handler.resolve("weather", "latest"), equalTo(schemaId));
    }

    @Test
    public void shouldConstructWithoutOptionsAndAcceptRuntimeRegistration()
    {
        InlineCatalogHandler handler = new InlineCatalogHandler(null, null);

        int schemaId = handler.register("weather", SCHEMA_A);

        assertThat(schemaId, not(equalTo(NO_SCHEMA_ID)));
        assertThat(handler.resolve(schemaId), equalTo(SCHEMA_A));
    }

    @Test
    public void shouldContentAddressIdenticalSchemas()
    {
        InlineCatalogHandler handler = newHandler();

        int first = handler.register("weather", SCHEMA_A);
        int second = handler.register("forecast", SCHEMA_A);

        assertThat(second, equalTo(first));
    }

    @Test
    public void shouldNotBumpReferenceWhenReregisteringSameContent()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);
        handler.register("weather", SCHEMA_A);

        handler.unregister("weather");

        assertThat(handler.resolve(schemaId), nullValue());
    }

    @Test
    public void shouldReplaceSchemaWhenSubjectContentChanges()
    {
        InlineCatalogHandler handler = newHandler();

        int idA = handler.register("weather", SCHEMA_A);
        int idB = handler.register("weather", SCHEMA_B);

        assertThat(idB, not(equalTo(idA)));
        assertThat(handler.resolve(idA), nullValue());
        assertThat(handler.resolve(idB), equalTo(SCHEMA_B));
        assertThat(handler.resolve("weather", "latest"), equalTo(idB));
    }

    @Test
    public void shouldRetainSharedSchemaUntilLastUnregister()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);
        handler.register("forecast", SCHEMA_A);

        handler.unregister("weather");
        assertThat(handler.resolve(schemaId), equalTo(SCHEMA_A));

        handler.unregister("forecast");
        assertThat(handler.resolve(schemaId), nullValue());
    }

    @Test
    public void shouldReturnRemovedSchemaIdOnUnregister()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("weather", SCHEMA_A);
        int[] removed = handler.unregister("weather");

        assertThat(removed.length, equalTo(1));
        assertThat(removed[0], equalTo(schemaId));
    }

    @Test
    public void shouldReturnEmptyOnUnregisterUnknownSubject()
    {
        InlineCatalogHandler handler = newHandler();

        int[] removed = handler.unregister("missing");

        assertThat(removed.length, equalTo(0));
    }

    @Test
    public void shouldResolveMissingSubjectToNoSchemaId()
    {
        InlineCatalogHandler handler = newHandler();

        assertThat(handler.resolve("missing", "latest"), equalTo(NO_SCHEMA_ID));
    }

    @Test
    public void shouldResolveGuardedIdentityExpressionInSubjectBeforeLookup()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        InlineCatalogHandler handler = new InlineCatalogHandler(options, name -> new TestGuard("alice", null));

        int schemaId = handler.register("orders.alice-value", SCHEMA_A);

        int resolved = handler.resolve("orders.${guarded['product:api_keys'].identity}-value", "latest", 7L);

        assertThat(resolved, equalTo(schemaId));
    }

    @Test
    public void shouldResolveGuardedAttributeExpressionInSubjectBeforeLookup()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        InlineCatalogHandler handler =
            new InlineCatalogHandler(options, name -> new TestGuard(null, Map.of("tenant", "acme")));

        int schemaId = handler.register("orders.acme-value", SCHEMA_A);

        int resolved =
            handler.resolve("orders.${guarded['product:api_keys'].attributes.tenant}-value", "latest", 7L);

        assertThat(resolved, equalTo(schemaId));
    }

    @Test
    public void shouldResolveDistinctGuardsByNameInExpressions()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        Map<String, GuardHandler> guardsByName = Map.of(
            "product:api_keys", new TestGuard("alice", null),
            "jwt0", new TestGuard("bob", null));
        InlineCatalogHandler handler = new InlineCatalogHandler(options, guardsByName::get);

        int schemaIdA = handler.register("orders.alice-value", SCHEMA_A);
        int schemaIdB = handler.register("orders.bob-value", SCHEMA_B);

        assertThat(
            handler.resolve("orders.${guarded['product:api_keys'].identity}-value", "latest", 7L),
            equalTo(schemaIdA));
        assertThat(
            handler.resolve("orders.${guarded['jwt0'].identity}-value", "latest", 7L),
            equalTo(schemaIdB));
    }

    @Test
    public void shouldFailToResolveGuardedExpressionWhenNoGuardCapabilityAvailable()
    {
        InlineCatalogHandler handler = newHandler();

        handler.register("orders.alice-value", SCHEMA_A);

        int resolved = handler.resolve("orders.${guarded['product:api_keys'].identity}-value", "latest", 7L);

        assertThat(resolved, equalTo(NO_SCHEMA_ID));
    }

    @Test
    public void shouldFailToResolveGuardedExpressionWhenGuardNameUnresolvable()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        InlineCatalogHandler handler = new InlineCatalogHandler(options, name -> null);

        handler.register("orders.alice-value", SCHEMA_A);

        int resolved = handler.resolve("orders.${guarded['product:api_keys'].identity}-value", "latest", 7L);

        assertThat(resolved, equalTo(NO_SCHEMA_ID));
    }

    @Test
    public void shouldIgnoreGuardedExpressionSyntaxWhenAuthorizationOverloadIsNotUsed()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        InlineCatalogHandler handler = new InlineCatalogHandler(options, name -> new TestGuard("alice", null));

        int schemaId = handler.register("weather", SCHEMA_A);

        assertThat(handler.resolve("weather", "latest"), equalTo(schemaId));
    }

    @Test
    public void shouldResolveLiteralSubjectAgainstWildcardRegisteredSubject()
    {
        InlineCatalogHandler handler = newHandler();

        int schemaId = handler.register("orders.*-value", SCHEMA_A);

        assertThat(handler.resolve("orders.acme-value", "latest"), equalTo(schemaId));
        assertThat(handler.resolve("orders.globex-value", "latest"), equalTo(schemaId));
    }

    @Test
    public void shouldResolveGuardedExpressionAgainstWildcardRegisteredSubject()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        InlineCatalogHandler handler = new InlineCatalogHandler(options, name -> new TestGuard("acme", null));

        int schemaId = handler.register("orders.*-value", SCHEMA_A);

        int resolved = handler.resolve("orders.${guarded['product:api_keys'].identity}-value", "latest", 7L);

        assertThat(resolved, equalTo(schemaId));
    }

    @Test
    public void shouldMatchWildcardRegisteredSubjectWhenGuardedIdentityIsBlank()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder().build();
        InlineCatalogHandler handler = new InlineCatalogHandler(options, name -> new TestGuard(null, null));

        int schemaId = handler.register("orders.*-value", SCHEMA_A);

        int resolved = handler.resolve("orders.${guarded['product:api_keys'].identity}-value", "latest", 7L);

        assertThat(resolved, equalTo(schemaId));
    }

    @Test
    public void shouldNotMatchWildcardRegisteredSubjectWithDifferentPrefix()
    {
        InlineCatalogHandler handler = newHandler();

        handler.register("orders.*-value", SCHEMA_A);

        assertThat(handler.resolve("shipments.acme-value", "latest"), equalTo(NO_SCHEMA_ID));
    }

    @Test
    public void shouldNotMatchWildcardRegisteredSubjectWithDifferentSuffix()
    {
        InlineCatalogHandler handler = newHandler();

        handler.register("orders.*-value", SCHEMA_A);

        assertThat(handler.resolve("orders.acme-key", "latest"), equalTo(NO_SCHEMA_ID));
    }

    @Test
    public void shouldNotMatchWildcardRegisteredSubjectWithDifferentVersion()
    {
        InlineCatalogHandler handler = newHandler();

        handler.register("orders.*-value", SCHEMA_A);

        assertThat(handler.resolve("orders.acme-value", "v2"), equalTo(NO_SCHEMA_ID));
    }

    @Test
    public void shouldPreferExactMatchOverWildcardRegisteredSubject()
    {
        InlineCatalogHandler handler = newHandler();

        handler.register("orders.*-value", SCHEMA_A);
        int exact = handler.register("orders.acme-value", SCHEMA_B);

        assertThat(handler.resolve("orders.acme-value", "latest"), equalTo(exact));
        assertThat(handler.resolve(exact), equalTo(SCHEMA_B));
    }

    @Test
    public void shouldStopMatchingWildcardRegisteredSubjectAfterUnregister()
    {
        InlineCatalogHandler handler = newHandler();

        handler.register("orders.*-value", SCHEMA_A);
        handler.unregister("orders.*-value");

        assertThat(handler.resolve("orders.acme-value", "latest"), equalTo(NO_SCHEMA_ID));
    }

    private static final class TestGuard implements GuardHandler
    {
        private final String identity;
        private final Map<String, String> attributes;

        private TestGuard(
            String identity,
            Map<String, String> attributes)
        {
            this.identity = identity;
            this.attributes = attributes;
        }

        @Override
        public long reauthorize(
            long traceId,
            long bindingId,
            long contextId,
            String credentials)
        {
            return NOT_AUTHORIZED;
        }

        @Override
        public void reauthorize(
            long traceId,
            long bindingId,
            long contextId,
            String credentials,
            LongCompletionCallback completion)
        {
            completion.completed(contextId, NOT_AUTHORIZED);
        }

        @Override
        public void deauthorize(
            long sessionId)
        {
        }

        @Override
        public String identity(
            long sessionId)
        {
            return identity;
        }

        @Override
        public String attribute(
            long sessionId,
            String name)
        {
            return attributes != null ? attributes.get(name) : null;
        }

        @Override
        public String credentials(
            long sessionId)
        {
            return null;
        }

        @Override
        public boolean verify(
            long sessionId,
            List<String> roles)
        {
            return false;
        }

        @Override
        public long expiresAt(
            long sessionId)
        {
            return EXPIRES_NEVER;
        }

        @Override
        public long expiringAt(
            long sessionId)
        {
            return EXPIRES_NEVER;
        }

        @Override
        public boolean challenge(
            long sessionId,
            long now)
        {
            return false;
        }
    }
}
