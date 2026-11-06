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
package io.aklivity.zilla.config.binding.a2a.openapi.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiCatalogConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiSkillConfig;
import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aOpenapiOptionsConfigAdapterTest
{
    private static final String YAML =
        """
        agent:
          name: Petstore
          description: Pets and owners
          version: 2.0.0
        authorization:
          guard0:
            credentials:
              headers:
                authorization: "Bearer {credentials}"
        specs:
          petstore:
            server: https://api.petstore.example.com
            catalog:
              catalog0:
                subject: petstore
                version: latest
                overlay:
                  catalog1:
                    subject: petstore-overlay
                    version: latest
            security:
              bearerAuth: authn_jwt
          store:
            catalog:
              catalog2:
                subject: store
        skills:
          list_pets:
            name: List pets
            description: Lists every pet
            examples:
              - what pets are available
            tags:
              - pets
            input:
              model: test
            output:
              model: test
          get_pet:
            examples:
              - show me pet 7
        embedding: embedding0
        store: vectors
        threshold: 0.9
        """;

    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new A2aOpenapiOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        A2aOpenapiOptionsConfig options = read(YAML);

        assertThat(options, not(nullValue()));
        assertThat(options.embedding, equalTo("embedding0"));
        assertThat(options.store, equalTo("vectors"));
        assertThat(options.threshold, equalTo(0.9));
        assertThat(options.agent.name, equalTo("Petstore"));
        assertThat(options.agent.description, equalTo("Pets and owners"));
        assertThat(options.agent.version, equalTo("2.0.0"));
        assertThat(options.authorization.name, equalTo("guard0"));
        assertThat(options.authorization.headers.get("authorization"), equalTo("Bearer {credentials}"));

        assertThat(options.specs, hasSize(2));
        A2aOpenapiSpecificationConfig petstore = options.specs.get(0);
        assertThat(petstore.label, equalTo("petstore"));
        assertThat(petstore.server, equalTo("https://api.petstore.example.com"));
        assertThat(petstore.security, equalTo(Map.of("bearerAuth", "authn_jwt")));
        A2aOpenapiCatalogConfig catalog = petstore.catalogs.get(0);
        assertThat(catalog.name, equalTo("catalog0"));
        assertThat(catalog.subject, equalTo("petstore"));
        assertThat(catalog.version, equalTo("latest"));
        assertThat(catalog.overlay.name, equalTo("catalog1"));
        assertThat(catalog.overlay.schema.subject, equalTo("petstore-overlay"));
        assertThat(options.specs.get(1).server, nullValue());
        assertThat(options.specs.get(1).security, nullValue());
        assertThat(options.specs.get(1).catalogs.get(0).overlay, nullValue());

        assertThat(options.skills, hasSize(2));
        A2aOpenapiSkillConfig listPets = options.skills.get(0);
        assertThat(listPets.id, equalTo("list_pets"));
        assertThat(listPets.name, equalTo("List pets"));
        assertThat(listPets.description, equalTo("Lists every pet"));
        assertThat(listPets.examples, contains("what pets are available"));
        assertThat(listPets.tags, contains("pets"));
        assertThat(listPets.input, not(nullValue()));
        assertThat(listPets.output, not(nullValue()));
        A2aOpenapiSkillConfig getPet = options.skills.get(1);
        assertThat(getPet.name, nullValue());
        assertThat(getPet.description, nullValue());
        assertThat(getPet.tags, nullValue());
        assertThat(getPet.input, nullValue());
        assertThat(getPet.output, nullValue());
    }

    @Test
    public void shouldDefaultThresholdAndOptionalSections()
    {
        A2aOpenapiOptionsConfig options = read(
            """
            specs:
              petstore:
                catalog:
                  catalog0:
                    subject: petstore
            embedding: embedding0
            store: vectors
            """);

        assertThat(options.threshold, equalTo(A2aOpenapiOptionsConfig.THRESHOLD_DEFAULT));
        assertThat(options.agent, nullValue());
        assertThat(options.authorization, nullValue());
        assertThat(options.skills, hasSize(0));
    }

    @Test
    public void shouldWriteOptions()
    {
        String text = jsonb.toJson(read(YAML));

        assertThat(text, containsString("embedding: embedding0"));
        assertThat(text, containsString("store: vectors"));
        assertThat(text, containsString("threshold: 0.9"));
        assertThat(text, containsString("name: Petstore"));
        assertThat(text, containsString("bearerAuth: authn_jwt"));
        assertThat(text, containsString("list_pets:"));
        assertThat(text, containsString("guard0:"));
    }

    @Test
    public void shouldRoundTripOptions()
    {
        A2aOpenapiOptionsConfig options = read(YAML);
        A2aOpenapiOptionsConfig again = read(jsonb.toJson(options));

        assertThat(again.embedding, equalTo(options.embedding));
        assertThat(again.store, equalTo(options.store));
        assertThat(again.threshold, equalTo(options.threshold));
        assertThat(again.agent.name, equalTo(options.agent.name));
        assertThat(again.authorization.name, equalTo(options.authorization.name));
        assertThat(again.specs, hasSize(2));
        assertThat(again.specs.get(0).security, equalTo(options.specs.get(0).security));
        assertThat(again.specs.get(0).catalogs.get(0).overlay.name, equalTo("catalog1"));
        assertThat(again.skills, hasSize(2));
        assertThat(again.skills.get(0).examples, equalTo(options.skills.get(0).examples));
        assertThat(again.skills.get(0).input, not(nullValue()));
        assertThat(again.skills.get(1).output, nullValue());
    }

    private A2aOpenapiOptionsConfig read(
        String yaml)
    {
        return jsonb.fromJson(yaml, A2aOpenapiOptionsConfig.class);
    }
}
