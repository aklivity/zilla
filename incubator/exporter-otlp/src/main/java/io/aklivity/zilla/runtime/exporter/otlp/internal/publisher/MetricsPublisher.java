/*
 * Copyright 2021-2023 Aklivity Inc
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
/*package io.aklivity.zilla.runtime.exporter.otlp.internal.publisher;

import java.util.function.BiFunction;
import java.util.function.Function;

import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.CounterGaugeRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricRecord;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricsProcessor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;

public class MetricsPublisher
{
    private final MetricsProcessor metrics;
    private final Meter meter;
    private final Function<String, String> supplyKind;
    private final BiFunction<String, String, String> supplyName;
    private final Function<String, String> supplyDescription;
    private final Function<String, String> supplyUnit;

    public MetricsPublisher(
        MetricsProcessor metrics,
        Meter meter,
        Function<String, String> supplyKind,
        BiFunction<String, String, String> supplyName,
        Function<String, String> supplyDescription,
        Function<String, String> supplyUnit)
    {
        this.metrics = metrics;
        this.meter = meter;
        this.supplyKind = supplyKind;
        this.supplyName = supplyName;
        this.supplyDescription = supplyDescription;
        this.supplyUnit = supplyUnit;
    }

    public void setup()
    {
        for (MetricRecord record : metrics.getRecords())
        {
            String metricName = record.metricName();
            switch (supplyKind.apply(metricName))
            {
            case "counter":
            {
                CounterGaugeRecord counter = (CounterGaugeRecord) record;
                Attributes attributes = Attributes.of(
                    AttributeKey.stringKey("namespace"), counter.namespaceName(),
                    AttributeKey.stringKey("binding"), counter.bindingName()
                );
                meter
                    .counterBuilder(supplyName.apply(metricName, counter.bindingName()))
                    .setDescription(supplyDescription.apply(metricName))
                    .setUnit(supplyUnit.apply(metricName))
                    .buildWithCallback(m -> m.record(counter.value(), attributes));
                break;
            }
            case "gauge":
            {
                CounterGaugeRecord gauge = (CounterGaugeRecord) record;
                Attributes attributes = Attributes.of(
                    AttributeKey.stringKey("namespace"), gauge.namespaceName(),
                    AttributeKey.stringKey("binding"), gauge.bindingName()
                );
                meter
                    .gaugeBuilder(supplyName.apply(metricName, gauge.bindingName()))
                    .ofLongs()
                    .setDescription(supplyDescription.apply(metricName))
                    .setUnit(supplyUnit.apply(metricName))
                    .buildWithCallback(m -> m.record(gauge.value(), attributes));
                break;
            }
            }
        }
    }
}
*/
