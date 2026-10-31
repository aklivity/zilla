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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.junit.Test;

public class A2aV03DialectTest
{
    private static final String MESSAGE_V03 =
        "{\"message\":{\"kind\":\"message\",\"messageId\":\"m-1\",\"role\":\"user\"," +
        "\"parts\":[{\"kind\":\"text\",\"text\":\"What is the weather in New York?\"}]}}";
    private static final String MESSAGE_V10 =
        "{\"message\":{\"messageId\":\"m-1\",\"role\":\"ROLE_USER\"," +
        "\"parts\":[{\"text\":\"What is the weather in New York?\"}]}}";
    private static final String PUSH_CREATE_V03 =
        "{\"taskId\":\"t-1\",\"pushNotificationConfig\":{\"id\":\"c-1\",\"url\":\"https://client.example.com/webhook\"," +
        "\"token\":\"tok-1\",\"authentication\":{\"schemes\":[\"Bearer\"],\"credentials\":\"cred-1\"}}}";
    private static final String PUSH_CREATE_V10 =
        "{\"taskId\":\"t-1\",\"configId\":\"c-1\",\"config\":{\"url\":\"https://client.example.com/webhook\"," +
        "\"token\":\"tok-1\",\"authentication\":{\"scheme\":\"Bearer\",\"credentials\":\"cred-1\"}}}";
    private static final String PUSH_CONFIG_V10 =
        "{\"id\":\"c-1\",\"taskId\":\"t-1\",\"url\":\"https://client.example.com/webhook\",\"token\":\"tok-1\"," +
        "\"authentication\":{\"scheme\":\"Bearer\",\"credentials\":\"cred-1\"}}";
    private static final String PUSH_CONFIG_V03 =
        "{\"taskId\":\"t-1\",\"pushNotificationConfig\":{\"id\":\"c-1\",\"url\":\"https://client.example.com/webhook\"," +
        "\"token\":\"tok-1\",\"authentication\":{\"schemes\":[\"Bearer\"],\"credentials\":\"cred-1\"}}}";

    private final A2aV03Dialect dialect = new A2aV03Dialect();

    @Test
    public void shouldResolveLegacyMethodNames()
    {
        assertThat(dialect.operation("message/send"), equalTo(A2aOperation.MESSAGE_SEND));
        assertThat(dialect.operation("tasks/resubscribe"), equalTo(A2aOperation.TASKS_SUBSCRIBE));
        assertThat(dialect.operation("SendMessage"), nullValue());
        assertThat(dialect.operation("tasks/list"), nullValue());
    }

    @Test
    public void shouldTranslateMessageRequestToCanonical()
    {
        final A2aRequestParams request = dialect.request(A2aOperation.MESSAGE_SEND, object(MESSAGE_V03));

        assertThat(request.body().toString(), equalTo(MESSAGE_V10));
    }

    @Test
    public void shouldTranslatePushConfigCreateRequestToCanonical()
    {
        final A2aRequestParams request = dialect.request(A2aOperation.PUSH_CONFIG_CREATE, object(PUSH_CREATE_V03));

        assertThat(request.taskId(), equalTo("t-1"));
        assertThat(request.configId(), equalTo("c-1"));
        assertThat(request.body().toString(), equalTo(PUSH_CREATE_V10));
    }

    @Test
    public void shouldRouteTaskRequests()
    {
        final A2aRequestParams get = dialect.request(A2aOperation.TASKS_GET, object("{\"id\":\"t-1\",\"historyLength\":2}"));
        final A2aRequestParams push = dialect.request(A2aOperation.PUSH_CONFIG_GET,
            object("{\"id\":\"t-1\",\"pushNotificationConfigId\":\"c-1\"}"));

        assertThat(get.taskId(), equalTo("t-1"));
        assertThat(get.historyLength(), equalTo(2));
        assertThat(push.taskId(), equalTo("t-1"));
        assertThat(push.configId(), equalTo("c-1"));
        assertThat(dialect.request(A2aOperation.TASKS_GET, object("{}")), nullValue());
    }

