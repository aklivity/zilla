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
package io.aklivity.zilla.config.binding.a2a.http.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.http.A2aHttpSkillConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aHttpOptionsConfigAdapterTest
{
    private static final String YAML =
        """
        embedding: embedding0
        store: store0
        threshold: 0.9
        card:
          name: items
          description: Items agent
          version: 2.0.0
          defaultInputModes:
            - text/plain
          defaultOutputModes:
            - application/json
        authorization:
          guard0:
            credentials:
              headers:
                authorization: "Bearer {credentials}"
        skills:
          get_item:
            name: Get item
            description: Look up an item by id
            examples:
              - get item 42
              - show me the details of item 7
            tags:
              - items
            schemas:
              input:
                model: test
              output:
                model: test
          ping:
            description: Check liveness
            schemas:
              input:
                model: test
        """;

    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new A2aHttpOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        A2aHttpOptionsConfig options = jsonb.fromJson(YAML, A2aHttpOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.embedding, equalTo("embedding0"));
        assertThat(options.store, equalTo("store0"));
        assertThat(options.threshold, equalTo(0.9));
        assertThat(options.card.name, equalTo("items"));
        assertThat(options.card.description, equalTo("Items agent"));
        assertThat(options.card.version, equalTo("2.0.0"));
        assertThat(options.card.defaultInputModes, contains("text/plain"));
        assertThat(options.card.defaultOutputModes, contains("application/json"));
        assertThat(options.authorization.name, equalTo("guard0"));
        assertThat(options.authorization.headers.get("authorization"), equalTo("Bearer {credentials}"));

        assertThat(options.skills, hasSize(2));
        A2aHttpSkillConfig skill = options.skills.get(0);
        assertThat(skill.id, equalTo("get_item"));
        assertThat(skill.name, equalTo("Get item"));
        assertThat(skill.description, equalTo("Look up an item by id"));
        assertThat(skill.examples, contains("get item 42", "show me the details of item 7"));
        assertThat(skill.tags, contains("items"));
        assertThat(skill.input, not(nullValue()));
        assertThat(skill.output, not(nullValue()));

        A2aHttpSkillConfig ping = options.skills.get(1);
        assertThat(ping.id, equalTo("ping"));
        assertThat(ping.name, equalTo("ping"));
        assertThat(ping.examples, hasSize(0));
        assertThat(ping.tags, hasSize(0));
        assertThat(ping.output, nullValue());
    }

    @Test
    public void shouldDefaultThresholdAndCard()
    {
        String yaml =
            """
            embedding: embedding0
            store: store0
            """;

        A2aHttpOptionsConfig options = jsonb.fromJson(yaml, A2aHttpOptionsConfig.class);

        assertThat(options.threshold, equalTo(A2aHttpOptionsConfig.THRESHOLD_DEFAULT));
        assertThat(options.threshold, equalTo(0.8));
        assertThat(options.card, nullValue());
        assertThat(options.authorization, nullValue());
        assertThat(options.skills, hasSize(0));
    }

    @Test
    public void shouldWriteOptions()
    {
        A2aHttpOptionsConfig options = jsonb.fromJson(YAML, A2aHttpOptionsConfig.class);

        String text = jsonb.toJson(options);

        assertThat(text, containsString("embedding: embedding0"));
        assertThat(text, containsString("store: store0"));
        assertThat(text, containsString("threshold: 0.9"));
        assertThat(text, containsString("get_item:"));
        assertThat(text, containsString("Look up an item by id"));
        assertThat(text, containsString("guard0:"));
    }

    @Test
    public void shouldRoundTripOptions()
    {
        A2aHttpOptionsConfig options = jsonb.fromJson(YAML, A2aHttpOptionsConfig.class);
        A2aHttpOptionsConfig again = jsonb.fromJson(jsonb.toJson(options), A2aHttpOptionsConfig.class);

        assertThat(again.embedding, equalTo(options.embedding));
        assertThat(again.store, equalTo(options.store));
        assertThat(again.threshold, equalTo(options.threshold));
        assertThat(again.card.name, equalTo(options.card.name));
        assertThat(again.skills, hasSize(2));
        assertThat(again.skills.get(0).id, equalTo("get_item"));
        assertThat(again.skills.get(0).examples, equalTo(options.skills.get(0).examples));
        assertThat(again.skills.get(1).output, nullValue());
    }
}
