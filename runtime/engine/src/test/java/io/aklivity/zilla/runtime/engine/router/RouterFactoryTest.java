/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.router;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class RouterFactoryTest
{
    @Test
    public void shouldDiscoverRegisteredRouterNames() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        assertThat(factory.names(), containsInAnyOrder("engine", "test"));
    }

    @Test
    public void shouldCreateRegisteredRouter() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        Router router = factory.create("test", new Configuration());

        assertNotNull(router);
    }

    @Test
    public void shouldCreateDefaultEngineRouter() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        Properties properties = new Properties();
        properties.put(ENGINE_DIRECTORY.name(), "target/router-tests");
        Router router = factory.create("engine", new EngineConfiguration(properties));

        assertNotNull(router);
    }

    @Test
    public void shouldRejectUnrecognizedRouterNameCitingAvailableNames() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> factory.create("unknown", new Configuration()));

        assertThat(error.getMessage(), containsString("unknown"));
        assertThat(error.getMessage(), containsString("engine"));
        assertThat(error.getMessage(), containsString("test"));
    }

    @Test
    public void shouldResolveLabelIdAndLabelThroughRouterAndRouterContext() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        Properties properties = new Properties();
        properties.put(ENGINE_DIRECTORY.name(), "target/router-tests/resolve");
        Router router = factory.create("engine", new EngineConfiguration(properties));

        RouteableContext context = mock(RouteableContext.class);
        RouterContext routerContext = router.supply(context);

        int labelId = router.supplyLabelId("namespace0");

        assertEquals(labelId, routerContext.supplyLabelId("namespace0"));
        assertEquals("namespace0", routerContext.supplyLabel(labelId));
        assertEquals("namespace0", router.supplyLabel(labelId));
    }

    @Test
    public void shouldNotifyWatchLabelsListenerWhenNewLabelRegistered() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        Properties properties = new Properties();
        properties.put(ENGINE_DIRECTORY.name(), "target/router-tests/watch");
        Router router = factory.create("engine", new EngineConfiguration(properties));

        List<String> notifiedLabels = new ArrayList<>();
        router.watchLabels((label, labelId) -> notifiedLabels.add(label));

        router.supplyLabelId("binding0");

        assertEquals(List.of("binding0"), notifiedLabels);

        router.supplyLabelId("binding0");

        assertEquals(List.of("binding0"), notifiedLabels);
    }

    @Test
    public void shouldBootstrapLabelIdsFromExistingLabelsFile() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        Path directory = Path.of("target/router-tests/bootstrap");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("labels"), "namespace0\nbinding0\n");

        Properties properties = new Properties();
        properties.put(ENGINE_DIRECTORY.name(), directory.toString());
        Router router = factory.create("engine", new EngineConfiguration(properties));

        assertEquals(1, router.supplyLabelId("namespace0"));
        assertEquals(2, router.supplyLabelId("binding0"));
        assertEquals("namespace0", router.supplyLabel(1));
        assertEquals("binding0", router.supplyLabel(2));
    }
}
