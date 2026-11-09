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
package io.aklivity.zilla.specs.binding.agui.a2a.streams.application.agui;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import io.aklivity.k3po.runtime.junit.annotation.Specification;
import io.aklivity.k3po.runtime.junit.rules.K3poRule;

public class AguiA2uiIT
{
    private final K3poRule k3po = new K3poRule()
        .addScriptRoot("app", "io/aklivity/zilla/specs/binding/agui/a2a/streams/application/agui");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "${app}/a2ui.surface/client",
        "${app}/a2ui.surface/server"})
    public void shouldRunA2uiSurface() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.two.surfaces/client",
        "${app}/a2ui.two.surfaces/server"})
    public void shouldRunA2uiTwoSurfaces() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.media.type/client",
        "${app}/a2ui.media.type/server"})
    public void shouldRunA2uiMediaType() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.v08/client",
        "${app}/a2ui.v08/server"})
    public void shouldRunA2uiV08() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.both.uris/client",
        "${app}/a2ui.both.uris/server"})
    public void shouldRunA2uiBothUris() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.no.capabilities/client",
        "${app}/a2ui.no.capabilities/server"})
    public void shouldRunA2uiNoCapabilities() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.not.declared/client",
        "${app}/a2ui.not.declared/server"})
    public void shouldRunA2uiNotDeclared() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.no.surface.id/client",
        "${app}/a2ui.no.surface.id/server"})
    public void shouldRunA2uiNoSurfaceId() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.with.agui/client",
        "${app}/a2ui.with.agui/server"})
    public void shouldRunA2uiWithAgui() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.client.v08/client",
        "${app}/a2ui.client.v08/server"})
    public void shouldRunA2uiClientV08() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.no.overlap/client",
        "${app}/a2ui.no.overlap/server"})
    public void shouldRunA2uiNoOverlap() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.capabilities.no.version/client",
        "${app}/a2ui.capabilities.no.version/server"})
    public void shouldRunA2uiCapabilitiesNoVersion() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
        "${app}/a2ui.data.model.only/client",
        "${app}/a2ui.data.model.only/server"})
    public void shouldRunA2uiDataModelOnly() throws Exception
    {
        k3po.finish();
    }
}