    @Test
    public void shouldTranslateTaskResult()
    {
        final JsonValue result = dialect.result(A2aOperation.TASKS_GET,
            object("{\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}"));

        assertThat(result.toString(),
            equalTo("{\"kind\":\"task\",\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"working\"}}"));
    }

    @Test
    public void shouldTranslateMessageResult()
    {
        final JsonValue result = dialect.result(A2aOperation.MESSAGE_SEND,
            object("{\"message\":{\"messageId\":\"m-2\",\"role\":\"ROLE_AGENT\"," +
                "\"parts\":[{\"text\":\"Partly cloudy, 72F\"}]}}"));

        assertThat(result.toString(), equalTo(
            "{\"kind\":\"message\",\"messageId\":\"m-2\",\"role\":\"agent\"," +
            "\"parts\":[{\"kind\":\"text\",\"text\":\"Partly cloudy, 72F\"}]}"));
    }

    @Test
    public void shouldTranslateTaskWithArtifactsResult()
    {
        final JsonValue result = dialect.result(A2aOperation.MESSAGE_SEND, object(
            "{\"task\":{\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}," +
            "\"artifacts\":[{\"artifactId\":\"a-1\",\"parts\":[{\"text\":\"Partly cloudy, 72F\"}]}]}}"));

        assertThat(result.toString(), equalTo(
            "{\"kind\":\"task\",\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"completed\"}," +
            "\"artifacts\":[{\"artifactId\":\"a-1\",\"parts\":[{\"kind\":\"text\",\"text\":\"Partly cloudy, 72F\"}]}]}"));
    }

    @Test
    public void shouldTranslateStatusUpdateResult()
    {
        final JsonValue working = dialect.result(A2aOperation.MESSAGE_STREAM, object(
            "{\"statusUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}"));
        final JsonValue completed = dialect.result(A2aOperation.TASKS_SUBSCRIBE, object(
            "{\"statusUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}}}"));

        assertThat(working.toString(), equalTo(
            "{\"kind\":\"status-update\",\"taskId\":\"t-1\",\"contextId\":\"c-1\"," +
            "\"status\":{\"state\":\"working\"},\"final\":false}"));
        assertThat(completed.toString(), equalTo(
            "{\"kind\":\"status-update\",\"taskId\":\"t-1\",\"contextId\":\"c-1\"," +
            "\"status\":{\"state\":\"completed\"},\"final\":true}"));
    }

    @Test
    public void shouldTranslateArtifactUpdateResult()
    {
        final JsonValue result = dialect.result(A2aOperation.MESSAGE_STREAM, object(
            "{\"artifactUpdate\":{\"taskId\":\"t-1\",\"contextId\":\"c-1\"," +
            "\"artifact\":{\"artifactId\":\"a-1\",\"parts\":[{\"text\":\"Partly cloudy, 72F\"}]}}}"));

        assertThat(result.toString(), equalTo(
            "{\"kind\":\"artifact-update\",\"taskId\":\"t-1\",\"contextId\":\"c-1\"," +
            "\"artifact\":{\"artifactId\":\"a-1\",\"parts\":[{\"kind\":\"text\",\"text\":\"Partly cloudy, 72F\"}]}}"));
    }

    @Test
    public void shouldTranslatePushConfigResults()
    {
        final JsonValue config = dialect.result(A2aOperation.PUSH_CONFIG_GET, object(PUSH_CONFIG_V10));
        final JsonValue list = dialect.result(A2aOperation.PUSH_CONFIG_LIST,
            object("{\"configs\":[" + PUSH_CONFIG_V10 + "]}"));

        assertThat(config.toString(), equalTo(PUSH_CONFIG_V03));
        assertThat(list.toString(), equalTo("[" + PUSH_CONFIG_V03 + "]"));
    }

