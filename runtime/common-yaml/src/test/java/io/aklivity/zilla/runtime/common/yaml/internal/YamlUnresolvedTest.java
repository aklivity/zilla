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

import org.junit.jupiter.api.Test;

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

    private static final YamlConfiguration RAW = YamlConfiguration.DEFAULT;

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

    @Test
    void shouldParseBareExplicitKeyBlock()
    {
        // a bare ? explicit-key indicator with the (non-scalar) key on the following block lines (6PBE);
        // previously this threw StringIndexOutOfBoundsException via content.substring(2)
        YamlObjectNode root = (YamlObjectNode) YamlDocumentParser.parse("---\n?\n- a\n- b\n:\n- c\n- d\n", RAW).node;

        assertEquals(1, root.entries.size());
        YamlEntry entry = root.entries.get(0);
        assertNull(entry.name, "non-scalar key has no scalar name");
        YamlArrayNode key = (YamlArrayNode) entry.key;
        assertEquals("a", ((YamlScalarNode) key.values.get(0)).value);
        assertEquals("b", ((YamlScalarNode) key.values.get(1)).value);
        YamlArrayNode value = (YamlArrayNode) entry.value;
        assertEquals("c", ((YamlScalarNode) value.values.get(0)).value);
        assertEquals("d", ((YamlScalarNode) value.values.get(1)).value);
    }

    @Test
    void shouldParseEmptyMappingKey()
    {
        // an empty mapping key (`: value`) is the empty scalar, not a parse error (2JQS, NHX8, S3PD, ...)
        YamlObjectNode root = (YamlObjectNode) YamlDocumentParser.parse(": a\n", RAW).node;

        assertEquals(1, root.entries.size());
        YamlEntry entry = root.entries.get(0);
        assertEquals("", entry.name, "empty key is the empty scalar");
        assertEquals("a", ((YamlScalarNode) entry.value).value);
    }

    @Test
    void shouldParseEmptyKeyInFlowSequence()
    {
        // an implicit single-pair mapping with an empty key inside a flow sequence (CFD4: [ : x ])
        YamlArrayNode root = (YamlArrayNode) YamlDocumentParser.parse("[ : x ]\n", RAW).node;

        YamlObjectNode pair = (YamlObjectNode) root.values.get(0);
        YamlEntry entry = pair.entries.get(0);
        assertEquals("", entry.name, "empty flow key is the empty scalar");
        assertEquals("x", ((YamlScalarNode) entry.value).value);
    }

    @Test
    void shouldParseFlowKeyWithAdjacentValue()
    {
        // a colon adjacent to a quoted scalar or flow collection key is a mapping colon (9MMW)
        YamlArrayNode quoted = (YamlArrayNode) YamlDocumentParser.parse("[ \"JSON like\":adjacent ]\n", RAW).node;
        YamlObjectNode quotedPair = (YamlObjectNode) quoted.values.get(0);
        assertEquals("JSON like", quotedPair.entries.get(0).name);
        assertEquals("adjacent", ((YamlScalarNode) quotedPair.entries.get(0).value).value);

        YamlArrayNode flow = (YamlArrayNode) YamlDocumentParser.parse("[ {JSON: like}:adjacent ]\n", RAW).node;
        YamlObjectNode flowPair = (YamlObjectNode) flow.values.get(0);
        YamlEntry flowEntry = flowPair.entries.get(0);
        assertEquals("like", ((YamlScalarNode) ((YamlObjectNode) flowEntry.key).entries.get(0).value).value);
        assertEquals("adjacent", ((YamlScalarNode) flowEntry.value).value);
    }

    @Test
    void shouldParseExplicitInlineMappingSequenceItem()
    {
        // a `? k: v` sequence item key and a `: k: v` value are each single-pair inline block mappings (V9D5)
        YamlArrayNode root = (YamlArrayNode) YamlDocumentParser.parse(
            "- sun: yellow\n- ? earth: blue\n  : moon: white\n", RAW).node;

        YamlObjectNode first = (YamlObjectNode) root.values.get(0);
        assertEquals("yellow", ((YamlScalarNode) entry(first, "sun").value).value);

        YamlObjectNode second = (YamlObjectNode) root.values.get(1);
        YamlEntry explicit = second.entries.get(0);
        assertNull(explicit.name, "non-scalar key has no name");
        YamlObjectNode key = (YamlObjectNode) explicit.key;
        assertEquals("blue", ((YamlScalarNode) entry(key, "earth").value).value);
        YamlObjectNode value = (YamlObjectNode) explicit.value;
        assertEquals("white", ((YamlScalarNode) entry(value, "moon").value).value);
    }

    @Test
    void shouldParseExplicitInlineMappingKeyWithFlowKey()
    {
        // `? []: x` is an explicit key that is itself the inline mapping {[]: x}, with a null outer value (M2N8/01)
        YamlObjectNode root = (YamlObjectNode) YamlDocumentParser.parse("? []: x\n", RAW).node;

        YamlEntry outer = root.entries.get(0);
        assertNull(outer.name, "non-scalar key has no name");
        assertEquals(YamlScalarType.NULL, ((YamlScalarNode) outer.value).type, "outer value is null");

        YamlObjectNode key = (YamlObjectNode) outer.key;
        YamlEntry inner = key.entries.get(0);
        assertNull(inner.name, "empty-seq key has no name");
        assertTrue(inner.key instanceof YamlArrayNode, "inner key is an empty sequence");
        assertEquals("x", ((YamlScalarNode) inner.value).value);
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
