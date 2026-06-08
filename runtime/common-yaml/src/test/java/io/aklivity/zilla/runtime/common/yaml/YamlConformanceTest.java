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
        "27NA",
        "2EBW",
        "2G84/02",
        "2G84/03",
        "2SXE",
        "2XXW",
        "35KP",
        "36F6",
        "3ALJ",
        "3GZX",
        "3MYT",
        "3R3P",
        "3RLN/00",
        "3RLN/01",
        "3RLN/02",
        "3RLN/03",
        "3RLN/04",
        "3RLN/05",
        "4CQQ",
        "4Q9F",
        "4QFQ",
        "4WA9",
        "4ZYM",
        "565N",
        "5C5M",
        "5GBF",
        "5KJE",
        "5TYM",
        "5WE3",
        "652Z",
        "6BCT",
        "6CA3",
        "6CK3",
        "6FWR",
        "6HB6",
        "6JQW",
        "6LVF",
        "6VJK",
        "6WLZ",
        "6WPF",
        "6XDY",
        "6ZKB",
        "74H7",
        "753E",
        "7A4E",
        "7BMT",
        "7FWL",
        "7T8X",
        "7W2P",
        "7Z25",
        "7ZZ5",
        "82AN",
        "87E4",
        "8KB6",
        "8UDB",
        "93WF",
        "96L6",
        "96NN/00",
        "96NN/01",
        "9BXH",
        "9DXL",
        "9KAX",
        "9MQT/00",
        "9SA2",
        "9TFX",
        "9WXW",
        "9YRD",
        "A2M4",
        "A984",
        "AB8U",
        "AZW3",
        "B3HG",
        "BU8L",
        "C2DT",
        "C4HZ",
        "CC74",
        "CN3R",
        "CT4Q",
        "CUP7",
        "DBG4",
        "DE56/00",
        "DE56/01",
        "DE56/02",
        "DE56/03",
        "DE56/04",
        "DE56/05",
        "DK3J",
        "DK95/00",
        "DK95/02",
        "DK95/03",
        "DK95/04",
        "DK95/05",
        "DK95/07",
        "DK95/08",
        "DWX9",
        "E76Z",
        "EHF6",
        "EX5H",
        "EXG3",
        "F6MC",
        "F8F9",
        "FBC9",
        "FP8R",
        "FTA2",
        "G992",
        "H2RW",
        "HMQ5",
        "HS5T",
        "J3BT",
        "J7PZ",
        "JEF9/02",
        "JHB9",
        "JTV5",
        "K527",
        "K54U",
        "K858",
        "KH5V/01",
        "KSS4",
        "L24T/00",
        "L24T/01",
        "L383",
        "L94M",
        "L9U5",
        "LE5A",
        "LQZ7",
        "M29M",
        "M5C3",
        "M7A3",
        "M9B4",
        "MJS9",
        "MYW6",
        "NAT4",
        "NB6Z",
        "NJ66",
        "NP9H",
        "P2AD",
        "P76L",
        "PRH3",
        "PUW8",
        "Q5MG",
        "Q8AD",
        "QF4Y",
        "R4YG",
        "RZT7",
        "S4JQ",
        "SKE5",
        "T26H",
        "T4YY",
        "T5N4",
        "TL85",
        "TS54",
        "U3XV",
        "U9NS",
        "UDR7",
        "UGM3",
        "UT92",
        "UV7Q",
        "W42U",
        "W4TN",
        "WZ62",
        "XLQ9",
        "XV9V",
        "Y79Y/001",
        "Y79Y/002",
        "Y79Y/010",
        "Z67P",
        "Z9M4",
        "ZH7C",
        "ZWK4");
    private static final Set<String> INVALID_REJECTION_GAPS = Set.of(
        "2G84/00",
        "2G84/01",
        "5LLU",
        "9C9N",
        "9JBA",
        "9MAG",
        "9MMA",
        "B63P",
        "CTN5",
        "CVW2",
        "CXX2",
        "DK4H",
        "EB22",
        "G5U8",
        "H7TQ",
        "MUS6/00",
        "MUS6/01",
        "RHX7",
        "S98Z",
        "SF5V",
        "SR86",
        "SY6V",
        "T833",
        "U99R",
        "VJP3/00",
        "W9L4",
        "Y79Y/004",
        "Y79Y/005",
        "Y79Y/006",
        "Y79Y/007",
        "Y79Y/008",
        "Y79Y/009",
        "YJV2",
        "ZCZ6");

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
