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

import org.junit.Test;

public class A2aV10DialectTest
{
    private final A2aV10Dialect dialect = new A2aV10Dialect();

    @Test
    public void shouldResolveMethodNames()
    {
        assertThat(dialect.operation("SendMessage"), equalTo(A2aOperation.MESSAGE_SEND));
        assertThat(dialect.operation("ListTasks"), equalTo(A2aOperation.TASKS_LIST));
        assertThat(dialect.operation("message/send"), nullValue());
        assertThat(dialect.verbatim(), equalTo(true));
        assertThat(dialect.version(), equalTo("1.0"));
    }

    @Test
    public void shouldRouteRequests()
    {
        final A2aRequestParams get = dialect.request(A2aOperation.TASKS_GET, object("{\"id\":\"t-1\",\"historyLength\":2}"));
        final A2aRequestParams push = dialect.request(A2aOperation.PUSH_CONFIG_DELETE,
            object("{\"taskId\":\"t-1\",\"id\":\"c-1\"}"));
        final A2aRequestParams list = dialect.request(A2aOperation.TASKS_LIST, object("{\"contextId\":\"c-1\"}"));
        final A2aRequestParams card = dialect.request(A2aOperation.EXTENDED_AGENT_CARD, null);

        assertThat(get.taskId(), equalTo("t-1"));
        assertThat(get.historyLength(), equalTo(2));
        assertThat(push.configId(), equalTo("c-1"));
        assertThat(list.contextId(), equalTo("c-1"));
        assertThat(card.taskId(), nullValue());
        assertThat(dialect.request(A2aOperation.PUSH_CONFIG_GET, object("{\"taskId\":\"t-1\"}")), nullValue());
        assertThat(dialect.request(A2aOperation.TASKS_CANCEL, object("{}")), nullValue());
    }

    @Test
    public void shouldRewriteCard()
    {
        final JsonObject card = dialect.card(object(
            "{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:9090/a2a\"," +
            "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"},{\"url\":\"grpc://x\",\"protocolBinding\":\"GRPC\"}]," +
            "\"version\":\"1.0.0\",\"signatures\":[{}],\"securitySchemes\":{\"x\":{}},\"securityRequirements\":[]}"),
            "http://localhost:8080/a2a", false);

        assertThat(card.toString(), equalTo(
            "{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:8080/a2a\"," +
            "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}],\"version\":\"1.0.0\"}"));
    }

    @Test
    public void shouldDeclareInterfaceWhenCardHasNone()
    {
        final JsonObject card = dialect.card(object("{\"name\":\"Weather Agent\"}"), "http://localhost:8080/a2a", true);

        assertThat(card.toString(), equalTo(
            "{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:8080/a2a\"," +
            "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}]," +
            "\"securitySchemes\":{\"bearer\":{\"httpAuthSecurityScheme\":{\"scheme\":\"Bearer\"}}}," +
            "\"securityRequirements\":[{\"schemes\":{\"bearer\":{\"list\":[]}}}]}"));
    }

    private static JsonObject object(
        String json)
    {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
