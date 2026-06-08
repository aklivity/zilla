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
package io.aklivity.zilla.runtime.common.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

final class YamlConformanceTest
{
    private static final String ENABLED_PROPERTY = "zilla.yaml.conformance";
    private static final String STRICT_PROPERTY = "zilla.yaml.conformance.strict";
    private static final String SUITE_TAG = "data-2022-01-17";
    private static final URI SUITE_ARCHIVE = URI.create(
        "https://codeload.github.com/yaml/yaml-test-suite/zip/refs/tags/" + SUITE_TAG);
    private static final Path SUITE_DIR = Path.of("target", "yaml-test-suite", SUITE_TAG);
    private static final Path SUITE_MARKER = SUITE_DIR.resolve(".complete");
    private static final JsonProvider YAML_JSON = YamlJson.provider();

    private static final Set<String> JSON_PROJECTION_GAPS = Set.of();
    private static final Set<String> INVALID_REJECTION_GAPS = Set.of();

    @TestFactory
    Stream<DynamicTest> shouldProjectYamlTestSuiteJsonCases() throws Exception
    {
        assumeTrue(Boolean.getBoolean(ENABLED_PROPERTY), enableMessage());

        return cases()
            .filter(c -> c.has("in.json") && !c.has("error"))
            .map(c -> DynamicTest.dynamicTest(c.displayName(), () ->
            {
                assumeTrue(isStrict() || !JSON_PROJECTION_GAPS.contains(c.id),
                    "known common-yaml JSON projection gap");

                List<JsonValue> expected = readJson(c.path.resolve("in.json"));
                List<JsonValue> actual = readYamlAsJson(c.path.resolve("in.yaml"));
                assertEquals(expected, actual, c.id);
            }));
    }

    @TestFactory
    Stream<DynamicTest> shouldRejectYamlTestSuiteInvalidCases() throws Exception
    {
        assumeTrue(Boolean.getBoolean(ENABLED_PROPERTY), enableMessage());

        return cases()
            .filter(c -> c.has("error"))
            .map(c -> DynamicTest.dynamicTest(c.displayName(), () ->
            {
                assumeTrue(isStrict() || !INVALID_REJECTION_GAPS.contains(c.id),
                    "known common-yaml invalid rejection gap");

                String yaml = Files.readString(c.path.resolve("in.yaml"));
                assertThrows(RuntimeException.class, () ->
                {
                    JsonParser parser = YamlJson.createParser(new StringReader(yaml));
                    while (parser.hasNext())
                    {
                        parser.next();
                    }
                }, c.id);
            }));
    }

    private static Stream<Case> cases() throws IOException
    {
        ensureSuite();

        return Files.find(SUITE_DIR, 3, (p, a) -> a.isRegularFile() && "in.yaml".equals(p.getFileName().toString()))
            .map(Path::getParent)
            .map(Case::new)
            .sorted(Comparator.comparing(c -> c.id));
    }

    private static List<JsonValue> readJson(
        Path path) throws IOException
    {
        List<JsonValue> values = new ArrayList<>();
        for (String value : splitJsonValues(Files.readString(path)))
        {
            values.add(YAML_JSON.createReader(new StringReader(value)).readValue());
        }
        return values;
    }

    private static List<JsonValue> readYamlAsJson(
        Path path) throws IOException
    {
        String text = Files.readString(path);
        List<JsonValue> values = new ArrayList<>();
        if (isEmptyYamlStream(text))
        {
            return values;
        }
        JsonParser parser = YAML_JSON.createParser(new StringReader(text));
        while (parser.hasNext())
        {
            values.add(readValue(parser, parser.next()));
        }
        return values;
    }

    private static boolean isEmptyYamlStream(
        String text)
    {
        for (String line : text.split("\\R", -1))
        {
            String content = line.stripLeading();
            int commentAt = content.indexOf('#');
            if (commentAt != -1)
            {
                content = content.substring(0, commentAt);
            }
            content = content.strip();
            if (!content.isEmpty() && !"...".equals(content))
            {
                return false;
            }
        }
        return true;
    }

    private static JsonValue readValue(
        JsonParser parser,
        JsonParser.Event event)
    {
        return switch (event)
        {
        case START_OBJECT -> readObject(parser);
        case START_ARRAY -> readArray(parser);
        case VALUE_STRING -> YAML_JSON.createValue(parser.getString());
        case VALUE_NUMBER -> YAML_JSON.createValue(new BigDecimal(parser.getString()));
        case VALUE_TRUE -> JsonValue.TRUE;
        case VALUE_FALSE -> JsonValue.FALSE;
        case VALUE_NULL -> JsonValue.NULL;
        default -> throw new IllegalStateException("Unexpected JSON event: " + event);
        };
    }

