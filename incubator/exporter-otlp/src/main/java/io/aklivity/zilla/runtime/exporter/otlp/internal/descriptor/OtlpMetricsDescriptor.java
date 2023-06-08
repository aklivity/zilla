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
package io.aklivity.zilla.runtime.exporter.otlp.internal.descriptor;

import java.util.Map;
import java.util.function.Function;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.exporter.otlp.internal.duplicated.MetricDescriptor;

public class OtlpMetricsDescriptor implements MetricDescriptor
{
    private final Function<String, Metric> resolveMetric;
    Function<String, KindConfig> findBindingKind;
    private final Map<String, String> names;
    private final Map<String, String> kinds;
    private final Map<String, String> descriptions;
    private final Map<String, String> units;

    public OtlpMetricsDescriptor(
        Function<String, Metric> resolveMetric,
        Function<String, KindConfig> findBindingKind)
    {
        this.resolveMetric = resolveMetric;
        this.findBindingKind = findBindingKind;
        this.names = new Object2ObjectHashMap<>();
        this.kinds = new Object2ObjectHashMap<>();
        this.descriptions = new Object2ObjectHashMap<>();
        this.units = new Object2ObjectHashMap<>();
    }

    @Override
    public String kind(
        String internalName)
    {
        String result = kinds.get(internalName);
        if (result == null)
        {
            result = resolveMetric.apply(internalName).kind().toString().toLowerCase();
            kinds.put(internalName, result);
        }
        return result;
    }

    @Override
    public String name(
        String internalName)
    {
        return names.computeIfAbsent(internalName, this::externalName);
    }

    //@Override
    public String nameByBinding(
        String bindingName,
        String internalName)
    {
        KindConfig kind = findBindingKind.apply(bindingName);
        if (kind == KindConfig.SERVER)
        {
            //
        }
        else if (kind == KindConfig.CLIENT)
        {
            //
        }
        return internalName + "_extname_todo";
        //return names.computeIfAbsent(internalName, this::externalName);
    }


    private String externalName(
        String internalName)
    {
        // TODO: Ati
        return internalName + "_extname_todo";
    }

    @Override
    public String description(
        String internalName)
    {
        String result = descriptions.get(internalName);
        if (result == null)
        {
            result = resolveMetric.apply(internalName).description();
            descriptions.put(internalName, result);
        }
        return result;
    }

    public String unit(
        String internalName)
    {
        String result = units.get(internalName);
        if (result == null)
        {
            result = resolveMetric.apply(internalName).unit().toString().toLowerCase();
            units.put(internalName, result);
        }
        return result;
    }
}
