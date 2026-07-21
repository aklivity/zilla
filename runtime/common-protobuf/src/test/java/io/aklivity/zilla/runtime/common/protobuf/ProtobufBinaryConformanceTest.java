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
package io.aklivity.zilla.runtime.common.protobuf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.protobuf.internal.ProtobufCanonicalizer;

/**
 * Replays a vendored binary round-trip conformance corpus: for each case, the {@code .in} payload
 * is canonicalized and compared to its {@code .expected} canonical encoding. Cases named in
 * {@code failure_list.txt} are treated as known gaps and skipped.
 * <p>
 * The corpus under {@code conformance/} is seeded with a few hand-crafted cases. The full corpus is
 * captured offline from the protobuf conformance runner — see {@code src/test/conformance/README.md}.
 * The harness, manifest format, and failure-list mechanics here are what that capture populates.
 */
public class ProtobufBinaryConformanceTest
{
    private static final String ROOT = "/io/aklivity/zilla/runtime/common/protobuf/conformance/";

    private final ProtobufSchema schema = newSchema();

    @TestFactory
    public List<DynamicTest> shouldCanonicalizeCorpus()
    {
        Set<String> failures = readFailureList();
        return readManifest().stream()
            .filter(entry -> !failures.contains(entry[0]))
            .map(entry -> dynamicTest(entry[0], () -> runCase(entry[0], entry[1])))
            .collect(Collectors.toList());
    }

    private void runCase(
        String name,
        String messageName)
    {
        byte[] input = readBytes(ROOT + "cases/" + name + ".in");
        byte[] expected = readBytes(ROOT + "cases/" + name + ".expected");

        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[Math.max(64, expected.length * 2 + 16)]);
        ProtobufCanonicalizer canonicalizer = new ProtobufCanonicalizer(schema);
        int length = canonicalizer.canonicalize(messageName, new UnsafeBufferEx(input), 0, input.length, out, 0);

        byte[] actual = new byte[length];
        out.getBytes(0, actual);
        assertArrayEquals(expected, actual, name);
    }

    private List<String[]> readManifest()
    {
        List<String[]> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(open(ROOT + "manifest.tsv"),
            StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#"))
                {
                    entries.add(trimmed.split("\t"));
                }
            }
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return entries;
    }

    private Set<String> readFailureList()
    {
        Set<String> failures = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(open(ROOT + "failure_list.txt"),
            StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#"))
                {
                    failures.add(trimmed);
                }
            }
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return failures;
    }

    private byte[] readBytes(
        String resource)
    {
        try (InputStream in = open(resource))
        {
            return in.readAllBytes();
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }

    private InputStream open(
        String resource)
    {
        InputStream in = getClass().getResourceAsStream(resource);
        assertNotNull(in, "missing conformance resource " + resource);
        return in;
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .message(ProtobufMessage.builder("conformance.TestMessage.G")
                .field(ProtobufField.builder().number(1).name("x").type(ProtobufType.INT32).build())
                .build())
            .message(ProtobufMessage.builder("conformance.TestMessage")
                .field(ProtobufField.builder().number(1).name("a").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("b").type(ProtobufType.INT32).build())
                .field(ProtobufField.builder().number(3).name("c").type(ProtobufType.INT32).repeated(true).build())
                .field(ProtobufField.builder().number(4).name("g").type(ProtobufType.GROUP)
                    .typeName("conformance.TestMessage.G").build())
                .build())
            .build();
    }
}
