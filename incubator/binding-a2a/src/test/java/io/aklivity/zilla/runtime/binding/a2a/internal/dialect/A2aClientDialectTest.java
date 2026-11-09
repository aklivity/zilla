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
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aCardRoles;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class A2aClientDialectTest
{
    private static final String MESSAGE_V10 =
        "{\"message\":{\"messageId\":\"m-1\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\"hello\"}]}}";
    private static final String MESSAGE_V03 =
        "{\"message\":{\"kind\":\"message\",\"messageId\":\"m-1\",\"role\":\"user\"," +
        "\"parts\":[{\"kind\":\"text\",\"text\":\"hello\"}]}}";
    private static final String CONFIG_V10 =
        "{\"id\":\"c-1\",\"taskId\":\"t-1\",\"url\":\"https://x/hook\"," +
        "\"authentication\":{\"scheme\":\"Bearer\",\"credentials\":\"c\"}}";
    private static final String CONFIG_V03 =
        "{\"taskId\":\"t-1\",\"pushNotificationConfig\":{\"id\":\"c-1\",\"url\":\"https://x/hook\"," +
        "\"authentication\":{\"schemes\":[\"Bearer\"],\"credentials\":\"c\"}}}";

    private final A2aDialect v10 = new A2aV10Dialect();
    private final A2aDialect v03 = new A2aV03Dialect();

    @Test
    public void shouldEncodeRequestEnvelope()
    {
        final UnsafeBufferEx buffer = new UnsafeBufferEx(new byte[256]);
        final A2aRequestEncoder encoder = new A2aRequestEncoder().wrap(buffer, 0);

        encoder.method("SendMessage").params().raw("{}").end();

        assertThat(buffer.getStringWithoutLengthUtf8(0, encoder.limit()),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"SendMessage\",\"params\":{}}"));
    }

    @Test
    public void shouldComposeV10RequestParams()
    {
        assertThat(v10.requestParams(A2aOperation.TASKS_GET, new A2aRequestParams("t-1", null, null, 2, null), null).toString(),
            equalTo("{\"id\":\"t-1\",\"historyLength\":2}"));
        assertThat(v10.requestParams(A2aOperation.TASKS_GET, new A2aRequestParams("t-1", null, null, -1, null),
            "acme").toString(),
            equalTo("{\"tenant\":\"acme\",\"id\":\"t-1\"}"));
        assertThat(v10.requestParams(A2aOperation.TASKS_CANCEL, new A2aRequestParams("t-1", null, null, -1, null),
            null).toString(),
            equalTo("{\"id\":\"t-1\"}"));
        assertThat(v10.requestParams(A2aOperation.TASKS_SUBSCRIBE, new A2aRequestParams("t-1", null, null, -1, null), null)
            .toString(), equalTo("{\"id\":\"t-1\"}"));
        assertThat(v10.requestParams(A2aOperation.PUSH_CONFIG_LIST, new A2aRequestParams("t-1", null, null, -1, null), null)
            .toString(), equalTo("{\"taskId\":\"t-1\"}"));
        assertThat(v10.requestParams(A2aOperation.PUSH_CONFIG_GET, new A2aRequestParams("t-1", "c-1", null, -1, null), null)
            .toString(), equalTo("{\"taskId\":\"t-1\",\"id\":\"c-1\"}"));
        assertThat(v10.requestParams(A2aOperation.PUSH_CONFIG_DELETE, new A2aRequestParams("t-1", "c-1", null, -1, null), null)
            .toString(), equalTo("{\"taskId\":\"t-1\",\"id\":\"c-1\"}"));
        assertThat(v10.requestParams(A2aOperation.EXTENDED_AGENT_CARD, new A2aRequestParams(null, null, null, -1, null), null),
            nullValue());
        assertThat(v10.requestParams(A2aOperation.EXTENDED_AGENT_CARD, new A2aRequestParams(null, null, null, -1, null), "acme")
            .toString(), equalTo("{\"tenant\":\"acme\"}"));
    }

    @Test
    public void shouldInjectTenantUnlessDeclared()
    {
        assertThat(v10.requestParams(A2aOperation.MESSAGE_SEND, new A2aRequestParams(null, null, null, -1, json(MESSAGE_V10)),
            "acme").toString(), equalTo("{\"tenant\":\"acme\"," + MESSAGE_V10.substring(1)));
        assertThat(v10.requestParams(A2aOperation.MESSAGE_SEND,
            new A2aRequestParams(null, null, null, -1, json("{\"tenant\":\"other\",\"message\":{}}")), "acme").toString(),
            equalTo("{\"tenant\":\"other\",\"message\":{}}"));
    }

    @Test
    public void shouldComposeV03RequestParams()
    {
        assertThat(v03.requestParams(A2aOperation.TASKS_GET, new A2aRequestParams("t-1", null, null, 2, null), "ignored")
            .toString(), equalTo("{\"id\":\"t-1\",\"historyLength\":2}"));
        assertThat(v03.requestParams(A2aOperation.TASKS_CANCEL, new A2aRequestParams("t-1", null, null, -1, null), null)
            .toString(), equalTo("{\"id\":\"t-1\"}"));
        assertThat(v03.requestParams(A2aOperation.PUSH_CONFIG_LIST, new A2aRequestParams("t-1", null, null, -1, null), null)
            .toString(), equalTo("{\"id\":\"t-1\"}"));
        assertThat(v03.requestParams(A2aOperation.PUSH_CONFIG_GET, new A2aRequestParams("t-1", "c-1", null, -1, null), null)
            .toString(), equalTo("{\"id\":\"t-1\",\"pushNotificationConfigId\":\"c-1\"}"));
        assertThat(v03.requestParams(A2aOperation.PUSH_CONFIG_DELETE, new A2aRequestParams("t-1", "c-1", null, -1, null), null)
            .toString(), equalTo("{\"id\":\"t-1\",\"pushNotificationConfigId\":\"c-1\"}"));
        assertThat(v03.requestParams(A2aOperation.EXTENDED_AGENT_CARD, new A2aRequestParams(null, null, null, -1, null), null),
            nullValue());
    }

    @Test
    public void shouldTranslateRequestBodiesToV03()
    {
        assertThat(v03.requestParams(A2aOperation.MESSAGE_SEND, new A2aRequestParams(null, null, null, -1, json(MESSAGE_V10)),
            null).toString(), equalTo(MESSAGE_V03));
        assertThat(v03.requestParams(A2aOperation.PUSH_CONFIG_CREATE, new A2aRequestParams(null, null, null, -1,
            json("{\"taskId\":\"t-1\",\"configId\":\"c-1\",\"config\":{\"url\":\"https://x/hook\"," +
            "\"authentication\":{\"scheme\":\"Bearer\",\"credentials\":\"c\"}}}")), null).toString(),
            equalTo("{\"taskId\":\"t-1\",\"pushNotificationConfig\":{\"id\":\"c-1\",\"url\":\"https://x/hook\"," +
            "\"authentication\":{\"schemes\":[\"Bearer\"],\"credentials\":\"c\"}}}"));
    }

    @Test
    public void shouldRoundTripRequestThroughServerDirection()
    {
        final JsonObject wire = v03.requestParams(A2aOperation.MESSAGE_SEND,
            new A2aRequestParams(null, null, null, -1, json(MESSAGE_V10)), null);

        assertThat(v03.request(A2aOperation.MESSAGE_SEND, wire).body().toString(), equalTo(MESSAGE_V10));
    }

    @Test
    public void shouldTranslateResultsFromV03()
    {
        assertThat(v03.resultFromWire(A2aOperation.MESSAGE_SEND, json(
            "{\"kind\":\"message\",\"messageId\":\"m-2\",\"role\":\"agent\",\"parts\":[{\"kind\":\"text\",\"text\":\"ok\"}]}"))
            .toString(), equalTo("{\"message\":{\"messageId\":\"m-2\",\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"ok\"}]}}"));
        assertThat(v03.resultFromWire(A2aOperation.MESSAGE_SEND, json(
            "{\"kind\":\"task\",\"id\":\"t-1\",\"status\":{\"state\":\"input-required\",\"message\":{\"role\":\"agent\"}}," +
            "\"artifacts\":[{\"artifactId\":\"a-1\",\"parts\":[{\"kind\":\"text\",\"text\":\"x\"}]}]," +
            "\"history\":[{\"role\":\"user\"}]}")).toString(),
            equalTo("{\"task\":{\"id\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_INPUT_REQUIRED\"," +
            "\"message\":{\"role\":\"ROLE_AGENT\"}},\"artifacts\":[{\"artifactId\":\"a-1\",\"parts\":[{\"text\":\"x\"}]}]," +
            "\"history\":[{\"role\":\"ROLE_USER\"}]}}"));
        assertThat(v03.resultFromWire(A2aOperation.MESSAGE_STREAM, json(
            "{\"kind\":\"status-update\",\"taskId\":\"t-1\",\"status\":{\"state\":\"working\"},\"final\":false}")).toString(),
            equalTo("{\"statusUpdate\":{\"taskId\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}"));
        assertThat(v03.resultFromWire(A2aOperation.TASKS_SUBSCRIBE, json(
            "{\"kind\":\"artifact-update\",\"taskId\":\"t-1\",\"artifact\":{\"artifactId\":\"a-1\",\"parts\":" +
            "[{\"kind\":\"data\",\"data\":{\"kind\":\"x\"}}]}}")).toString(),
            equalTo("{\"artifactUpdate\":{\"taskId\":\"t-1\",\"artifact\":{\"artifactId\":\"a-1\",\"parts\":" +
            "[{\"data\":{\"kind\":\"x\"}}]}}}"));
        assertThat(v03.resultFromWire(A2aOperation.TASKS_GET, json(
            "{\"kind\":\"task\",\"id\":\"t-1\",\"status\":{\"state\":\"working\"}}")).toString(),
            equalTo("{\"id\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}"));
        assertThat(v03.resultFromWire(A2aOperation.TASKS_CANCEL, JsonValue.NULL), equalTo(JsonValue.NULL));
    }

    @Test
    public void shouldTranslatePushConfigResultsFromV03()
    {
        assertThat(v03.resultFromWire(A2aOperation.PUSH_CONFIG_CREATE, json(CONFIG_V03)).toString(), equalTo(CONFIG_V10));
        assertThat(v03.resultFromWire(A2aOperation.PUSH_CONFIG_GET, json(CONFIG_V03)).toString(), equalTo(CONFIG_V10));
        assertThat(v03.resultFromWire(A2aOperation.PUSH_CONFIG_LIST, value("[" + CONFIG_V03 + "]")).toString(),
            equalTo("{\"configs\":[" + CONFIG_V10 + "]}"));
        assertThat(v03.resultFromWire(A2aOperation.PUSH_CONFIG_DELETE, JsonValue.NULL), equalTo(JsonValue.NULL));
    }

    @Test
    public void shouldPassV10ResultsThrough()
    {
        assertThat(v10.resultFromWire(A2aOperation.MESSAGE_SEND, json(MESSAGE_V10)).toString(), equalTo(MESSAGE_V10));
        assertThat(v10.cardFromWire(json("{\"a\":1}")).toString(), equalTo("{\"a\":1}"));
    }

    @Test
    public void shouldTranslateLegacyCard()
    {
        assertThat(v03.cardFromWire(json("{\"name\":\"W\",\"url\":\"http://x/a2a\",\"preferredTransport\":\"JSONRPC\"," +
            "\"protocolVersion\":\"0.3.0\",\"additionalInterfaces\":[{\"url\":\"http://x:81\",\"transport\":\"GRPC\"}]," +
            "\"version\":\"1\",\"skills\":[{\"id\":\"weather\",\"security\":[{\"oauth2\":[\"a\",\"b\"]}]}]}")).toString(),
            equalTo("{\"name\":\"W\",\"supportedInterfaces\":[{\"url\":\"http://x/a2a\",\"protocolBinding\":\"JSONRPC\"," +
            "\"protocolVersion\":\"0.3.0\"},{\"url\":\"http://x:81\",\"protocolBinding\":\"GRPC\"," +
            "\"protocolVersion\":\"0.3.0\"}],\"version\":\"1\",\"skills\":[{\"id\":\"weather\"," +
            "\"securityRequirements\":[{\"schemes\":{\"oauth2\":{\"list\":[\"a\",\"b\"]}}}]}]}"));
    }

    @Test
    public void shouldInjectRolesIntoCardSkills()
    {
        final JsonObject card = json("{\"skills\":[{\"id\":\"weather\"},{\"id\":\"forecast\"}," +
            "{\"id\":\"secured\",\"securityRequirements\":[{\"schemes\":{\"basic\":{\"list\":[]}}}]}," +
            "\"ignored\"]}").asJsonObject();

        assertThat(A2aCardRoles.inject(card, skill -> "weather".equals(skill) ? List.of() : List.of("agents:" + skill))
            .toString(),
            equalTo("{\"skills\":[{\"id\":\"weather\"},{\"id\":\"forecast\",\"securityRequirements\":" +
            "[{\"schemes\":{\"oauth2\":{\"list\":[\"agents:forecast\"]}}}]},{\"id\":\"secured\",\"securityRequirements\":" +
            "[{\"schemes\":{\"basic\":{\"list\":[]}}},{\"schemes\":{\"oauth2\":{\"list\":[\"agents:secured\"]}}}]}," +
            "\"ignored\"]}"));
    }

    private static JsonValue value(
        String text)
    {
        return Json.createReader(new StringReader(text)).readValue();
    }

    private static JsonObject json(
        String text)
    {
        return Json.createReader(new StringReader(text)).read() instanceof JsonObject object ? object : null;
    }
}
