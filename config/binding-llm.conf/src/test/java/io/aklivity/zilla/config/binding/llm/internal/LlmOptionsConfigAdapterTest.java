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
package io.aklivity.zilla.config.binding.llm.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmModelConfig;
import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;
import io.aklivity.zilla.config.binding.llm.LlmTestDialectConfig;
import io.aklivity.zilla.config.engine.test.internal.model.config.TestModelConfig;

public class LlmOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new LlmOptionsConfigAdapter(List.of(new LlmTestDialectExtInfo().options())));
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, equalTo("openai"));
    }

    @Test
    public void shouldReadOptionsWithOpenaiResponsesDialect()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai/responses\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, equalTo("openai/responses"));
    }

    @Test
    public void shouldWriteOptionsWithOpenaiResponsesDialect()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai/responses")
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"dialect\":\"openai/responses\"}"));
    }

    @Test
    public void shouldWriteOptions()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai")
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"dialect\":\"openai\"}"));
    }

    @Test
    public void shouldReadOptionsWithoutDialect()
    {
        String text = "{}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, nullValue());
    }

    @Test
    public void shouldWriteOptionsWithoutDialect()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{}"));
    }

    @Test
    public void shouldReadGuardrails()
    {
        String text =
                "{" +
                    "\"guardrails\": {" +
                        "\"gpt-4o\": {" +
                            "\"request\": [" +
                                "{ \"section\": [\"user-text\", \"tool-result\"], \"value\": \"test\" }" +
                            "]" +
                        "}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.guardrails, hasSize(1));
        assertThat(options.guardrails.get(0).name, equalTo("gpt-4o"));
        assertThat(options.guardrails.get(0).request, hasSize(1));
        assertThat(options.guardrails.get(0).request.get(0).sections, contains("user-text", "tool-result"));
        assertThat(options.guardrails.get(0).request.get(0).value, instanceOf(TestModelConfig.class));
        assertThat(options.guardrails.get(0).response, empty());
    }

    @Test
    public void shouldReadGuardrailsWithSectionAsString()
    {
        String text =
                "{" +
                    "\"guardrails\": {" +
                        "\"gpt-4o\": {" +
                            "\"response\": [" +
                                "{ \"section\": \"assistant-text\", \"value\": \"test\" }" +
                            "]" +
                        "}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.guardrails.get(0).request, empty());
        assertThat(options.guardrails.get(0).response, hasSize(1));
        assertThat(options.guardrails.get(0).response.get(0).sections, contains("assistant-text"));
    }

    @Test
    public void shouldWriteGuardrailRequest()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .request()
                    .section("user-text")
                    .section("tool-result")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"guardrails\":{\"gpt-4o\":{\"request\":" +
            "[{\"section\":[\"user-text\",\"tool-result\"],\"value\":\"test\"}]}}}"));
    }

    @Test
    public void shouldWriteGuardrailResponse()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .guardrail()
                .name("gpt-4o")
                .response()
                    .section("assistant-text")
                    .value(TestModelConfig.builder().length(0).build())
                    .build()
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"guardrails\":{\"gpt-4o\":{\"response\":" +
            "[{\"section\":[\"assistant-text\"],\"value\":\"test\"}]}}}"));
    }

    @Test
    public void shouldReadServerOption()
    {
        String text =
                "{" +
                    "\"server\": \"http://localhost:11434\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.scheme, equalTo("http"));
        assertThat(options.server.host, equalTo("localhost"));
        assertThat(options.server.port, equalTo(11434));
        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldWriteServerOption()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .server()
                .scheme("http")
                .host("localhost")
                .port(11434)
                .path("/v1")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"server\":\"http://localhost:11434/v1\"}"));
    }

    @Test
    public void shouldReadOptionsWithoutServer()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server, nullValue());
    }

    @Test
    public void shouldWriteOptionsWithoutServer()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai")
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"dialect\":\"openai\"}"));
    }

    @Test
    public void shouldReadServerOptionWithCustomPath()
    {
        String text =
                "{" +
                    "\"server\": \"http://localhost:11434/aicomp/v1\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.path, equalTo("/aicomp/v1"));
    }

    @Test
    public void shouldWriteServerOptionWithCustomPath()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .server()
                .scheme("http")
                .host("localhost")
                .port(11434)
                .path("/aicomp/v1")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"server\":\"http://localhost:11434/aicomp/v1\"}"));
    }

    @Test
    public void shouldReadDialectAndServerOptions()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"," +
                    "\"server\": \"https://example.com:8080\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, equalTo("openai"));
        assertThat(options.server.scheme, equalTo("https"));
        assertThat(options.server.host, equalTo("example.com"));
        assertThat(options.server.port, equalTo(8080));
        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldReadServerOptionWithRootPathAsDefaultPath()
    {
        String text =
                "{" +
                    "\"server\": \"https://api.openai.com/\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.server.host, equalTo("api.openai.com"));
        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldReadServerOptionWithGeminiDialectDefaultPath()
    {
        String text =
                "{" +
                    "\"dialect\": \"gemini\"," +
                    "\"server\": \"https://generativelanguage.googleapis.com\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.dialect, equalTo("gemini"));
        assertThat(options.server.host, equalTo("generativelanguage.googleapis.com"));
        assertThat(options.server.path, equalTo("/v1beta"));
    }

    @Test
    public void shouldReadServerOptionWithGeminiDialectDefaultPathWhenServerPrecedesDialect()
    {
        String text =
                "{" +
                    "\"server\": \"https://generativelanguage.googleapis.com/\"," +
                    "\"dialect\": \"gemini\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.server.path, equalTo("/v1beta"));
    }

    @Test
    public void shouldReadServerOptionWithGeminiDialectExplicitPath()
    {
        String text =
                "{" +
                    "\"dialect\": \"gemini\"," +
                    "\"server\": \"https://generativelanguage.googleapis.com/v1\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldReadServerOptionWithAnthropicDialectDefaultPath()
    {
        String text =
                "{" +
                    "\"dialect\": \"anthropic\"," +
                    "\"server\": \"https://api.anthropic.com\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.server.path, equalTo("/v1"));
    }

    @Test
    public void shouldReadServerOptionWithTrailingSlashStripped()
    {
        String text =
                "{" +
                    "\"server\": \"http://localhost:11434/aicomp/v1/\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.server.path, equalTo("/aicomp/v1"));
    }

    @Test
    public void shouldReadServerOptionWithDefaultHttpsPort()
    {
        String text =
                "{" +
                    "\"server\": \"https://example.com\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.port, equalTo(443));
    }

    @Test
    public void shouldReadServerOptionWithDefaultHttpPort()
    {
        String text =
                "{" +
                    "\"server\": \"http://example.com\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server.port, equalTo(80));
    }

    @Test
    public void shouldWriteDialectAndServerOptions()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai")
            .server()
                .scheme("https")
                .host("example.com")
                .port(8080)
                .path("/v1")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"dialect\":\"openai\",\"server\":\"https://example.com:8080/v1\"}"));
    }

    @Test
    public void shouldReadOptionsWithAuthorizationDefaultCredentials()
    {
        String text =
                "{" +
                    "\"authorization\": {" +
                        "\"test0\": {}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldReadOptionsWithAuthorizationExplicitCredentials()
    {
        String text =
                "{" +
                    "\"authorization\": {" +
                        "\"test0\": {" +
                            "\"credentials\": \"{credentials}\"" +
                        "}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("test0"));
        assertThat(options.authorization.credentials, equalTo("{credentials}"));
    }

    @Test
    public void shouldReadOptionsWithoutAuthorization()
    {
        String text = "{}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, nullValue());
    }

    @Test
    public void shouldWriteOptionsWithAuthorization()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .authorization()
                .name("test0")
                .credentials("Bearer {credentials}")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{\"authorization\":{\"test0\":{\"credentials\":\"Bearer {credentials}\"}}}"));
    }

    @Test
    public void shouldReadOptionsWithDialectExtension()
    {
        String text =
                "{" +
                    "\"dialect\": \"test-dialect\"," +
                    "\"test-settings\": { \"region\": \"us-east-1\" }" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.dialect, equalTo("test-dialect"));
        LlmTestDialectConfig settings = options.ext(LlmTestDialectExtInfo.OPTIONS_NAME, LlmTestDialectConfig.class);
        assertThat(settings, not(nullValue()));
        assertThat(settings.region, equalTo("us-east-1"));
    }

    @Test
    public void shouldReadOptionsWithoutDialectExtension()
    {
        String text = "{\"dialect\": \"test-dialect\"}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.ext(LlmTestDialectExtInfo.OPTIONS_NAME, LlmTestDialectConfig.class), nullValue());
    }

    @Test
    public void shouldWriteOptionsWithDialectExtension()
    {
        String text =
                "{" +
                    "\"dialect\":\"test-dialect\"," +
                    "\"test-settings\":{\"region\":\"us-east-1\"}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldReadModels()
    {
        String text =
                "{" +
                    "\"dialect\": \"anthropic\"," +
                    "\"models\": {" +
                        "\"claude-opus-4-7\": { \"sampling\": false, \"thinking\": \"adaptive\" }," +
                        "\"claude-3-*\": {" +
                            "\"thinking\": \"enabled\"," +
                            "\"budget\": { \"minimum\": 1024, \"maximum\": 32768 }," +
                            "\"ladder\": { \"low\": 2048, \"high\": 16384 }" +
                        "}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.models, hasSize(2));

        LlmModelConfig opus = options.models.get(0);
        assertThat(opus.name, equalTo("claude-opus-4-7"));
        assertThat(opus.sampling, equalTo(false));
        assertThat(opus.thinking, equalTo("adaptive"));
        assertThat(opus.budgetMinimum, nullValue());
        assertThat(opus.budgetMaximum, nullValue());
        assertThat(opus.ladder, anEmptyMap());

        LlmModelConfig legacy = options.models.get(1);
        assertThat(legacy.name, equalTo("claude-3-*"));
        assertThat(legacy.sampling, nullValue());
        assertThat(legacy.thinking, equalTo("enabled"));
        assertThat(legacy.budgetMinimum, equalTo(1024));
        assertThat(legacy.budgetMaximum, equalTo(32768));
        assertThat(legacy.ladder.get("low"), equalTo(2048));
        assertThat(legacy.ladder.get("high"), equalTo(16384));
    }

    @Test
    public void shouldReadOptionsWithoutModels()
    {
        String text = "{\"dialect\": \"anthropic\"}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.models, empty());
    }

    @Test
    public void shouldWriteModels()
    {
        String text =
                "{" +
                    "\"dialect\":\"gemini\"," +
                    "\"models\":{" +
                        "\"gemini-2.5-pro\":{" +
                            "\"sampling\":false," +
                            "\"thinking\":\"budget\"," +
                            "\"budget\":{\"minimum\":128,\"maximum\":32768}," +
                            "\"ladder\":{\"low\":1024,\"medium\":8192,\"high\":24576}" +
                        "}" +
                    "}" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldBuildModel()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("anthropic")
            .model()
                .name("claude-sonnet-4-6")
                .thinking("adaptive-enabled")
                .build()
            .build();

        assertThat(options.models, hasSize(1));
        assertThat(options.models.get(0).name, equalTo("claude-sonnet-4-6"));
        assertThat(options.models.get(0).thinking, equalTo("adaptive-enabled"));
    }

    @Test
    public void shouldWriteModelBudgetMaximumOnly()
    {
        String text = "{\"dialect\":\"gemini\",\"models\":{\"m\":{\"budget\":{\"maximum\":24576}}}}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.models.get(0).budgetMinimum, nullValue());
        assertThat(options.models.get(0).budgetMaximum, equalTo(24576));
        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldWriteModelBudgetMinimumOnly()
    {
        String text = "{\"dialect\":\"gemini\",\"models\":{\"m\":{\"budget\":{\"minimum\":128}}}}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldAdaptModelWithoutName()
    {
        LlmModelConfig model = new LlmModelConfigAdapter()
            .adaptFromJson(Json.createObjectBuilder().add("sampling", false).build());

        assertThat(model.name, nullValue());
        assertThat(model.sampling, equalTo(false));
    }

    @Test
    public void shouldReadModelTiers()
    {
        String text = "{\"dialect\":\"openai\",\"models\":{\"gpt-5\":{\"tiers\":[\"auto\",\"flex\"]},\"o3*\":{\"tiers\":[]}}}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.models.get(0).tiers, equalTo(List.of("auto", "flex")));
        assertThat(options.models.get(1).tiers, equalTo(List.of()));
        assertThat(jsonb.toJson(options), equalTo(text));
    }

    @Test
    public void shouldReadModelWithoutTiers()
    {
        String text = "{\"dialect\":\"openai\",\"models\":{\"gpt-5\":{\"sampling\":false}}}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options.models.get(0).tiers, nullValue());
        assertThat(jsonb.toJson(options), equalTo(text));
    }
}
