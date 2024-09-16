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
package io.aklivity.zilla.runtime.engine.test.internal.binding.config;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class TestBindingOptionsConfig extends OptionsConfig
{
    public final ModelConfig value;
    public final String mode;
    public final TestAuthorizationConfig authorization;
    public final List<CatalogedConfig> cataloged;
    public final List<Event> events;
    public final List<CatalogAssertions> catalogAssertions;
    public final VaultAssertion vaultAssertion;

    public static TestBindingOptionsConfigBuilder<TestBindingOptionsConfig> builder()
    {
        return new TestBindingOptionsConfigBuilder<>(TestBindingOptionsConfig.class::cast);
    }

    public static <T> TestBindingOptionsConfigBuilder<T> builder(
        Function<OptionsConfig, T> mapper)
    {
        return new TestBindingOptionsConfigBuilder<>(mapper);
    }

    TestBindingOptionsConfig(
        ModelConfig value,
        String mode,
        TestAuthorizationConfig authorization,
        List<CatalogedConfig> cataloged,
        List<Event> events,
        List<CatalogAssertions> catalogAssertions,
        VaultAssertion vaultAssertion)
    {
        super(value != null ? List.of(value) : List.of(), List.of());
        this.value = value;
        this.mode = mode;
        this.authorization = authorization;
        this.cataloged = cataloged;
        this.events = events;
        this.catalogAssertions = catalogAssertions;
        this.vaultAssertion = vaultAssertion;
    }

    public static final class Event
    {
        public final long timestamp;
        public final String message;

        public Event(
            long timestamp,
            String message)
        {
            this.timestamp = timestamp;
            this.message = message;
        }
    }

    public static final class VaultAssertion
    {
        public final String key;
        public final String signer;
        public final String trust;

        public VaultAssertion(
            String key,
            String signer,
            String trust)
        {
            this.key = key;
            this.signer = signer;
            this.trust = trust;
        }
    }


    public static final class CatalogAssertions
    {
        public final String name;
        public final List<CatalogAssertion> assertions;

        public CatalogAssertions(
            String name,
            List<CatalogAssertion> assertions)
        {
            this.name = name;
            this.assertions = assertions;
        }
    }

    public static final class CatalogAssertion
    {
        public final int id;
        public final String schema;
        public final long delay;

        public CatalogAssertion(
            int id,
            String schema,
            long delay)
        {
            this.id = id;
            this.schema = schema;
            this.delay = delay;
        }
    }
}
