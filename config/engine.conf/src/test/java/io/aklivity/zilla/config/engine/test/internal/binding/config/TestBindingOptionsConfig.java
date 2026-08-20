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
package io.aklivity.zilla.config.engine.test.internal.binding.config;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class TestBindingOptionsConfig extends OptionsConfig
{
    public final ModelConfig value;
    public final String mode;
    public final String schema;
    public final TestAuthorizationConfig authorization;
    public final List<CatalogedConfig> cataloged;
    public final List<Event> events;
    public final List<Metric> metrics;
    public final List<CatalogAssertions> catalogAssertions;
    public final VaultAssertion vaultAssertion;
    public final String store;
    public final List<StoreAssertions> storeAssertions;
    public final List<EnvelopeValue> envelope;
    public final List<EnvelopeAssertion> envelopeAssertions;

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
        String schema,
        TestAuthorizationConfig authorization,
        List<CatalogedConfig> cataloged,
        List<Event> events,
        List<Metric> metrics,
        List<CatalogAssertions> catalogAssertions,
        VaultAssertion vaultAssertion,
        String store,
        List<StoreAssertions> storeAssertions,
        List<EnvelopeValue> envelope,
        List<EnvelopeAssertion> envelopeAssertions)
    {
        super(value != null ? List.of(value) : List.of(), List.of());
        this.value = value;
        this.mode = mode;
        this.schema = schema;
        this.authorization = authorization;
        this.cataloged = cataloged;
        this.events = events;
        this.metrics = metrics;
        this.catalogAssertions = catalogAssertions;
        this.vaultAssertion = vaultAssertion;
        this.store = store;
        this.storeAssertions = storeAssertions;
        this.envelope = envelope;
        this.envelopeAssertions = envelopeAssertions;
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

    public static final class Metric
    {
        public final String name;
        public final String kind;
        public final long[] values;

        public Metric(
            String name,
            String kind,
            long[] values)
        {
            this.name = name;
            this.kind = kind;
            this.values = values;
        }
    }

    public static final class VaultAssertion
    {
        public final String key;
        public final String signer;
        public final String trust;
        public final boolean trustcacerts;

        public VaultAssertion(
            String key,
            String signer,
            String trust,
            boolean trustcacerts)
        {
            this.key = key;
            this.signer = signer;
            this.trust = trust;
            this.trustcacerts = trustcacerts;
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

    public static final class StoreAssertions
    {
        public final String name;
        public final List<StoreAssertion> assertions;

        public StoreAssertions(
            String name,
            List<StoreAssertion> assertions)
        {
            this.name = name;
            this.assertions = assertions;
        }
    }

    public static final class StoreAssertion
    {
        public final String op;
        public final String key;
        public final String value;
        public final Duration ttl;
        public final String expect;
        public final boolean hasExpect;
        public final long delay;

        public StoreAssertion(
            String op,
            String key,
            String value,
            Duration ttl,
            String expect,
            boolean hasExpect,
            long delay)
        {
            this.op = op;
            this.key = key;
            this.value = value;
            this.ttl = ttl;
            this.expect = expect;
            this.hasExpect = hasExpect;
            this.delay = delay;
        }
    }

    // Seeds a value model's ModelEnvelope before its transform runs, so a decode-direction
    // transform (e.g. decrypting a field) can be exercised against known out-of-value metadata
    // without first driving an encode-direction transform to produce it.
    public static final class EnvelopeValue
    {
        public final String name;
        public final String value;
        public final byte[] bytes;

        public EnvelopeValue(
            String name,
            String value,
            byte[] bytes)
        {
            this.name = name;
            this.value = value;
            this.bytes = bytes;
        }
    }

    // Asserts a value model's ModelEnvelope contents once its transform completes, so an
    // encode-direction transform (e.g. encrypting a field) can be verified without depending on
    // literal (and non-deterministic, e.g. randomly-IV'd) wire bytes.
    public static final class EnvelopeAssertion
    {
        public final String name;
        public final String value;
        public final boolean hasValue;

        public EnvelopeAssertion(
            String name,
            String value,
            boolean hasValue)
        {
            this.name = name;
            this.value = value;
            this.hasValue = hasValue;
        }
    }
}
