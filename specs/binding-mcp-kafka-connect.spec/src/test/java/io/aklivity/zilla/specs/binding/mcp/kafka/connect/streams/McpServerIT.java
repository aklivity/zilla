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
package io.aklivity.zilla.specs.binding.mcp.kafka.connect.streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class McpServerIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("mcp", "io/aklivity/zilla/specs/binding/mcp/kafka/connect/streams/mcp");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${mcp}/list.connectors/client",
        "${mcp}/list.connectors/server"})
    public void shouldCallToolListConnectors() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/create.connector/client",
        "${mcp}/create.connector/server"})
    public void shouldCallToolCreateConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.connector/client",
        "${mcp}/describe.connector/server"})
    public void shouldCallToolDescribeConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/delete.connector/client",
        "${mcp}/delete.connector/server"})
    public void shouldCallToolDeleteConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.connector.config/client",
        "${mcp}/describe.connector.config/server"})
    public void shouldCallToolDescribeConnectorConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/update.connector.config/client",
        "${mcp}/update.connector.config/server"})
    public void shouldCallToolUpdateConnectorConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/validate.connector.config/client",
        "${mcp}/validate.connector.config/server"})
    public void shouldCallToolValidateConnectorConfig() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.connector.status/client",
        "${mcp}/describe.connector.status/server"})
    public void shouldCallToolDescribeConnectorStatus() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/restart.connector/client",
        "${mcp}/restart.connector/server"})
    public void shouldCallToolRestartConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/pause.connector/client",
        "${mcp}/pause.connector/server"})
    public void shouldCallToolPauseConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/resume.connector/client",
        "${mcp}/resume.connector/server"})
    public void shouldCallToolResumeConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/stop.connector/client",
        "${mcp}/stop.connector/server"})
    public void shouldCallToolStopConnector() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/list.connector.tasks/client",
        "${mcp}/list.connector.tasks/server"})
    public void shouldCallToolListConnectorTasks() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/restart.connector.task/client",
        "${mcp}/restart.connector.task/server"})
    public void shouldCallToolRestartConnectorTask() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/describe.connector.offsets/client",
        "${mcp}/describe.connector.offsets/server"})
    public void shouldCallToolDescribeConnectorOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/alter.connector.offsets/client",
        "${mcp}/alter.connector.offsets/server"})
    public void shouldCallToolAlterConnectorOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/reset.connector.offsets/client",
        "${mcp}/reset.connector.offsets/server"})
    public void shouldCallToolResetConnectorOffsets() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${mcp}/list.connector.plugins/client",
        "${mcp}/list.connector.plugins/server"})
    public void shouldCallToolListConnectorPlugins() throws Exception
    {
        k3po.finish();
    }
}
