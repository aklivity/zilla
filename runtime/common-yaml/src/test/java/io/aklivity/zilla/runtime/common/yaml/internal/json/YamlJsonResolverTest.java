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
import io.aklivity.zilla.runtime.common.yaml.internal.YamlStreamScanner;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

/**
 * Validates the streaming reference resolver. White-box tests pin the resolved event buffer it builds
 * from the raw scanner; the differential asserts that wherever the streaming path engages, the JSON it
 * projects is identical to the eager path (forced by a non-default config, which disables the scanner).
 */
class YamlJsonResolverTest
{
    private static final String SUITE_TAG = "data-2022-01-17";

    // PRESERVE_SOURCE forces the eager path (not scanner-eligible) but does not affect JSON projection
    private static final Map<String, Object> FORCE_EAGER = Map.of(YamlConfig.PRESERVE_SOURCE, true);

    @Test
    void shouldExpandBlockAliasToAnchoredObject()
    {
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan("""
            base: &b
              host: localhost
            use: *b
            """, true));
        assertTrue(scanner.hasReferences());

        YamlJsonResolver resolver = new YamlJsonResolver(scanner);
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
            "END_OBJECT"), project(resolver));
    }

    @Test
    void shouldExpandScalarAndArrayAliases()
    {
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan("""
            scalar: &s value
            list: &l [one, two]
            scalarAlias: *s
            listAlias: *l
            """, true));

        YamlJsonResolver resolver = new YamlJsonResolver(scanner);
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
            "END_OBJECT"), project(resolver));
    }

    @Test
    void shouldThrowUnresolvedForDanglingAlias()
    {
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan("use: *missing\n", true));
        assertThrows(RuntimeException.class, () -> new YamlJsonResolver(scanner));
    }

    @Test
    void shouldCoerceJsonSchemaTags()
    {
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan("""
            s: !!str 42
            i: !!int "0x10"
            f: !!float "1.5"
            b: !!bool true
            n: !!null ~
            """, true));

        YamlJsonResolver resolver = new YamlJsonResolver(scanner);
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
            "END_OBJECT"), project(resolver));
    }

    @Test
    void shouldBailToEagerOnContainerTagMismatch()
    {
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan("x: !!seq foo\n", true));
        assertThrows(YamlJsonResolver.Unsupported.class, () -> new YamlJsonResolver(scanner));
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
                    "streaming projection diverged from eager projection");
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

    private static List<String> project(
        YamlJsonResolver resolver)
    {
        List<String> events = new ArrayList<>();
        for (int index = 0; index < resolver.count(); index++)
        {
            String token = switch (resolver.kind(index))
            {
            case YamlStreamScanner.START_OBJECT -> "START_OBJECT";
            case YamlStreamScanner.END_OBJECT -> "END_OBJECT";
            case YamlStreamScanner.START_ARRAY -> "START_ARRAY";
            case YamlStreamScanner.END_ARRAY -> "END_ARRAY";
            case YamlStreamScanner.KEY_NAME -> "KEY_NAME:" + resolver.value(index);
            case YamlStreamScanner.VALUE_STRING -> "VALUE_STRING:" + resolver.value(index);
            case YamlStreamScanner.VALUE_NUMBER -> "VALUE_NUMBER:" + resolver.value(index);
            case YamlStreamScanner.VALUE_TRUE -> "VALUE_TRUE";
            case YamlStreamScanner.VALUE_FALSE -> "VALUE_FALSE";
            case YamlStreamScanner.VALUE_NULL -> "VALUE_NULL";
            default -> throw new IllegalStateException("Unexpected kind: " + resolver.kind(index));
            };
            events.add(token);
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
        URL resource = YamlJsonResolverTest.class.getResource("/io/aklivity/zilla/runtime/common/yaml/" + SUITE_TAG);
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
