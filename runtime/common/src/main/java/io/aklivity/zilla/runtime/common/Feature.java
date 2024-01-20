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
package io.aklivity.zilla.runtime.common;

import static java.util.Collections.unmodifiableMap;

import java.lang.module.ModuleDescriptor;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import io.aklivity.zilla.runtime.common.annotation.Incubating;

public abstract class Feature
{
    protected static <S extends FeatureSpi, F> F instantiate(
        Iterable<S> factories,
        Function<Map<String, S>, F> construct)
    {
        Map<String, S> factoriesByName = new TreeMap<>();
        for (S factory : factories)
        {
            if (!factories.getClass().isAnnotationPresent(Incubating.class) ||
                incubatorEnabled())
            {
                factoriesByName.put(factory.name(), factory);
            }
        }

        return construct.apply(unmodifiableMap(factoriesByName));
    }

    private static boolean incubatorEnabled()
    {
        final Module module = Feature.class.getModule();
        final String override = System.getProperty("zilla.incubator.enabled");

        return override != null
            ? Boolean.parseBoolean(override)
            : module == null ||
            "develop-SNAPSHOT".equals(module
                .getDescriptor()
                .version()
                .map(ModuleDescriptor.Version::toString)
                .orElse("develop-SNAPSHOT"));
    }

}
