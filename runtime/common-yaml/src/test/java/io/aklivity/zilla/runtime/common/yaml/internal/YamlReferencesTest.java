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
package io.aklivity.zilla.runtime.common.yaml.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.aklivity.zilla.runtime.common.yaml.YamlConfig;

/**
 * Differential guard for the standalone {@link YamlReferences} resolver. For every conformance
 * fixture, resolving a raw (unresolved) parse tree must yield exactly the tree the parser produces
 * in its inline resolved mode. This pins the extracted compose/construct pass to the in-place logic
 * it replaces, across alias dereferencing, {@code <<} merge, and tag coercion.
 */
class YamlReferencesTest
{
    private static final String SUITE_TAG = "data-2022-01-17";

    private static final YamlConfiguration RAW =
        new YamlConfiguration(Map.of(YamlConfig.RESOLVE_REFERENCES, false));

    @TestFactory
    Stream<DynamicTest> shouldMatchInlineResolutionForEveryFixture() throws Exception
    {
        return fixtures()
            .map(path -> DynamicTest.dynamicTest(SUITE_DIR.relativize(path).toString(), () ->
            {
                String text = Files.readString(path.resolve("in.yaml"));
                YamlNode inline = YamlDocumentParser.parse(text).node;
                YamlNode resolved = YamlReferences.resolve(
                    YamlDocumentParser.parse(text, RAW).node, Map.of());
                assertEquals(project(inline), project(resolved),
                    "resolver diverged from inline resolution");
            }));
    }

    private static String project(
        YamlNode node)
    {
        StringBuilder builder = new StringBuilder();
        project(node, builder);
        return builder.toString();
    }

    private static void project(
        YamlNode node,
        StringBuilder builder)
    {
        if (node instanceof YamlObjectNode object)
        {
            builder.append("START_OBJECT").append(meta(object)).append('\n');
            for (YamlEntry entry : object.entries)
            {
                String name = entry.name != null ? entry.name :
                    entry.key instanceof YamlScalarNode scalar ? scalar.value : "?";
                builder.append("KEY_NAME").append('(').append(name).append(')')
                    .append(entry.merged ? "[merged]" : "").append('\n');
                project(entry.value, builder);
            }
            builder.append("END_OBJECT").append('\n');
        }
        else if (node instanceof YamlArrayNode array)
        {
            builder.append("START_ARRAY").append(meta(array)).append('\n');
            for (YamlNode value : array.values)
            {
                project(value, builder);
            }
            builder.append("END_ARRAY").append('\n');
        }
        else
        {
            YamlScalarNode scalar = (YamlScalarNode) node;
            switch (scalar.type)
            {
            case STRING -> builder.append("VALUE_STRING").append('(').append(scalar.value).append(')');
            case NUMBER -> builder.append("VALUE_NUMBER").append('(').append(scalar.value).append(')');
            case TRUE -> builder.append("VALUE_TRUE");
            case FALSE -> builder.append("VALUE_FALSE");
            case NULL -> builder.append("VALUE_NULL");
            }
            builder.append(meta(scalar)).append('\n');
        }
    }

    private static String meta(
        YamlNode node)
    {
        return (node.anchor != null ? "&" + node.anchor : "") +
            (node.alias != null ? "*" + node.alias : "") +
            (node.tag != null ? "!" + node.tag : "") +
            (node.style != null ? "%" + node.style : "");
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
        URL resource = YamlReferencesTest.class.getResource("/io/aklivity/zilla/runtime/common/yaml/" + SUITE_TAG);
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
