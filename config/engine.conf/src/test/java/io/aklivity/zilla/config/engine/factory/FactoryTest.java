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
package io.aklivity.zilla.config.engine.factory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.feature.Incubating;

public class FactoryTest
{
    @Test
    public void shouldInstantiateAllProvidedFactories()
    {
        TestFactorySpi factory = new TestFactorySpi("test");

        List<TestFactorySpi> instances = Factory.instantiate(List.of(factory));

        assertThat(instances, equalTo(List.of(factory)));
    }

    @Test
    public void shouldFilterIdenticallyToMapOverload()
    {
        TestFactorySpi stable = new TestFactorySpi("stable");
        IncubatingTestFactorySpi incubating = new IncubatingTestFactorySpi("incubating");
        List<TestFactorySpi> provided = List.of(stable, incubating);

        List<TestFactorySpi> instances = Factory.instantiate(provided);
        Map<String, TestFactorySpi> instancesByType = new TestFactory().map(provided);

        assertThat(instances.contains(stable), equalTo(instancesByType.containsKey("stable")));
        assertThat(instances.contains(incubating), equalTo(instancesByType.containsKey("incubating")));
    }

    private static final class TestFactory extends Factory
    {
        private Map<String, TestFactorySpi> map(
            Iterable<TestFactorySpi> factories)
        {
            return instantiate(factories, map -> map);
        }
    }

    private static class TestFactorySpi implements FactorySpi
    {
        private final String type;

        private TestFactorySpi(
            String type)
        {
            this.type = type;
        }

        @Override
        public String type()
        {
            return type;
        }
    }

    @Incubating
    private static final class IncubatingTestFactorySpi extends TestFactorySpi
    {
        private IncubatingTestFactorySpi(
            String type)
        {
            super(type);
        }
    }
}
