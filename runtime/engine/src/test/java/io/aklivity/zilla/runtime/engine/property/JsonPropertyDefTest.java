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
package io.aklivity.zilla.runtime.engine.property;

import static io.aklivity.zilla.runtime.engine.property.ExampleConfiguration.EXAMPLE_CREATOR_ENTRIES;
import static io.aklivity.zilla.runtime.engine.property.ExampleConfiguration.EXAMPLE_CREATOR_ENTRIES_NAME;
import static io.aklivity.zilla.runtime.engine.property.ExampleConfiguration.EXAMPLE_ENTRIES;
import static io.aklivity.zilla.runtime.engine.property.ExampleConfiguration.EXAMPLE_ENTRIES_NAME;
import static io.aklivity.zilla.runtime.engine.property.ExampleConfiguration.EXAMPLE_LIMIT;
import static io.aklivity.zilla.runtime.engine.property.ExampleConfiguration.EXAMPLE_LIMIT_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.Configuration.ConfigurationDef;
import io.aklivity.zilla.runtime.engine.Configuration.JsonPropertyDef;
import io.aklivity.zilla.runtime.engine.Configuration.PropertyDef;

public final class JsonPropertyDefTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldVerifyConstants()
    {
        assertEquals(EXAMPLE_ENTRIES.name(), EXAMPLE_ENTRIES_NAME);
        assertEquals(EXAMPLE_CREATOR_ENTRIES.name(), EXAMPLE_CREATOR_ENTRIES_NAME);
        assertEquals(EXAMPLE_LIMIT.name(), EXAMPLE_LIMIT_NAME);
    }

    @Test
    public void shouldListProperty()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        JsonPropertyDef<ExampleEntry[]> propertyDef = configDef.property(ExampleEntry[].class, "entries", schema());

        assertTrue(configDef.properties().contains(propertyDef));
        assertEquals("scope.entries", propertyDef.name());
        assertEquals(ExampleEntry[].class, propertyDef.kind());
    }

    @Test
    public void shouldDecodeArrayOfRecords()
    {
        ExampleConfiguration config = configure(EXAMPLE_ENTRIES_NAME,
            "[{\"name\":\"first\",\"mode\":\"FAST\",\"target\":{\"host\":\"localhost\",\"port\":8080}},{\"name\":\"second\"}]");

        ExampleEntry[] entries = config.entries();

        assertArrayEquals(new ExampleEntry[]
        {
            new ExampleEntry("first", ExampleMode.FAST, new ExampleTarget("localhost", 8080)),
            new ExampleEntry("second", null, null)
        }, entries);
    }

    @Test
    public void shouldDecodeArrayOfCreatorClass()
    {
        ExampleConfiguration config = configure(EXAMPLE_CREATOR_ENTRIES_NAME,
            "[{\"name\":\"first\",\"mode\":\"SAFE\"}]");

        ExampleCreatorEntry[] entries = config.creatorEntries();

        assertEquals(1, entries.length);
        assertEquals("first", entries[0].name);
        assertEquals(ExampleMode.SAFE, entries[0].mode);
    }

    @Test
    public void shouldDecodeYamlFlowIdenticallyToJson()
    {
        ExampleEntry[] json = configure(EXAMPLE_ENTRIES_NAME,
            "[{\"name\":\"first\",\"mode\":\"FAST\",\"target\":{\"host\":\"localhost\",\"port\":8080}}]").entries();
        ExampleEntry[] yaml = configure(EXAMPLE_ENTRIES_NAME,
            "[{name: first, mode: FAST, target: {host: localhost, port: 8080}}]").entries();

        assertEquals(1, yaml.length);
        assertArrayEquals(json, yaml);
    }

    @Test
    public void shouldDefaultToNullWhenAbsent()
    {
        ExampleConfiguration config = new ExampleConfiguration(empty());

        assertNull(config.entries());
        assertNull(config.creatorEntries());
    }

    @Test
    public void shouldReportUnknownMemberWithLocation()
    {
        ExampleConfiguration config = configure(EXAMPLE_ENTRIES_NAME,
            "[{\"name\":\"first\"},{\"name\":\"second\",\"other\":true}]");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::entries);

        assertTrue(ex.getMessage(), ex.getMessage().contains(EXAMPLE_ENTRIES_NAME));
        assertTrue(ex.getMessage(), ex.getMessage().contains(EXAMPLE_ENTRIES_NAME + "[1].other"));
    }

    @Test
    public void shouldReportEnumOutOfRangeWithLocation()
    {
        ExampleConfiguration config = configure(EXAMPLE_ENTRIES_NAME,
            "[{\"name\":\"first\"},{\"name\":\"second\",\"mode\":\"SLOW\"}]");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::entries);

        assertTrue(ex.getMessage(), ex.getMessage().contains(EXAMPLE_ENTRIES_NAME + "[1].mode"));
    }

    @Test
    public void shouldReportNestedMemberLocation()
    {
        ExampleConfiguration config = configure(EXAMPLE_ENTRIES_NAME,
            "[{\"name\":\"first\",\"target\":{\"host\":\"localhost\",\"port\":\"http\"}}]");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::entries);

        assertTrue(ex.getMessage(), ex.getMessage().contains(EXAMPLE_ENTRIES_NAME + "[0].target.port"));
    }

    @Test
    public void shouldReportValueThatIsNotJson()
    {
        ExampleConfiguration config = configure(EXAMPLE_ENTRIES_NAME, "[{\"name\":");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::entries);

        assertTrue(ex.getMessage(), ex.getMessage().contains(EXAMPLE_ENTRIES_NAME));
    }

    @Test
    public void shouldRaiseWhenValueIsRead()
    {
        Properties properties = new Properties();
        properties.setProperty(EXAMPLE_ENTRIES_NAME, "[{\"name\":1}]");
        properties.setProperty(EXAMPLE_LIMIT_NAME, "many");

        ExampleConfiguration config = new ExampleConfiguration(new Configuration(properties));

        assertThrows(IllegalArgumentException.class, config::entries);
        assertThrows(NumberFormatException.class, config::limit);
    }

    @Test
    public void shouldSelectStringPropertyForStringDefault()
    {
        ConfigurationDef configDef = new ConfigurationDef("scope");
        PropertyDef<String> propertyDef = configDef.property("name", "default");

        assertEquals("default", propertyDef.get(empty()));
    }

    @Test
    public void shouldReportPackageThatIsNotOpened() throws Exception
    {
        ClassLoader loader = namedModule();
        Class<?> entry = loader.loadClass("owner.Entry");
        @SuppressWarnings("unchecked")
        Class<Object[]> kind = (Class<Object[]>) entry.arrayType();

        ConfigurationDef configDef = new ConfigurationDef("scope");
        JsonPropertyDef<Object[]> propertyDef = configDef.property(kind, "entries", schema());

        Properties properties = new Properties();
        properties.setProperty("scope.entries", "[{\"name\":\"first\"}]");
        Configuration config = new Configuration(properties);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> propertyDef.get(config));

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage(), ex.getMessage().contains("scope.entries"));
        assertTrue(ex.getMessage(), ex.getMessage().contains("package owner of module owner.module must be opened"));
    }

    private static JsonSchema schema()
    {
        return JsonSchema.of(ExampleConfiguration.class, "example-entries.schema.json");
    }

    private static Configuration empty()
    {
        return new Configuration(new Properties());
    }

    private static ExampleConfiguration configure(
        String name,
        String value)
    {
        Properties properties = new Properties();
        properties.setProperty(name, value);

        return new ExampleConfiguration(new Configuration(properties));
    }

    private ClassLoader namedModule() throws IOException
    {
        Path dir = folder.newFolder().toPath();
        Path src = dir.resolve("src");
        Path out = dir.resolve("out").resolve("owner.module");
        Files.createDirectories(src.resolve("owner"));
        Files.createDirectories(out);

        Files.writeString(src.resolve("module-info.java"), "module owner.module { }", UTF_8);
        Files.writeString(src.resolve("owner").resolve("Entry.java"),
            "package owner; public record Entry(String name) { }", UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
            "-d", out.toString(),
            src.resolve("module-info.java").toString(),
            src.resolve("owner").resolve("Entry.java").toString());
        assertEquals(0, result);

        ModuleFinder finder = ModuleFinder.of(out.getParent());
        var configuration = ModuleLayer.boot().configuration()
            .resolve(finder, ModuleFinder.of(), Set.of("owner.module"));
        ModuleLayer layer = ModuleLayer.defineModulesWithOneLoader(
            configuration, List.of(ModuleLayer.boot()), ClassLoader.getSystemClassLoader()).layer();

        return layer.findLoader("owner.module");
    }
}
