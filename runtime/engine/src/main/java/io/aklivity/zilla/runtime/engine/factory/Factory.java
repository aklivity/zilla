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
package io.aklivity.zilla.runtime.engine.factory;

import static io.aklivity.zilla.runtime.common.feature.FeatureFilter.filter;
import static java.util.Collections.unmodifiableMap;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public abstract class Factory
{
    protected static <S extends FactorySpi, F> F instantiate(
        Iterable<S> factories,
        Function<Map<String, S>, F> construct)
    {
        Map<String, S> factoriesByType = new TreeMap<>();
        for (S factory : filter(factories))
        {
            factoriesByType.put(factory.type(), factory);

            for (String alias : factory.aliases())
            {
                factoriesByType.put(alias, factory);
            }
        }

        return construct.apply(unmodifiableMap(factoriesByType));
    }
}
