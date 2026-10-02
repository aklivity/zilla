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
package io.aklivity.zilla.runtime.binding.llm.internal.dialect;

import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmNativeEventOutput;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Confirms {@link LlmLegacyDialect#signer()}'s null-when-unneeded contract stays lazy: {@link LlmLegacyDialectResolver}
 * constructs every registered dialect on every binding attach, needed for detection and cross-dialect
 * lookup, not only the binding's own configured one -- so a dialect whose {@code signer()} would trigger a
 * real side effect (e.g. starting background credential resolution) must never trigger it merely from being
 * created or held by a resolver, only from {@code signer()} itself being called.
 */
public class LlmLegacyDialectSignerLazinessTest
{
    @Test
    public void shouldNotInvokeContextWhenDialectIsCreated()
    {
        LlmLegacyDialectContext context = eagerlyFailingContext();

        LlmLegacyEagerFailingDialect dialect = new LlmLegacyEagerFailingDialect(context);

        assertThat(dialect, not(nullValue()));
    }

    @Test
    public void shouldNotInvokeContextWhenDialectIsHeldByResolver()
    {
        LlmLegacyDialectContext context = eagerlyFailingContext();
        LlmLegacyEagerFailingDialect dialect = new LlmLegacyEagerFailingDialect(context);

        LlmLegacyDialectResolver resolver = new LlmLegacyDialectResolver("other", of(dialect));
        LlmLegacyDialect resolved = resolver.resolve(JsonEnvelope.NONE);

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldInvokeContextOnlyWhenSignerIsRequested()
    {
        LlmLegacyDialectContext context = eagerlyFailingContext();
        LlmLegacyEagerFailingDialect dialect = new LlmLegacyEagerFailingDialect(context);

        assertThrows(IllegalStateException.class, dialect::signer);
    }

    private static LlmLegacyDialectContext eagerlyFailingContext()
    {
        LlmLegacyDialectContext context = mock(LlmLegacyDialectContext.class);
        when(context.signaler()).thenThrow(new IllegalStateException("dialect construction must not call signaler()"));
        return context;
    }

    private static final class LlmLegacyEagerFailingDialect implements LlmLegacyDialect
    {
        private final LlmLegacyDialectContext context;

        private LlmLegacyEagerFailingDialect(
            LlmLegacyDialectContext context)
        {
            this.context = context;
        }

        @Override
        public String name()
        {
            return "eager-failing";
        }

        @Override
        public boolean detect(
            JsonEnvelope headers)
        {
            return false;
        }

        @Override
        public String requestPath(
            String basePath)
        {
            return basePath;
        }

        @Override
        public String credentialsHeader()
        {
            return "authorization";
        }

        @Override
        public String unauthorizedBody()
        {
            return "{}";
        }

        @Override
        public String errorBody(
            int status,
            String type,
            String message)
        {
            return "{}";
        }

        @Override
        public String requestContentType()
        {
            return "application/json";
        }

        @Override
        public Set<String> responseContentTypes()
        {
            return Set.of("application/json");
        }

        @Override
        public JsonTransform supplyRequestDecoder(
            JsonEnvelope envelope)
        {
            return null;
        }

        @Override
        public JsonTransform supplyExtractor(
            Kind kind,
            JsonEnvelope envelope)
        {
            return null;
        }

        @Override
        public JsonTransform supplyRequestEncoder(
            JsonEnvelope envelope)
        {
            return null;
        }

        @Override
        public JsonTransform supplyResponseDecodeTransform()
        {
            return null;
        }

        @Override
        public JsonSink supplyResponseEncodeSink(
            JsonEnvelope envelope,
            LlmNativeEventOutput output)
        {
            return null;
        }

        @Override
        public JsonTransform supplySchemaValidator(
            Kind kind)
        {
            return null;
        }

        @Override
        public DirectBufferEx terminator(
            Kind kind)
        {
            return null;
        }

        @Override
        public LlmRequestSigner signer()
        {
            context.signaler();
            return (method, scheme, authority, path, headers, body, bodyOffset, bodyLength) -> headers;
        }
    }
}
