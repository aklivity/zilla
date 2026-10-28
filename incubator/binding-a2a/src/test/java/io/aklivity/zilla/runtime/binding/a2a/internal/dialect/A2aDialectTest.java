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

import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_SEND;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_MESSAGE_STREAM;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_CANCEL;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_GET;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_LIST;
import static io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW.KIND_TASKS_SUBSCRIBE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.function.Consumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCard;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCardParser;
import io.aklivity.zilla.runtime.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class A2aDialectTest
{
    private static final String MESSAGE_V10 =
        "{\"message\":{\"messageId\":\"m-1\",\"role\":\"ROLE_USER\"," +
            "\"parts\":[{\"text\":\"What is the weather in New York?\"}]}}";
    private static final String MESSAGE_V03 =
        "{\"message\":{\"kind\":\"message\",\"messageId\":\"m-1\",\"role\":\"user\"," +
        "\"parts\":[{\"kind\":\"text\",\"text\":\"What is the weather in New York?\"}]}}";

    private static final String CONFIG_V10 =
        "{\"id\":\"c-1\",\"taskId\":\"t-1\",\"url\":\"https://client.example.com/webhook\",\"token\":\"tok-1\"," +
        "\"authentication\":{\"scheme\":\"Bearer\",\"credentials\":\"cred-1\"}}";
    private static final String CONFIG_V03 =
        "{\"taskId\":\"t-1\",\"pushNotificationConfig\":{\"id\":\"c-1\",\"url\":\"https://client.example.com/webhook\"," +
        "\"token\":\"tok-1\",\"authentication\":{\"schemes\":[\"Bearer\"],\"credentials\":\"cred-1\"}}}";

    private final A2aDialects dialects = new A2aDialects();
    private final A2aDialect v10 = dialects.lookup("1.0");
    private final A2aDialect v03 = dialects.lookup("0.3");
    private final MutableDirectBufferEx exBuffer = new UnsafeBufferEx(new byte[1024]);
    private final UnsafeBufferEx input = new UnsafeBufferEx(new byte[65536]);
    private final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[65536]);

    @Test
    public void shouldLookupDialects()
    {
        assertThat(v10.version(), equalTo("1.0"));
        assertThat(v03.version(), equalTo("0.3"));
        assertThat(dialects.lookup("2.0"), equalTo(null));
        assertThat(v10.supports(KIND_TASKS_LIST), is(true));
        assertThat(v03.supports(KIND_TASKS_LIST), is(false));
        assertThat(v03.supports(KIND_MESSAGE_SEND), is(true));
    }

    @Test
    public void shouldEncodeRequestsV10()
    {
        assertThat(encode(v10, KIND_MESSAGE_SEND, b -> b.messageSend(s -> s.contentLength(1)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"SendMessage\",\"params\":"));
        assertThat(encode(v10, KIND_MESSAGE_STREAM, b -> b.messageStream(s -> s.contentLength(1)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"SendStreamingMessage\",\"params\":"));
        assertThat(encode(v10, KIND_TASKS_LIST, b -> b.tasksList(s -> s.contentLength(1)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ListTasks\",\"params\":"));
        assertThat(encode(v10, KIND_PUSH_CONFIG_CREATE, b -> b.pushConfigCreate(s -> s.taskId("t-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"CreateTaskPushNotificationConfig\",\"params\":"));
        assertThat(encode(v10, KIND_TASKS_GET, b -> b.tasksGet(s -> s.taskId("t-1").historyLength(2)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"GetTask\",\"params\":{\"id\":\"t-1\",\"historyLength\":2}}"));
        assertThat(encode(v10, KIND_TASKS_GET, b -> b.tasksGet(s -> s.taskId("t-1")), "acme"),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"GetTask\",\"params\":{\"tenant\":\"acme\",\"id\":\"t-1\"}}"));
        assertThat(encode(v10, KIND_TASKS_CANCEL, b -> b.tasksCancel(s -> s.taskId("t-\"1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"CancelTask\",\"params\":{\"id\":\"t-\\\"1\"}}"));
        assertThat(encode(v10, KIND_TASKS_SUBSCRIBE, b -> b.tasksSubscribe(s -> s.taskId("t-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"SubscribeToTask\",\"params\":{\"id\":\"t-1\"}}"));
        assertThat(encode(v10, KIND_PUSH_CONFIG_GET, b -> b.pushConfigGet(s -> s.taskId("t-1").configId("c-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"GetTaskPushNotificationConfig\"," +
                "\"params\":{\"taskId\":\"t-1\",\"id\":\"c-1\"}}"));
        assertThat(encode(v10, KIND_PUSH_CONFIG_LIST, b -> b.pushConfigList(s -> s.taskId("t-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ListTaskPushNotificationConfigs\"," +
                "\"params\":{\"taskId\":\"t-1\"}}"));
        assertThat(encode(v10, KIND_PUSH_CONFIG_DELETE, b -> b.pushConfigDelete(s -> s.taskId("t-1").configId("c-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"DeleteTaskPushNotificationConfig\"," +
                "\"params\":{\"taskId\":\"t-1\",\"id\":\"c-1\"}}"));
        assertThat(encode(v10, KIND_EXTENDED_AGENT_CARD, b -> b.extendedAgentCard(s -> s.timeout(0)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"GetExtendedAgentCard\"}"));
    }

    @Test
    public void shouldEncodeRequestsV03()
    {
        assertThat(encode(v03, KIND_MESSAGE_SEND, b -> b.messageSend(s -> s.contentLength(1)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/send\",\"params\":"));
        assertThat(encode(v03, KIND_MESSAGE_STREAM, b -> b.messageStream(s -> s.contentLength(1)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"message/stream\",\"params\":"));
        assertThat(encode(v03, KIND_PUSH_CONFIG_CREATE, b -> b.pushConfigCreate(s -> s.taskId("t-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tasks/pushNotificationConfig/set\",\"params\":"));
        assertThat(encode(v03, KIND_TASKS_GET, b -> b.tasksGet(s -> s.taskId("t-1").historyLength(2)), "ignored"),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tasks/get\",\"params\":{\"id\":\"t-1\",\"historyLength\":2}}"));
        assertThat(encode(v03, KIND_TASKS_CANCEL, b -> b.tasksCancel(s -> s.taskId("t-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tasks/cancel\",\"params\":{\"id\":\"t-1\"}}"));
        assertThat(encode(v03, KIND_TASKS_SUBSCRIBE, b -> b.tasksSubscribe(s -> s.taskId("t-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tasks/resubscribe\",\"params\":{\"id\":\"t-1\"}}"));
        assertThat(encode(v03, KIND_PUSH_CONFIG_GET, b -> b.pushConfigGet(s -> s.taskId("t-1").configId("c-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tasks/pushNotificationConfig/get\"," +
                "\"params\":{\"id\":\"t-1\",\"pushNotificationConfigId\":\"c-1\"}}"));
        assertThat(encode(v03, KIND_PUSH_CONFIG_LIST, b -> b.pushConfigList(s -> s.taskId("t-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1," +
                "\"method\":\"tasks/pushNotificationConfig/list\",\"params\":{\"id\":\"t-1\"}}"));
        assertThat(encode(v03, KIND_PUSH_CONFIG_DELETE, b -> b.pushConfigDelete(s -> s.taskId("t-1").configId("c-1")), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tasks/pushNotificationConfig/delete\"," +
                "\"params\":{\"id\":\"t-1\",\"pushNotificationConfigId\":\"c-1\"}}"));
        assertThat(encode(v03, KIND_EXTENDED_AGENT_CARD, b -> b.extendedAgentCard(s -> s.timeout(0)), null),
            equalTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"agent/getAuthenticatedExtendedCard\"}"));
    }

    @Test
    public void shouldTranslateRequestToV03()
    {
        assertThat(v03.translatesRequest(null), is(true));
        assertThat(v10.translatesRequest(null), is(false));
        assertThat(v10.translatesRequest("acme"), is(true));
        assertThat(translateRequest(KIND_MESSAGE_SEND, MESSAGE_V10), equalTo(MESSAGE_V03));
        assertThat(translateRequest(KIND_MESSAGE_STREAM, MESSAGE_V10), equalTo(MESSAGE_V03));
    }

    @Test
    public void shouldTranslateRequestPartsToV03()
    {
        assertThat(translateRequest(KIND_MESSAGE_SEND,
            "{\"message\":{\"messageId\":\"m-1\",\"role\":\"ROLE_AGENT\",\"parts\":[{\"data\":{\"kind\":\"x\"}}," +
            "{\"url\":\"https://x/y\",\"mediaType\":\"image/png\"," +
                "\"filename\":\"y.png\"},{\"raw\":\"AAA=\"},{}]},\"configuration\":" +
            "{\"historyLength\":2}}"),
            equalTo("{\"message\":{\"kind\":\"message\",\"messageId\":\"m-1\",\"role\":\"agent\",\"parts\":[" +
            "{\"kind\":\"data\",\"data\":{\"kind\":\"x\"}}," +
            "{\"kind\":\"file\",\"file\":{\"uri\":\"https://x/y\",\"mimeType\":\"image/png\",\"name\":\"y.png\"}}," +
            "{\"kind\":\"file\",\"file\":{\"bytes\":\"AAA=\"}},{}]},\"configuration\":{\"historyLength\":2}}"));
    }

    @Test
    public void shouldInjectTenantIntoRequestParams()
    {
        assertThat(translateTenant("{\"message\":{\"messageId\":\"m-1\"}}"),
            equalTo("{\"tenant\":\"acme\",\"message\":{\"messageId\":\"m-1\"}}"));
        assertThat(translateTenant("{\"tenant\":\"other\",\"message\":{}}"),
            equalTo("{\"tenant\":\"other\",\"message\":{}}"));
        assertThat(translateTenant("{}"), equalTo("{\"tenant\":\"acme\"}"));
    }

    @Test
    public void shouldTranslatePushConfigCreateRequestToV03()
    {
        assertThat(translateRequest(KIND_PUSH_CONFIG_CREATE,
            "{\"taskId\":\"t-1\",\"configId\":\"c-1\",\"config\":{\"url\":\"https://client.example.com/webhook\"," +
            "\"token\":\"tok-1\",\"authentication\":{\"scheme\":\"Bearer\",\"credentials\":\"cred-1\"}}}"),
            equalTo("{\"taskId\":\"t-1\",\"pushNotificationConfig\":{\"id\":\"c-1\"," +
            "\"url\":\"https://client.example.com/webhook\",\"token\":\"tok-1\"," +
            "\"authentication\":{\"schemes\":[\"Bearer\"],\"credentials\":\"cred-1\"}}}"));
    }

    @Test
    public void shouldTranslateResultsFromV03()
    {
        assertThat(v03.translatesResult(), is(true));
        assertThat(v10.translatesResult(), is(false));
        assertThat(translateResult(KIND_MESSAGE_SEND,
            "{\"kind\":\"message\",\"messageId\":\"m-2\",\"role\":\"agent\"," +
                "\"parts\":[{\"kind\":\"text\",\"text\":\"Partly cloudy\"}]}"),
            equalTo("{\"message\":{\"messageId\":\"m-2\",\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"Partly cloudy\"}]}}"));
        assertThat(translateResult(KIND_MESSAGE_SEND,
            "{\"kind\":\"task\",\"id\":\"t-1\",\"status\":{\"state\":\"input-required\"},\"history\":[{\"role\":\"user\"}]}"),
            equalTo("{\"task\":{\"id\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_INPUT_REQUIRED\"}," +
            "\"history\":[{\"role\":\"ROLE_USER\"}]}}"));
        assertThat(translateResult(KIND_MESSAGE_STREAM,
            "{\"kind\":\"status-update\",\"taskId\":\"t-1\",\"status\":{\"state\":\"working\"},\"final\":false}"),
            equalTo("{\"statusUpdate\":{\"taskId\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}"));
        assertThat(translateResult(KIND_TASKS_SUBSCRIBE,
            "{\"kind\":\"artifact-update\",\"taskId\":\"t-1\",\"artifact\":{\"artifactId\":\"a-1\",\"parts\":" +
            "[{\"kind\":\"data\",\"data\":{\"kind\":\"x\"}}],\"metadata\":{\"kind\":\"y\"}}}"),
            equalTo("{\"artifactUpdate\":{\"taskId\":\"t-1\",\"artifact\":{\"artifactId\":\"a-1\",\"parts\":" +
            "[{\"data\":{\"kind\":\"x\"}}],\"metadata\":{\"kind\":\"y\"}}}}"));
        assertThat(translateResult(KIND_TASKS_GET,
            "{\"kind\":\"task\",\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"working\"}}"),
            equalTo("{\"id\":\"t-1\",\"contextId\":\"c-1\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}"));
        assertThat(translateResult(KIND_TASKS_CANCEL,
            "{\"kind\":\"task\",\"id\":\"t-1\",\"status\":{\"state\":\"canceled\"}}"),
            equalTo("{\"id\":\"t-1\",\"status\":{\"state\":\"TASK_STATE_CANCELED\"}}"));
    }

    @Test
    public void shouldTranslatePushConfigResultsFromV03()
    {
        assertThat(translateResult(KIND_PUSH_CONFIG_CREATE, CONFIG_V03), equalTo(CONFIG_V10));
        assertThat(translateResult(KIND_PUSH_CONFIG_GET, CONFIG_V03), equalTo(CONFIG_V10));
        assertThat(translateResult(KIND_PUSH_CONFIG_LIST, "[" + CONFIG_V03 + "]"), equalTo("{\"configs\":[" + CONFIG_V10 + "]}"));
        assertThat(translateResult(KIND_PUSH_CONFIG_DELETE, "null"), equalTo("null"));
    }

    @Test
    public void shouldTranslatePushConfigResultWithTaskIdAfterConfig()
    {
        assertThat(translateResult(KIND_PUSH_CONFIG_GET,
            "{\"pushNotificationConfig\":{\"id\":\"c-1\",\"url\":\"u\"," +
                "\"authentication\":{\"schemes\":[\"Bearer\",\"Basic\"]}}," +
            "\"taskId\":\"t-1\"}"),
            equalTo("{\"id\":\"c-1\",\"url\":\"u\",\"authentication\":{\"scheme\":\"Bearer\"},\"taskId\":\"t-1\"}"));
    }

    @Test
    public void shouldTranslateExtendedAgentCardFromV03()
    {
        final String card = "{\"name\":\"Weather Agent\",\"url\":\"http://localhost:8080/a2a\"," +
            "\"preferredTransport\":\"JSONRPC\"," +
            "\"protocolVersion\":\"0.3.0\",\"version\":\"1.0.0\",\"skills\":[{\"id\":\"weather\"}]}";
        final A2aAgentCard parsed = parse(card);

        final int limit = v03.translateResult(KIND_EXTENDED_AGENT_CARD, parsed, put(card), 0, card.length(), output, 0);

        assertThat(output.getStringWithoutLengthUtf8(0, limit),
            equalTo("{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:8080/a2a\"," +
            "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"0.3.0\"}]," +
                "\"version\":\"1.0.0\",\"skills\":[{\"id\":\"weather\"}]}"));
    }

    private String encode(
        A2aDialect dialect,
        int kind,
        Consumer<A2aBeginExFW.Builder> mutator,
        String tenant)
    {
        final A2aBeginExFW.Builder builder = new A2aBeginExFW.Builder().wrap(exBuffer, 0, exBuffer.capacity()).typeId(1);
        mutator.accept(builder);
        final A2aBeginExFW beginEx = new A2aBeginExFW().wrap(exBuffer, 0, builder.build().limit());
        assertThat(beginEx.kind(), equalTo(kind));

        final int limit = dialect.encodeRequest(beginEx, tenant, output, 0);
        return output.getStringWithoutLengthUtf8(0, limit);
    }

    private String translateRequest(
        int kind,
        String params)
    {
        final int limit = v03.translateRequest(kind, null, put(params), 0, params.length(), output, 0);
        return output.getStringWithoutLengthUtf8(0, limit);
    }

    private String translateTenant(
        String params)
    {
        final int limit = v10.translateRequest(KIND_MESSAGE_SEND, "acme", put(params), 0, params.length(), output, 0);
        return output.getStringWithoutLengthUtf8(0, limit);
    }

    private String translateResult(
        int kind,
        String result)
    {
        final int limit = v03.translateResult(kind, null, put(result), 0, result.length(), output, 0);
        return output.getStringWithoutLengthUtf8(0, limit);
    }

    private A2aAgentCard parse(
        String card)
    {
        return new A2aAgentCardParser().parse(put(card), 0, card.length());
    }

    private UnsafeBufferEx put(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);
        input.putBytes(0, bytes);
        return input;
    }
}
