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
package io.aklivity.zilla.config.binding.agui.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Test;

import io.aklivity.zilla.config.engine.EngineConfigReader;
import io.aklivity.zilla.config.engine.EngineInfo;

public class AguiA2aSpecConfigTest
{
    private static final String CONFIG_ROOT = "io/aklivity/zilla/specs/binding/agui/a2a/config";
    private static final String INVALID_SUFFIX = ".invalid.yaml";
    private static final List<String> UNRESOLVED_AT_ATTACH = List.of();

    private final EngineConfigReader reader = new EngineConfigReader(
        text -> text, new EngineInfo(), AguiA2aSpecConfigTest::noop, AguiA2aSpecConfigTest::noop);

    private static void noop(
        String value)
    {
    }

    @Test
    public void shouldFindSpecConfigs() throws Exception
    {
        assertThat(configs(false), not(empty()));
        assertThat(configs(true), not(empty()));
        assertThat(configs(false), hasItem("proxy.yaml"));
    }

    @Test
    public void shouldLoadValidSpecConfigs() throws Exception
    {
        List<String> failures = new ArrayList<>();

        for (String name : configs(false))
        {
            try
            {
                reader.read(read(name));
            }
            catch (RuntimeException ex)
            {
                failures.add(name + ": " + ex);
            }
        }

        assertThat(failures, empty());
    }

    @Test
    public void shouldRejectInvalidSpecConfigs() throws Exception
    {
        List<String> accepted = new ArrayList<>();

        for (String name : configs(true))
        {
            try
            {
                reader.read(read(name));
                accepted.add(name);
            }
            catch (RuntimeException ex)
            {
                // expected
            }
        }

        assertThat(accepted, equalTo(UNRESOLVED_AT_ATTACH));
    }

    private static String read(
        String name) throws IOException
    {
        try (var input = AguiA2aSpecConfigTest.class.getClassLoader().getResourceAsStream(CONFIG_ROOT + "/" + name))
        {
            return new String(input.readAllBytes());
        }
    }

    private static List<String> configs(
        boolean invalid) throws IOException, URISyntaxException
    {
        URL root = AguiA2aSpecConfigTest.class.getClassLoader().getResource(CONFIG_ROOT);
        URI uri = root.toURI();

        List<String> names;
        if ("jar".equals(uri.getScheme()))
        {
            try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of()))
            {
                names = list(jar.getPath(CONFIG_ROOT), invalid);
            }
        }
        else
        {
            names = list(Paths.get(uri), invalid);
        }

        return names;
    }

    private static List<String> list(
        Path directory,
        boolean invalid) throws IOException
    {
        try (Stream<Path> files = Files.list(directory))
        {
            return files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".yaml"))
                .filter(name -> name.endsWith(INVALID_SUFFIX) == invalid)
                .sorted()
                .toList();
        }
    }
}
