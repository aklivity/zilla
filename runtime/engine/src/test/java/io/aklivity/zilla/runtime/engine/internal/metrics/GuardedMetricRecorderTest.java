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
package io.aklivity.zilla.runtime.engine.internal.metrics;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToLongFunction;

import jakarta.json.Json;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class GuardedMetricRecorderTest
{
    private static final int TENANT_USER_ID = 11;
    private static final int TENANT_ID = 12;
    private static final long GUARD_ID = 5L;
    private static final long AUTHORIZED = 100L;

    private EngineContext context;
    private GuardHandler guard;
    private ToLongFunction<String> resolveId;
    private IntFunction<LongConsumer> supplyWriter;
    private LongConsumer defaultWriter;
    private LongConsumer tenantUserWriter;
    private LongConsumer tenantWriter;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp()
    {
        context = mock(EngineContext.class);
        guard = mock(GuardHandler.class);
        resolveId = mock(ToLongFunction.class);
        supplyWriter = mock(IntFunction.class);
        defaultWriter = mock(LongConsumer.class);
        tenantUserWriter = mock(LongConsumer.class);
        tenantWriter = mock(LongConsumer.class);

        when(resolveId.applyAsLong("test0")).thenReturn(GUARD_ID);
        when(context.supplyGuard(GUARD_ID)).thenReturn(guard);
        when(context.supplyTypeId("tenant=acme,user=alice")).thenReturn(TENANT_USER_ID);
        when(context.supplyTypeId("tenant=acme")).thenReturn(TENANT_ID);
        when(supplyWriter.apply(0)).thenReturn(defaultWriter);
        when(supplyWriter.apply(TENANT_USER_ID)).thenReturn(tenantUserWriter);
        when(supplyWriter.apply(TENANT_ID)).thenReturn(tenantWriter);
        when(guard.identity(AUTHORIZED)).thenReturn("alice");
        when(guard.attribute(AUTHORIZED, "tenant")).thenReturn(Json.createValue("acme"));
    }

    @Test
    public void shouldRecordWithoutAttributes()
    {
        GuardedMetricRecorder recorder = new GuardedMetricRecorder(context, resolveId, List.of(), supplyWriter, 4);

        recorder.record(AUTHORIZED, 7L);

        verify(defaultWriter).accept(7L);
    }

    @Test
    public void shouldRecordWithIdentityAndAttributes()
    {
        GuardedMetricRecorder recorder = new GuardedMetricRecorder(context, resolveId, attributes(), supplyWriter, 4);

        recorder.record(AUTHORIZED, 7L);

        verify(tenantUserWriter).accept(7L);
        verify(defaultWriter, never()).accept(anyLong());
    }

    @Test
    public void shouldRecordWithoutAuthorization()
    {
        GuardedMetricRecorder recorder = new GuardedMetricRecorder(context, resolveId, attributes(), supplyWriter, 4);

        recorder.record(0L, 7L);

        verify(defaultWriter).accept(7L);
        verify(tenantUserWriter, never()).accept(anyLong());
    }

    @Test
    public void shouldIgnoreUnrecognisedExpressions()
    {
        List<AttributeConfig> attributes = List.of(
            attribute("tenant", "${guarded['test0'].attributes.tenant}"),
            attribute("model", "${llm.request.model}"),
            attribute("other", "literal"));
        GuardedMetricRecorder recorder = new GuardedMetricRecorder(context, resolveId, attributes, supplyWriter, 4);

        recorder.record(AUTHORIZED, 7L);

        verify(tenantWriter).accept(7L);
    }

    @Test
    public void shouldRejectCredentialsAttribute()
    {
        List<AttributeConfig> attributes = List.of(attribute("token", "${guarded['test0'].credentials}"));

        assertThrows(IllegalArgumentException.class,
            () -> new GuardedMetricRecorder(context, resolveId, attributes, supplyWriter, 4));
    }

    @Test
    public void shouldMemoiseAttributesPerAuthorization()
    {
        GuardedMetricRecorder recorder = new GuardedMetricRecorder(context, resolveId, attributes(), supplyWriter, 4);

        recorder.record(AUTHORIZED, 1L);
        recorder.record(AUTHORIZED, 2L);

        verify(guard, times(1)).identity(AUTHORIZED);
        verify(tenantUserWriter).accept(1L);
        verify(tenantUserWriter).accept(2L);
    }

    @Test
    public void shouldClearMemoisedAttributesWhenFull()
    {
        GuardedMetricRecorder recorder = new GuardedMetricRecorder(context, resolveId, attributes(), supplyWriter, 2);

        recorder.record(AUTHORIZED, 1L);
        recorder.record(AUTHORIZED + 1, 1L);
        recorder.record(AUTHORIZED + 2, 1L);
        recorder.record(AUTHORIZED, 1L);

        verify(guard, times(2)).identity(AUTHORIZED);
    }

    private static List<AttributeConfig> attributes()
    {
        return List.of(
            attribute("tenant", "${guarded['test0'].attributes.tenant}"),
            attribute("user", "${guarded['test0'].identity}"));
    }

    private static AttributeConfig attribute(
        String name,
        String value)
    {
        return AttributeConfig.builder().name(name).value(value).build();
    }
}