    @Test
    public void shouldTranslateFileAndDataParts()
    {
        final String file03 = "{\"message\":{\"kind\":\"message\",\"messageId\":\"m-1\",\"role\":\"user\",\"parts\":" +
            "[{\"kind\":\"file\",\"file\":{\"name\":\"a.txt\",\"mimeType\":\"text/plain\",\"bytes\":\"YQ==\"}}," +
            "{\"kind\":\"data\",\"data\":{\"k\":1}}]}}";

        final A2aRequestParams request = dialect.request(A2aOperation.MESSAGE_SEND, object(file03));

        assertThat(request.body().toString(), equalTo(
            "{\"message\":{\"messageId\":\"m-1\",\"role\":\"ROLE_USER\",\"parts\":" +
            "[{\"filename\":\"a.txt\",\"mediaType\":\"text/plain\",\"raw\":\"YQ==\"},{\"data\":{\"k\":1}}]}}"));

        final JsonValue back = dialect.result(A2aOperation.MESSAGE_SEND, object(
            "{\"message\":{\"messageId\":\"m-1\",\"role\":\"ROLE_USER\",\"parts\":" +
            "[{\"filename\":\"a.txt\",\"mediaType\":\"text/plain\",\"raw\":\"YQ==\"},{\"data\":{\"k\":1}}]}}"));

        assertThat(back.toString(), equalTo(
            "{\"kind\":\"message\",\"messageId\":\"m-1\",\"role\":\"user\",\"parts\":" +
            "[{\"kind\":\"file\",\"file\":{\"name\":\"a.txt\",\"mimeType\":\"text/plain\",\"bytes\":\"YQ==\"}}," +
            "{\"kind\":\"data\",\"data\":{\"k\":1}}]}"));
    }

    @Test
    public void shouldRoundTripMessageIdentity()
    {
        final JsonObject canonical = (JsonObject) dialect.request(A2aOperation.MESSAGE_SEND, object(MESSAGE_V03)).body();
        final JsonValue legacy = dialect.result(A2aOperation.MESSAGE_SEND, canonical);

        assertThat(legacy.toString(), equalTo(
            "{\"kind\":\"message\",\"messageId\":\"m-1\",\"role\":\"user\"," +
            "\"parts\":[{\"kind\":\"text\",\"text\":\"What is the weather in New York?\"}]}"));
        assertThat(object(MESSAGE_V03).getJsonObject("message"), equalTo(legacy));
    }

    @Test
    public void shouldRoundTripPushConfigIdentity()
    {
        final JsonObject canonical = (JsonObject) dialect.request(A2aOperation.PUSH_CONFIG_CREATE,
            object(PUSH_CREATE_V03)).body();
        final JsonObjectBuilder builder = Json.createObjectBuilder()
            .add("id", canonical.getString("configId"))
            .add("taskId", canonical.getString("taskId"));
        canonical.getJsonObject("config").forEach(builder::add);
        final JsonObject config = builder.build();

        assertThat(config.toString(), equalTo(PUSH_CONFIG_V10));
        assertThat(dialect.result(A2aOperation.PUSH_CONFIG_CREATE, config).toString(), equalTo(PUSH_CONFIG_V03));
    }

    @Test
    public void shouldRewriteCardForLegacyClients()
    {
        final JsonObject card = dialect.card(object(
            "{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:9090/a2a\"," +
            "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}],\"version\":\"1.0.0\",\"signatures\":[{}]}"),
            "http://localhost:8080/a2a", false);

        assertThat(card.toString(), equalTo(
            "{\"name\":\"Weather Agent\",\"url\":\"http://localhost:8080/a2a\",\"preferredTransport\":\"JSONRPC\"," +
            "\"protocolVersion\":\"0.3.0\",\"version\":\"1.0.0\"}"));
    }

    @Test
    public void shouldAdvertiseGuardSchemeForLegacyClients()
    {
        final JsonObject card = dialect.card(object("{\"name\":\"Weather Agent\"}"), "http://localhost:8080/a2a", true);

        assertThat(card.toString(), equalTo(
            "{\"name\":\"Weather Agent\",\"url\":\"http://localhost:8080/a2a\",\"preferredTransport\":\"JSONRPC\"," +
            "\"protocolVersion\":\"0.3.0\",\"securitySchemes\":{\"bearer\":{\"type\":\"http\",\"scheme\":\"bearer\"}}," +
            "\"security\":[{\"bearer\":[]}]}"));
    }

    private static JsonObject object(
        String json)
    {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
