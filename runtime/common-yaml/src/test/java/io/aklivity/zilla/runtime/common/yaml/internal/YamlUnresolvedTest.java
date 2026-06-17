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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.yaml.YamlConfig;

class YamlUnresolvedTest
{
    private static final String DOCUMENT = """
        base: &base
          host: localhost
        use:
          <<: *base
          port: 7114
        typed: !!str 42
        """;

    private static final YamlConfiguration RAW =
        new YamlConfiguration(Map.of(YamlConfig.RESOLVE_REFERENCES, false));

    @Test
    void shouldRetainAnchorAliasMergeAndTagWhenUnresolved()
    {
        YamlObjectNode root = (YamlObjectNode) YamlDocumentParser.parse(DOCUMENT, RAW).node;

        YamlNode base = entry(root, "base").value;
        assertEquals("base", base.anchor);

        YamlObjectNode use = (YamlObjectNode) entry(root, "use").value;
        YamlEntry merge = entry(use, "<<");
        assertNotNull(merge, "merge key retained");
        assertEquals("base", merge.value.alias);
        assertNotNull(entry(use, "port"));
        assertNull(entry(use, "host"), "merge not applied when unresolved");

        YamlScalarNode typed = (YamlScalarNode) entry(root, "typed").value;
        assertEquals("tag:yaml.org,2002:str", typed.tag);
        assertEquals(YamlScalarType.NUMBER, typed.type, "tag not coerced when unresolved");
    }

    @Test
    void shouldResolveByDefault()
    {
        YamlObjectNode root = (YamlObjectNode) YamlDocumentParser.parse(DOCUMENT, YamlConfiguration.DEFAULT).node;

        YamlObjectNode use = (YamlObjectNode) entry(root, "use").value;
        assertNull(entry(use, "<<"), "merge key consumed when resolved");
        assertNotNull(entry(use, "host"), "merge applied when resolved");
        assertNotNull(entry(use, "port"));

        YamlScalarNode typed = (YamlScalarNode) entry(root, "typed").value;
        assertEquals(YamlScalarType.STRING, typed.type, "tag coerced when resolved");
    }

    @Test
    void shouldNotPopulateAnchorMapWhenUnresolved()
    {
        boolean resolved = true;
        try
        {
            YamlDocumentParser.parse("use: *missing\n", RAW);
        }
        catch (RuntimeException ex)
        {
            resolved = false;
        }
        assertTrue(resolved, "unresolved mode must not dereference (or fail on) a dangling alias");
    }

    private static YamlEntry entry(
        YamlObjectNode object,
        String name)
    {
        YamlEntry found = null;
        for (YamlEntry entry : object.entries)
        {
            if (name.equals(entry.name))
            {
                found = entry;
                break;
            }
        }
        return found;
    }
}
