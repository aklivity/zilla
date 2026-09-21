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

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialect;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmDialectContext;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmNativeEventOutput;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Confirms {@link LlmDialect#signer()}'s null-when-unneeded contract stays lazy: {@link LlmDialectResolver}
 * constructs every registered dialect on every binding attach, needed for detection and cross-dialect
 * lookup, not only the binding's own configured one -- so a dialect whose {@code signer()} would trigger a
 * real side effect (e.g. starting background credential resolution) must never trigger it merely from being
 * created or held by a resolver, only from {@code signer()} itself being called.
 */
public class LlmDialectSignerLazinessTest
{
    @Test
    public void shouldNotInvokeContextWhenDialectIsCreated()
    {
        LlmDialectContext context = eagerlyFailingContext();

        LlmEagerFailingDialect dialect = new LlmEagerFailingDialect(context);

        assertThat(dialect, not(nullValue()));
    }

    @Test
    public void shouldNotInvokeContextWhenDialectIsHeldByResolver()
    {
        LlmDialectContext context = eagerlyFailingContext();
        LlmEagerFailingDialect dialect = new LlmEagerFailingDialect(context);

        LlmDialectResolver resolver = new LlmDialectResolver("other", of(dialect));
        LlmDialect resolved = resolver.resolve(JsonEnvelope.NONE);

        assertThat(resolved, nullValue());
    }

    @Test
    public void shouldInvokeContextOnlyWhenSignerIsRequested()
    {
        LlmDialectContext context = eagerlyFailingContext();
        LlmEagerFailingDialect dialect = new LlmEagerFailingDialect(context);

        assertThrows(IllegalStateException.class, dialect::signer);
    }

    private static LlmDialectContext eagerlyFailingContext()
    {
        LlmDialectContext context = mock(LlmDialectContext.class);
        when(context.signaler()).thenThrow(new IllegalStateException("dialect construction must not call signaler()"));
        return context;
    }

    private static final class LlmEagerFailingDialect implements LlmDialect
    {
        private final LlmDialectContext context;

        private LlmEagerFailingDialect(
            LlmDialectContext context)
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
        public JsonTransform supplyDecoder(
            Kind kind,
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
        public JsonTransform supplyEncoder(
            Kind kind,
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
