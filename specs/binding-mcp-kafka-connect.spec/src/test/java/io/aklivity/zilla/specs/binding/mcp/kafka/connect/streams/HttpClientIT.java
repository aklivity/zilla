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

public class HttpClientIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("http", "io/aklivity/zilla/specs/binding/mcp/kafka/connect/streams/http");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${http}/list.connectors/client",
        "${http}/list.connectors/server"})
    public void shouldProxyListConnectorsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/create.connector/client",
        "${http}/create.connector/server"})
    public void shouldProxyCreateConnectorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/describe.connector/client",
        "${http}/describe.connector/server"})
    public void shouldProxyDescribeConnectorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/delete.connector/client",
        "${http}/delete.connector/server"})
    public void shouldProxyDeleteConnectorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/describe.connector.config/client",
        "${http}/describe.connector.config/server"})
    public void shouldProxyDescribeConnectorConfigToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/update.connector.config/client",
        "${http}/update.connector.config/server"})
    public void shouldProxyUpdateConnectorConfigToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/validate.connector.config/client",
        "${http}/validate.connector.config/server"})
    public void shouldProxyValidateConnectorConfigToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/describe.connector.status/client",
        "${http}/describe.connector.status/server"})
    public void shouldProxyDescribeConnectorStatusToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/restart.connector/client",
        "${http}/restart.connector/server"})
    public void shouldProxyRestartConnectorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/pause.connector/client",
        "${http}/pause.connector/server"})
    public void shouldProxyPauseConnectorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/resume.connector/client",
        "${http}/resume.connector/server"})
    public void shouldProxyResumeConnectorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/stop.connector/client",
        "${http}/stop.connector/server"})
    public void shouldProxyStopConnectorToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/list.connector.tasks/client",
        "${http}/list.connector.tasks/server"})
    public void shouldProxyListConnectorTasksToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/restart.connector.task/client",
        "${http}/restart.connector.task/server"})
    public void shouldProxyRestartConnectorTaskToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/describe.connector.offsets/client",
        "${http}/describe.connector.offsets/server"})
    public void shouldProxyDescribeConnectorOffsetsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/alter.connector.offsets/client",
        "${http}/alter.connector.offsets/server"})
    public void shouldProxyAlterConnectorOffsetsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/reset.connector.offsets/client",
        "${http}/reset.connector.offsets/server"})
    public void shouldProxyResetConnectorOffsetsToHttp() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${http}/list.connector.plugins/client",
        "${http}/list.connector.plugins/server"})
    public void shouldProxyListConnectorPluginsToHttp() throws Exception
    {
        k3po.finish();
    }
}
