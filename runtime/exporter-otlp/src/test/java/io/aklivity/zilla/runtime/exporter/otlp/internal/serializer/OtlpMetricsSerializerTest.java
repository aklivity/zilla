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
package io.aklivity.zilla.runtime.exporter.otlp.internal.serializer;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.exporter.otlp.internal.serializer.OtlpMetricsSerializer.OtlpMetricsDescriptor;

public class OtlpMetricsSerializerTest
{
    @Test
    public void shouldFallBackToInternalNameWhenBindingKindNotCaptured() throws Exception
    {
        OtlpMetricsSerializer serializer = new OtlpMetricsSerializer(
            List.of(),
            List.of(),
            name -> null);

        Field descriptorField = OtlpMetricsSerializer.class.getDeclaredField("descriptor");
        descriptorField.setAccessible(true);
        OtlpMetricsDescriptor descriptor = (OtlpMetricsDescriptor) descriptorField.get(serializer);

        String name = descriptor.nameByBinding("http.request.size", null);

        assertThat(name, equalTo("http.request.size"));
    }

    @Test
    public void shouldResolveExternalNameByBindingKind() throws Exception
    {
        OtlpMetricsSerializer serializer = new OtlpMetricsSerializer(
            List.of(),
            List.of(),
            name -> null);

        Field descriptorField = OtlpMetricsSerializer.class.getDeclaredField("descriptor");
        descriptorField.setAccessible(true);
        OtlpMetricsDescriptor descriptor = (OtlpMetricsDescriptor) descriptorField.get(serializer);

        String name = descriptor.nameByBinding("http.request.size", KindConfig.SERVER);

        assertThat(name, equalTo("http.server.request.size"));
    }
}