    private static JsonValue readObject(
        JsonParser parser)
    {
        JsonObjectBuilder builder = YAML_JSON.createObjectBuilder();
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.END_OBJECT)
            {
                return builder.build();
            }
            String key = parser.getString();
            builder.add(key, readValue(parser, parser.next()));
        }
        throw new IllegalStateException("Unterminated JSON object");
    }

    private static JsonValue readArray(
        JsonParser parser)
    {
        JsonArrayBuilder builder = YAML_JSON.createArrayBuilder();
        while (parser.hasNext())
        {
            JsonParser.Event event = parser.next();
            if (event == JsonParser.Event.END_ARRAY)
            {
                return builder.build();
            }
            builder.add(readValue(parser, event));
        }
        throw new IllegalStateException("Unterminated JSON array");
    }

    private static List<String> splitJsonValues(
        String text)
    {
        List<String> values = new ArrayList<>();
        int offset = 0;
        while (offset < text.length())
        {
            while (offset < text.length() && Character.isWhitespace(text.charAt(offset)))
            {
                offset++;
            }
            if (offset == text.length())
            {
                break;
            }
            int end = jsonValueEnd(text, offset);
            values.add(text.substring(offset, end));
            offset = end;
        }
        return values;
    }

    private static int jsonValueEnd(
        String text,
        int offset)
    {
        char first = text.charAt(offset);
        if (first == '{' || first == '[')
        {
            return jsonBalancedEnd(text, offset);
        }
        if (first == '"')
        {
            return jsonStringEnd(text, offset);
        }
        int end = offset;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end)))
        {
            end++;
        }
        return end;
    }

    private static int jsonBalancedEnd(
        String text,
        int offset)
    {
        boolean string = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = offset; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (string)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (c == '\\')
                {
                    escaped = true;
                }
                else if (c == '"')
                {
                    string = false;
                }
            }
            else if (c == '"')
            {
                string = true;
            }
            else if (c == '{' || c == '[')
            {
                depth++;
            }
            else if (c == '}' || c == ']')
            {
                depth--;
                if (depth == 0)
                {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    private static int jsonStringEnd(
        String text,
        int offset)
    {
        boolean escaped = false;
        for (int i = offset + 1; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (escaped)
            {
                escaped = false;
            }
            else if (c == '\\')
            {
                escaped = true;
            }
            else if (c == '"')
            {
                return i + 1;
            }
        }
        return text.length();
    }

    private static void ensureSuite() throws IOException
    {
        if (Files.exists(SUITE_MARKER))
        {
            return;
        }

        Files.createDirectories(SUITE_DIR);
        try (InputStream input = SUITE_ARCHIVE.toURL().openStream();
             ZipInputStream zip = new ZipInputStream(input))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                String name = stripTopLevelDirectory(entry.getName());
                if (!name.isEmpty())
                {
                    Path target = SUITE_DIR.resolve(name).normalize();
                    if (!target.startsWith(SUITE_DIR))
                    {
                        throw new IOException("Invalid YAML test suite archive entry: " + entry.getName());
                    }
                    if (entry.isDirectory())
                    {
                        Files.createDirectories(target);
                    }
                    else
                    {
                        Files.createDirectories(target.getParent());
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                zip.closeEntry();
            }
        }

        Files.createFile(SUITE_MARKER);
    }

    private static String stripTopLevelDirectory(
        String name)
    {
        int slashAt = name.indexOf('/');
        return slashAt == -1 ? "" : name.substring(slashAt + 1);
    }

    private static String enableMessage()
    {
        return "enable with -D" + ENABLED_PROPERTY + "=true";
    }

    private static boolean isStrict()
    {
        return Boolean.getBoolean(STRICT_PROPERTY);
    }

    private static final class Case
    {
        private final String id;
        private final Path path;

        private Case(
            Path path)
        {
            this.path = path;
            this.id = SUITE_DIR.relativize(path).toString();
        }

        private boolean has(
            String filename)
        {
            return Files.exists(path.resolve(filename));
        }

        private String displayName()
        {
            Path description = path.resolve("===");
            String title = "";
            try
            {
                title = Files.exists(description) ? Files.readString(description).strip() : "";
            }
            catch (IOException ex)
            {
                // Keep test discovery working even if an optional title file is unreadable.
            }
            return title.isEmpty() ? id : id + " " + title;
        }
    }
}
