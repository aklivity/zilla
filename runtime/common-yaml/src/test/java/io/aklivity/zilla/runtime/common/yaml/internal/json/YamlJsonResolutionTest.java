/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import io.aklivity.zilla.runtime.common.yaml.YamlConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

/**
 * Validates reference resolution and JSON Schema tag coercion through {@link YamlJsonParser} (which is layered
 * on the YamlParser event stream). The differential asserts that the scanner and eager parser paths project
 * identical JSON for every fixture, the eager path being forced by a non-default config.
 */
class YamlJsonResolutionTest
{
    private static final String SUITE_TAG = "data-2022-01-17";

    // PRESERVE_SOURCE forces the eager path (not scanner-eligible) but does not affect JSON projection
    private static final Map<String, Object> FORCE_EAGER = Map.of(YamlConfig.PRESERVE_SOURCE, true);

    @Test
    void shouldExpandBlockAliasToAnchoredObject()
    {
        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:base",
            "START_OBJECT",
            "KEY_NAME:host",
            "VALUE_STRING:localhost",
            "END_OBJECT",
            "KEY_NAME:use",
            "START_OBJECT",
            "KEY_NAME:host",
            "VALUE_STRING:localhost",
            "END_OBJECT",
            "END_OBJECT"), events("base: &b\n  host: localhost\nuse: *b\n", Map.of()));
    }

    @Test
    void shouldExpandScalarAndArrayAliases()
    {
        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:scalar",
            "VALUE_STRING:value",
            "KEY_NAME:list",
            "START_ARRAY",
            "VALUE_STRING:one",
            "VALUE_STRING:two",
            "END_ARRAY",
            "KEY_NAME:scalarAlias",
            "VALUE_STRING:value",
            "KEY_NAME:listAlias",
            "START_ARRAY",
            "VALUE_STRING:one",
            "VALUE_STRING:two",
            "END_ARRAY",
            "END_OBJECT"), events("scalar: &s value\nlist: &l [one, two]\nscalarAlias: *s\nlistAlias: *l\n", Map.of()));
    }

    @Test
    void shouldThrowUnresolvedForDanglingAlias()
    {
        assertThrows(RuntimeException.class, () -> events("use: *missing\n", Map.of()));
    }

    @Test
    void shouldResolveNearestPrecedingAnchorDefinition()
    {
        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:first",
            "VALUE_STRING:Foo",
            "KEY_NAME:second",
            "VALUE_STRING:Foo",
            "KEY_NAME:third",
            "VALUE_STRING:Bar",
            "KEY_NAME:reuse",
            "VALUE_STRING:Bar",
            "END_OBJECT"), events("first: &a Foo\nsecond: *a\nthird: &a Bar\nreuse: *a\n", Map.of()));
    }

    @Test
    void shouldCoerceJsonSchemaTags()
    {
        assertEquals(List.of(
            "START_OBJECT",
            "KEY_NAME:s",
            "VALUE_STRING:42",
            "KEY_NAME:i",
            "VALUE_NUMBER:16",
            "KEY_NAME:f",
            "VALUE_NUMBER:1.5",
            "KEY_NAME:b",
            "VALUE_TRUE",
            "KEY_NAME:n",
            "VALUE_NULL",
            "END_OBJECT"), events("s: !!str 42\ni: !!int \"0x10\"\nf: !!float \"1.5\"\nb: !!bool true\nn: !!null ~\n", Map.of()));
    }

    @Test
    void shouldEngageScannerForEngineConfig()
    {
        // the engine reads zilla.yaml with unique keys enabled; that must use the streaming path
        assertTrue(YamlJsonParser.scannerEligible(Map.of(YamlConfig.FEATURE_UNIQUE_KEYS, true)));
        assertTrue(YamlJsonParser.scannerEligible(Map.of(YamlConfig.FEATURE_NON_SCALAR_KEYS, false)));
        assertTrue(YamlJsonParser.scannerEligible(Map.of()));
        assertTrue(YamlJsonParser.scannerEligible(null));
    }

    @Test
    void shouldNotEngageScannerForSemanticConfig()
    {
        assertFalse(YamlJsonParser.scannerEligible(Map.of(YamlConfig.FEATURE_ANCHORS, false)));
        assertFalse(YamlJsonParser.scannerEligible(Map.of(YamlConfig.FEATURE_NON_SCALAR_KEYS, true)));
        assertFalse(YamlJsonParser.scannerEligible(Map.of(YamlConfig.PRESERVE_SOURCE, true)));
    }

    @TestFactory
    Stream<DynamicTest> shouldMatchEagerProjectionForEveryFixture() throws Exception
    {
        return fixtures()
            .map(path -> DynamicTest.dynamicTest(SUITE_DIR.relativize(path).toString(), () ->
            {
                String text = Files.readString(path.resolve("in.yaml"));
                assertEquals(events(text, FORCE_EAGER), events(text, Map.of()),
                    "scanner projection diverged from eager projection");
            }));
    }

    private static List<String> events(
        String text,
        Map<String, ?> config)
    {
        List<String> events = new ArrayList<>();
        JsonParser parser = YamlJson.createParserFactory(config).createParser(new StringReader(text));
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            String token = event.toString();
            switch (event)
            {
            case KEY_NAME, VALUE_STRING, VALUE_NUMBER -> events.add(token + ":" + parser.getString());
            default -> events.add(token);
            }
        }
        return events;
    }

    private static final Path SUITE_DIR = resolveSuite();

    private static Stream<Path> fixtures() throws IOException
    {
        List<Path> directories = new ArrayList<>();
        Files.find(SUITE_DIR, 3, (p, a) -> a.isRegularFile() && "in.yaml".equals(p.getFileName().toString()))
            .map(Path::getParent)
            .filter(p -> Files.exists(p.resolve("in.json")) && !Files.exists(p.resolve("error")))
            .sorted(Comparator.comparing(p -> SUITE_DIR.relativize(p).toString()))
            .forEach(directories::add);
        return directories.stream();
    }

    private static Path resolveSuite()
    {
        URL resource = YamlJsonResolutionTest.class.getResource("/io/aklivity/zilla/runtime/common/yaml/" + SUITE_TAG);
        if (resource == null)
        {
            throw new IllegalStateException("Missing vendored YAML test suite: " + SUITE_TAG);
        }
        try
        {
            return Path.of(resource.toURI());
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalStateException("Invalid vendored YAML test suite location: " + resource, ex);
        }
    }
}
