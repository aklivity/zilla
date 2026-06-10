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
package io.aklivity.zilla.runtime.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import jakarta.json.JsonReader;
import jakarta.json.spi.JsonProvider;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.json.Jsonp;

/**
 * Drives the vendored JSON Parsing Test Suite (nst/JSONTestSuite, see the {@code jsontestsuite}
 * resource directory). File-name prefixes follow the suite convention: {@code y_} must be
 * accepted, {@code n_} must be rejected, {@code i_} is implementation-defined (either result is
 * RFC 8259 compliant, so it is not gated here).
 * <p>
 * The streaming parser was built for path-filtered streaming rather than strict RFC 8259
 * validation, so it does not yet reject every malformed document. The cases it currently handles
 * incorrectly are pinned in {@link #KNOWN_DEVIATIONS}; the test asserts the live deviation set
 * matches it exactly, so any regression (a new deviation) or improvement (a pinned case that now
 * behaves) fails the build and is reviewed deliberately.
 */
final class JsonTestSuiteConformanceTest
{
    private static final JsonProvider PROVIDER = Jsonp.provider();

    // Must-reject cases the streaming parser currently accepts, pinned from a baseline run.
    // Two categories: trailing tokens after a complete value (no end-of-input enforcement) and
    // lenient number lexing (leading zeros, missing integer/fraction part). Burn this down as the
    // parser is hardened toward strict RFC 8259 conformance.
    private static final Set<String> KNOWN_DEVIATIONS = Set.of(
        "n_array_comma_after_close.json",
        "n_array_extra_close.json",
        "n_multidigit_number_then_00.json",
        "n_number_-01.json",
        "n_number_-2..json",
        "n_number_0.e1.json",
        "n_number_2.e+3.json",
        "n_number_2.e-3.json",
        "n_number_2.e3.json",
        "n_number_neg_int_starting_with_zero.json",
        "n_number_neg_real_without_int_part.json",
        "n_number_real_without_fractional_part.json",
        "n_number_with_leading_zero.json",
        "n_object_trailing_comment.json",
        "n_object_trailing_comment_open.json",
        "n_object_trailing_comment_slash_open.json",
        "n_object_trailing_comment_slash_open_incomplete.json",
        "n_object_with_trailing_garbage.json",
        "n_string_with_trailing_garbage.json",
        "n_structure_array_trailing_garbage.json",
        "n_structure_array_with_extra_array_close.json",
        "n_structure_close_unopened_array.json",
        "n_structure_double_array.json",
        "n_structure_number_with_trailing_garbage.json",
        "n_structure_object_followed_by_closing_object.json",
        "n_structure_object_with_trailing_garbage.json",
        "n_structure_trailing_#.json");

    @Test
    void shouldMatchJsonTestSuiteDeviationBaseline() throws Exception
    {
        Path dir = Path.of(getClass().getResource("jsontestsuite/test_parsing").toURI());
        Set<String> deviations = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir))
        {
            List<Path> cases = files
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .toList();
            for (Path testCase : cases)
            {
                String name = testCase.getFileName().toString();
                boolean accepted = accepts(Files.readAllBytes(testCase));
                boolean deviates = switch (name.charAt(0))
                {
                case 'y' -> !accepted;
                case 'n' -> accepted;
                default -> false;
                };
                if (deviates)
                {
                    deviations.add(name);
                }
            }
        }
        Files.writeString(Path.of("target", "json-testsuite-deviations.txt"),
            String.join("\n", deviations));
        assertEquals(KNOWN_DEVIATIONS, deviations, "JSONTestSuite deviation set changed");
    }

    private static boolean accepts(
        byte[] document)
    {
        boolean accepted;
        try (JsonReader reader = PROVIDER.createReader(new ByteArrayInputStream(document)))
        {
            reader.readValue();
            accepted = true;
        }
        catch (RuntimeException | StackOverflowError ex)
        {
            accepted = false;
        }
        return accepted;
    }
}
