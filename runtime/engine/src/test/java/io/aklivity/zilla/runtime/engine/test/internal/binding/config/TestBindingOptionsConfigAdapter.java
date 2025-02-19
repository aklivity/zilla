/*
 * Copyright 2021-2024 Aklivity Inc.
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

import java.util.LinkedList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfigAdapter;
import io.aklivity.zilla.runtime.engine.test.internal.binding.config.TestBindingOptionsConfig.VaultAssertion;

public final class TestBindingOptionsConfigAdapter implements OptionsConfigAdapterSpi
{
    public static final String DEFAULT_ASSERTION_SCHEMA = new String();

    private static final String VALUE_NAME = "value";
    private static final String MODE_NAME = "mode";
    private static final String CATALOG_NAME = "catalog";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String CREDENTIALS_NAME = "credentials";
    private static final String EVENTS_NAME = "events";
    private static final String TIMESTAMP_NAME = "timestamp";
    private static final String MESSAGE_NAME = "message";
    private static final String ASSERTIONS_NAME = "assertions";
    private static final String ID_NAME = "id";
    private static final String SCHEMA_NAME = "schema";
    private static final String DELAY_NAME = "delay";
    private static final String VAULT_NAME = "vault";
    private static final String VAULT_KEY_NAME = "key";
    private static final String VAULT_SIGNER_NAME = "signer";
    private static final String VAULT_TRUST_NAME = "trust";
    private static final String METRICS_NAME = "metrics";
    private static final String NAME_NAME = "name";
    private static final String KIND_NAME = "kind";
    private static final String VALUES_NAME = "values";

    private final ModelConfigAdapter model = new ModelConfigAdapter();

    private final SchemaConfigAdapter schema = new SchemaConfigAdapter();

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return "test";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TestBindingOptionsConfig testOptions = (TestBindingOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (testOptions.value != null)
        {
            object.add(VALUE_NAME, model.adaptToJson(testOptions.value));
        }

        if (testOptions.mode != null)
        {
            object.add(MODE_NAME, testOptions.mode);
        }

        if (testOptions.cataloged != null && !testOptions.cataloged.isEmpty())
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (CatalogedConfig catalog : testOptions.cataloged)
            {
                JsonArrayBuilder array = Json.createArrayBuilder();
                for (SchemaConfig schemaItem: catalog.schemas)
                {
                    array.add(schema.adaptToJson(schemaItem));
                }
                catalogs.add(catalog.name, array);
            }
            object.add(CATALOG_NAME, catalogs);
        }

        if (testOptions.catalogAssertions != null ||
            testOptions.vaultAssertion != null)
        {
            JsonObjectBuilder assertions = Json.createObjectBuilder();

            if (testOptions.catalogAssertions != null)
            {
                JsonObjectBuilder catalogAssertions = Json.createObjectBuilder();
                for (TestBindingOptionsConfig.CatalogAssertions c : testOptions.catalogAssertions)
                {
                    JsonArrayBuilder array = Json.createArrayBuilder();
                    for (TestBindingOptionsConfig.CatalogAssertion a: c.assertions)
                    {
                        JsonObjectBuilder assertion = Json.createObjectBuilder();
                        assertion.add(ID_NAME, a.id);
                        assertion.add(SCHEMA_NAME, a.schema);
                        assertion.add(DELAY_NAME, a.delay);
                        array.add(assertion);
                    }
                    catalogAssertions.add(c.name, array);
                }
                assertions.add(CATALOG_NAME, catalogAssertions);
            }

            if (testOptions.vaultAssertion != null)
            {
                VaultAssertion v = testOptions.vaultAssertion;
                JsonObjectBuilder assertion = Json.createObjectBuilder()
                    .add(VAULT_KEY_NAME, v.key)
                    .add(VAULT_SIGNER_NAME, v.signer)
                    .add(VAULT_TRUST_NAME, v.trust);

                assertions.add(VAULT_NAME, assertion);
            }

            object.add(ASSERTIONS_NAME, assertions);
        }

        if (testOptions.authorization != null)
        {
            JsonObjectBuilder credentials = Json.createObjectBuilder();
            credentials.add(CREDENTIALS_NAME, testOptions.authorization.credentials);
            JsonObjectBuilder authorization = Json.createObjectBuilder();
            authorization.add(testOptions.authorization.name, credentials);
            object.add(AUTHORIZATION_NAME, authorization);
        }

        if (testOptions.events != null)
        {
            JsonArrayBuilder events = Json.createArrayBuilder();
            for (TestBindingOptionsConfig.Event e : testOptions.events)
            {
                JsonObjectBuilder event = Json.createObjectBuilder();
                event.add(TIMESTAMP_NAME, e.timestamp);
                event.add(MESSAGE_NAME, e.message);
                events.add(event);
            }
            object.add(EVENTS_NAME, events);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TestBindingOptionsConfigBuilder<TestBindingOptionsConfig> testOptions = TestBindingOptionsConfig.builder();

        if (object != null)
        {
            if (object.containsKey(VALUE_NAME))
            {
                testOptions.value(model.adaptFromJson(object.get(VALUE_NAME)));
            }

            if (object.containsKey(MODE_NAME))
            {
                testOptions.mode(object.getString(MODE_NAME));
            }

            if (object.containsKey(SCHEMA_NAME))
            {
                testOptions.schema(object.getString(SCHEMA_NAME));
            }

            if (object.containsKey(CATALOG_NAME))
            {
                JsonObject catalogsJson = object.getJsonObject(CATALOG_NAME);
                List<CatalogedConfig> catalogs = new LinkedList<>();
                for (String catalogName: catalogsJson.keySet())
                {
                    JsonArray schemasJson = catalogsJson.getJsonArray(catalogName);
                    List<SchemaConfig> schemas = new LinkedList<>();
                    for (JsonValue item : schemasJson)
                    {
                        JsonObject schemaJson = (JsonObject) item;
                        SchemaConfig schemaElement = schema.adaptFromJson(schemaJson);
                        schemas.add(schemaElement);
                    }
                    catalogs.add(new CatalogedConfig(catalogName, schemas));
                }
                testOptions.catalog(catalogs);
            }

            if (object.containsKey(ASSERTIONS_NAME))
            {
                JsonObject assertionsJson = object.getJsonObject(ASSERTIONS_NAME);

                if (assertionsJson.containsKey(CATALOG_NAME))
                {
                    JsonObject catalogsJson = assertionsJson.getJsonObject(CATALOG_NAME);
                    for (String catalogName: catalogsJson.keySet())
                    {
                        JsonArray catalogAssertionsJson = catalogsJson.getJsonArray(catalogName);
                        List<TestBindingOptionsConfig.CatalogAssertion> catalogAssertions = new LinkedList<>();
                        for (JsonValue assertion : catalogAssertionsJson)
                        {
                            JsonObject c = assertion.asJsonObject();
                            catalogAssertions.add(new TestBindingOptionsConfig.CatalogAssertion(
                                c.containsKey(ID_NAME) ? c.getInt(ID_NAME) : 0,
                                c.containsKey(SCHEMA_NAME) ? !c.isNull(SCHEMA_NAME) ? c.getString(SCHEMA_NAME)
                                    : null : DEFAULT_ASSERTION_SCHEMA,
                                c.containsKey(DELAY_NAME) ? c.getJsonNumber(DELAY_NAME).longValue() : 0L));
                        }
                        testOptions.catalogAssertions(catalogName, catalogAssertions);
                    }
                }

                if (assertionsJson.containsKey(VAULT_NAME))
                {
                    JsonObject vaultJson = assertionsJson.getJsonObject(VAULT_NAME);
                    testOptions.vaultAssertion(new TestBindingOptionsConfig.VaultAssertion(
                        vaultJson.containsKey(VAULT_KEY_NAME) ? vaultJson.getString(VAULT_KEY_NAME) : null,
                        vaultJson.containsKey(VAULT_SIGNER_NAME) ? vaultJson.getString(VAULT_SIGNER_NAME) : null,
                        vaultJson.containsKey(VAULT_TRUST_NAME) ? vaultJson.getString(VAULT_TRUST_NAME) : null));
                }
            }

            if (object.containsKey(AUTHORIZATION_NAME))
            {
                JsonObject authorization = object.getJsonObject(AUTHORIZATION_NAME);
                String name = authorization.keySet().stream().findFirst().orElse(null);
                if (name != null)
                {
                    JsonObject guard = authorization.getJsonObject(name);
                    if (guard.containsKey(CREDENTIALS_NAME))
                    {
                        String credentials = guard.getString(CREDENTIALS_NAME);
                        testOptions.authorization(name, credentials);
                    }
                }
            }

            if (object.containsKey(EVENTS_NAME))
            {
                JsonArray events = object.getJsonArray(EVENTS_NAME);
                for (JsonValue e : events)
                {
                    JsonObject e0 = e.asJsonObject();
                    if (e0.containsKey(TIMESTAMP_NAME) && e0.containsKey(MESSAGE_NAME))
                    {
                        testOptions.event(e0.getInt(TIMESTAMP_NAME), e0.getString(MESSAGE_NAME));
                    }
                }
            }

            if (object.containsKey(METRICS_NAME))
            {
                JsonArray metrics = object.getJsonArray(METRICS_NAME);
                for (JsonValue m : metrics)
                {
                    JsonObject m0 = m.asJsonObject();

                    long[] values = m0.getJsonArray(VALUES_NAME).stream()
                        .mapToLong(v -> ((JsonNumber) v).longValue())
                        .toArray();

                    testOptions.metric(m0.getString(NAME_NAME), m0.getString(KIND_NAME), values);
                }
            }
        }

        return testOptions.build();
    }
}
