/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.IntFunction;
import java.util.function.LongSupplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public class ScalarRecordTest
{
    private static final LongSupplier READER_42 = () -> 42L;

    @Test
    public void shouldResolveFields()
    {
        // GIVEN
        IntFunction<String> labelResolver = mock(IntFunction.class);
        when(labelResolver.apply(77)).thenReturn("namespace1");
        when(labelResolver.apply(7)).thenReturn("binding1");
        when(labelResolver.apply(8)).thenReturn("metric1");
        long bindingId = NamespacedId.id(77, 7);
        long metricId = NamespacedId.id(77, 8);
        ScalarRecord scalar = new ScalarRecord(bindingId, metricId, READER_42, labelResolver);

        // WHEN
        String namespaceName = scalar.namespaceName();
        String bindingName = scalar.bindingName();
        String metricName = scalar.metricName();
        long value = scalar.valueReader().getAsLong();

        // THEN
        assertThat(namespaceName, equalTo("namespace1"));
        assertThat(bindingName, equalTo("binding1"));
        assertThat(metricName, equalTo("metric1"));
        assertThat(value, equalTo(42L));
    }
}
