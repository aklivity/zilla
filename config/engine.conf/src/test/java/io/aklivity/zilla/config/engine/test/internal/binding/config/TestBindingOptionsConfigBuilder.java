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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.CatalogedConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.test.internal.binding.config.TestBindingOptionsConfig.VaultAssertion;

public final class TestBindingOptionsConfigBuilder<T> extends ConfigBuilder<T, TestBindingOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private ModelConfig value;
    private String mode;
    private String schema;
    private TestAuthorizationConfig authorization;
    private List<CatalogedConfig> catalogs;
    private List<TestBindingOptionsConfig.Event> events;
    private List<TestBindingOptionsConfig.Metric> metrics;
    private List<TestBindingOptionsConfig.CatalogAssertions> catalogAssertions;
    private VaultAssertion vaultAssertion;
    private String store;
    private List<TestBindingOptionsConfig.StoreAssertions> storeAssertions;
    private List<TestBindingOptionsConfig.EnvelopeValue> envelope;
    private List<TestBindingOptionsConfig.EnvelopeAssertion> envelopeAssertions;

    TestBindingOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TestBindingOptionsConfigBuilder<T>> thisType()
    {
        return (Class<TestBindingOptionsConfigBuilder<T>>) getClass();
    }

    public TestBindingOptionsConfigBuilder<T> value(
        ModelConfig value)
    {
        this.value = value;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> mode(
        String mode)
    {
        this.mode = mode;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> schema(
        String schema)
    {
        this.schema = schema;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> catalog(
        List<CatalogedConfig> catalogs)
    {
        this.catalogs = catalogs;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> authorization(
        String name,
        String credentials,
        Map<String, String> attributes)
    {
        this.authorization = new TestAuthorizationConfig(name, credentials, attributes);
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> authorization(
        String name,
        String credentials,
        String callback,
        Map<String, String> callbackParams,
        Map<String, String> attributes)
    {
        this.authorization = new TestAuthorizationConfig(
            name, credentials, callback, callbackParams, null, null, attributes);
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> authorization(
        String name,
        String credentials,
        String callback,
        Map<String, String> callbackParams,
        String expectIdentity,
        String expectCredentials,
        Map<String, String> attributes)
    {
        this.authorization = new TestAuthorizationConfig(
            name, credentials, callback, callbackParams, expectIdentity, expectCredentials, attributes);
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> authorization(
        String name,
        String credentials,
        String callback,
        Map<String, String> callbackParams,
        String expectIdentity,
        String expectCredentials,
        Map<String, String> attributes,
        boolean releaseOnEnd)
    {
        this.authorization = new TestAuthorizationConfig(
            name, credentials, callback, callbackParams, expectIdentity, expectCredentials, attributes, releaseOnEnd);
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> event(
        long timestamp,
        String message)
    {
        if (this.events == null)
        {
            this.events = new LinkedList<>();
        }
        this.events.add(new TestBindingOptionsConfig.Event(timestamp, message));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> metric(
        String name,
        String kind,
        long[] values)
    {
        if (this.metrics == null)
        {
            this.metrics = new LinkedList<>();
        }
        this.metrics.add(new TestBindingOptionsConfig.Metric(name, kind, values));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> catalogAssertions(
        String name,
        List<TestBindingOptionsConfig.CatalogAssertion> assertions)
    {
        if (this.catalogAssertions == null)
        {
            this.catalogAssertions = new LinkedList<>();
        }
        this.catalogAssertions.add(new TestBindingOptionsConfig.CatalogAssertions(name, assertions));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> vaultAssertion(
        TestBindingOptionsConfig.VaultAssertion assertion)
    {
        this.vaultAssertion = assertion;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> storeAssertions(
        String name,
        List<TestBindingOptionsConfig.StoreAssertion> assertions)
    {
        if (this.storeAssertions == null)
        {
            this.storeAssertions = new LinkedList<>();
        }
        this.storeAssertions.add(new TestBindingOptionsConfig.StoreAssertions(name, assertions));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> envelope(
        String name,
        String value)
    {
        if (this.envelope == null)
        {
            this.envelope = new LinkedList<>();
        }
        this.envelope.add(new TestBindingOptionsConfig.EnvelopeValue(name, value, null));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> envelopeBytes(
        String name,
        byte[] bytes)
    {
        if (this.envelope == null)
        {
            this.envelope = new LinkedList<>();
        }
        this.envelope.add(new TestBindingOptionsConfig.EnvelopeValue(name, null, bytes));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> envelopeAssertion(
        String name)
    {
        if (this.envelopeAssertions == null)
        {
            this.envelopeAssertions = new LinkedList<>();
        }
        this.envelopeAssertions.add(new TestBindingOptionsConfig.EnvelopeAssertion(name, null, false));
        return this;
    }

    public TestBindingOptionsConfigBuilder<T> envelopeAssertion(
        String name,
        String value)
    {
        if (this.envelopeAssertions == null)
        {
            this.envelopeAssertions = new LinkedList<>();
        }
        this.envelopeAssertions.add(new TestBindingOptionsConfig.EnvelopeAssertion(name, value, true));
        return this;
    }

    @Override
    public T build()
    {
        List<NamedConfig> refs = value != null ? value.refs() : List.of();
        return mapper.apply(new TestBindingOptionsConfig(value, mode, schema, authorization, catalogs, events,
                metrics, catalogAssertions, vaultAssertion, store, storeAssertions, envelope, envelopeAssertions,
                refs));
    }
}
