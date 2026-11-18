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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.Test;

import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.reader.HistogramRecord;
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

    @Test
    public void shouldExportMillisecondHistogramWithExactBucketBounds()
    {
        long[] values = new long[64];
        values[24] = 1L;
        values[63] = 18_000_000L;
        LongSupplier[] readers = Arrays.stream(values).mapToObj(v -> (LongSupplier) () -> v).toArray(LongSupplier[]::new);
        LongFunction<String> labels = id -> id == 8L ? "http.duration" : "label";
        HistogramRecord record = new HistogramRecord(1L << 32 | 7L, 8, 0, -1, readers, labels);
        Metric metric = mock(Metric.class);
        when(metric.kind()).thenReturn(Metric.Kind.HISTOGRAM);
        when(metric.unit()).thenReturn(Metric.Unit.NANOSECONDS);
        when(metric.description()).thenReturn("duration");
        OtlpMetricsSerializer serializer = new OtlpMetricsSerializer(List.of(record), List.of(), name -> metric);

        JsonObject point = Json.createReader(new StringReader(serializer.serializeAll())).readObject()
            .getJsonArray("resourceMetrics").getJsonObject(0)
            .getJsonArray("scopeMetrics").getJsonObject(0)
            .getJsonArray("metrics").getJsonObject(0)
            .getJsonObject("histogram").getJsonArray("dataPoints").getJsonObject(0);

        assertThat(point.getJsonArray("explicitBounds").getJsonNumber(23).doubleValue(), equalTo(16.777216));
        assertThat(point.getJsonArray("explicitBounds").getJsonNumber(24).doubleValue(), equalTo(33.554432));
        assertThat(point.getJsonArray("bucketCounts").getJsonNumber(24).longValue(), equalTo(1L));
        assertThat(point.getJsonNumber("sum").longValue(), equalTo(18L));
    }
}
