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
package io.aklivity.zilla.runtime.binding.mcp.kafka.connect.internal.stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;
import io.aklivity.zilla.runtime.engine.test.EngineRule;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public class McpKafkaConnectClientIT
{
    private static final String HTTP_CLIENT_EXIT_NAME = "zilla.binding.mcp.http.client.exit";
    private static final String SESSION_ID_NAME = "zilla.binding.mcp.http.session.id";

    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/kafka/connect/streams/mcp")
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/kafka/connect/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final EngineRule engine = new EngineRule()
        .directory("target/zilla-itests")
        .countersBufferCapacity(8192)
        .configure(HTTP_CLIENT_EXIT_NAME, "test:http0")
        .configure(SESSION_ID_NAME, "%s::sessionId".formatted(McpKafkaConnectClientIT.class.getName()))
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/kafka/connect/config")
        .external("http0")
        .clean();

    public static String sessionId(
        long affinity)
    {
        assert affinity == 0L;
        return "5ca1ab1e-c0de-4a11-5e55-000100000000";
    }

    @Rule
    public final TestRule chain = outerRule(engine).around(k3po).around(timeout);

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/tools.list/client"})
    public void shouldListTools() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/list.connectors/client",
        "${http}/list.connectors/server"})
    public void shouldCallToolListConnectors() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/create.connector/client",
        "${http}/create.connector/server"})
    public void shouldCallToolCreateConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/describe.connector/client",
        "${http}/describe.connector/server"})
    public void shouldCallToolDescribeConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/delete.connector/client",
        "${http}/delete.connector/server"})
    public void shouldCallToolDeleteConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/describe.connector.config/client",
        "${http}/describe.connector.config/server"})
    public void shouldCallToolDescribeConnectorConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/update.connector.config/client",
        "${http}/update.connector.config/server"})
    public void shouldCallToolUpdateConnectorConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/validate.connector.config/client",
        "${http}/validate.connector.config/server"})
    public void shouldCallToolValidateConnectorConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/describe.connector.status/client",
        "${http}/describe.connector.status/server"})
    public void shouldCallToolDescribeConnectorStatus() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/restart.connector/client",
        "${http}/restart.connector/server"})
    public void shouldCallToolRestartConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/pause.connector/client",
        "${http}/pause.connector/server"})
    public void shouldCallToolPauseConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/resume.connector/client",
        "${http}/resume.connector/server"})
    public void shouldCallToolResumeConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/stop.connector/client",
        "${http}/stop.connector/server"})
    public void shouldCallToolStopConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/list.connector.tasks/client",
        "${http}/list.connector.tasks/server"})
    public void shouldCallToolListConnectorTasks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/restart.connector.task/client",
        "${http}/restart.connector.task/server"})
    public void shouldCallToolRestartConnectorTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/describe.connector.offsets/client",
        "${http}/describe.connector.offsets/server"})
    public void shouldCallToolDescribeConnectorOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/alter.connector.offsets/client",
        "${http}/alter.connector.offsets/server"})
    public void shouldCallToolAlterConnectorOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/reset.connector.offsets/client",
        "${http}/reset.connector.offsets/server"})
    public void shouldCallToolResetConnectorOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Configuration("proxy.yaml")
    @Specification({
        "${mcp}/list.connector.plugins/client",
        "${http}/list.connector.plugins/server"})
    public void shouldCallToolListConnectorPlugins() throws Exception
    {
        k3po.finish();
    }
}
