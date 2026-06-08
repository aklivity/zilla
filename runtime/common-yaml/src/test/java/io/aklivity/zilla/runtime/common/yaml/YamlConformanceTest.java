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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;

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

    private static final Set<String> JSON_PROJECTION_GAPS = Set.of(
        "26DV",
        "2EBW",
        "2SXE",
        "2XXW",
        "35KP",
        "36F6",
        "3MYT",
        "4CQQ",
        "4Q9F",
        "4WA9",
        "4ZYM",
        "565N",
        "5GBF",
        "5TYM",
        "5WE3",
        "652Z",
        "6BCT",
        "6CK3",
        "6FWR",
        "6HB6",
        "6WLZ",
        "6WPF",
        "6XDY",
        "6ZKB",
        "74H7",
        "7A4E",
        "7BMT",
        "7FWL",
        "7T8X",
        "7W2P",
        "7Z25",
        "82AN",
        "93WF",
        "96NN/00",
        "96NN/01",
        "9DXL",
        "9KAX",
        "9MQT/00",
        "9TFX",
        "9WXW",
        "9YRD",
        "A2M4",
        "A984",
        "AB8U",
        "AZW3",
        "BU8L",
        "C4HZ",
        "CC74",
        "CN3R",
        "CT4Q",
        "CUP7",
        "DBG4",
        "DK95/02",
        "DK95/08",
        "DWX9",
        "E76Z",
        "EX5H",
        "EXG3",
        "FBC9",
        "H2RW",
        "HMQ5",
        "HS5T",
        "J7PZ",
        "JEF9/02",
        "JHB9",
        "JTV5",
        "K527",
        "K858",
        "KSS4",
        "L24T/00",
        "L24T/01",
        "L383",
        "L94M",
        "M5C3",
        "M7A3",
        "NAT4",
        "NB6Z",
        "NP9H",
        "P76L",
        "PRH3",
        "PUW8",
        "Q8AD",
        "R4YG",
        "RZT7",
        "S4JQ",
        "T26H",
        "T4YY",
        "TL85",
        "TS54",
        "U3XV",
        "U9NS",
        "UGM3",
        "UT92",
        "UV7Q",
        "W4TN",
        "WZ62",
        "XLQ9",
        "XV9V",
        "Y79Y/001",
        "Z67P",
        "Z9M4",
        "ZH7C",
        "ZWK4");
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

                JsonValue expected = readJson(c.path.resolve("in.json"));
                JsonValue actual = readYamlAsJson(c.path.resolve("in.yaml"));
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
                assertThrows(RuntimeException.class, () -> YamlJson.createReader(new StringReader(yaml)).readValue(), c.id);
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

    private static JsonValue readJson(
        Path path) throws IOException
    {
        return readYamlAsJson(path);
    }

    private static JsonValue readYamlAsJson(
        Path path) throws IOException
    {
        return YAML_JSON.createReader(new StringReader(Files.readString(path))).readValue();
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
