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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream.cache;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;

// each hydrated route's tools/list fragment already carries its toolkit__-prefixed name (see
// McpProxyListFactory), but an optional "title" is a free-text, unprefixed display label -- two
// toolkits can legitimately expose tools with the same everyday-language title. Disambiguation is
// collision-only: a title is only rewritten when the SAME title is shared by tools from two or more
// distinct toolkits. A route with no toolkit configured has no identifier to disambiguate with, so
// its items never contribute to collision detection and are never themselves rewritten -- a title
// shared with only one other toolkit-identified route (or with a toolkit-less route) is left alone.
// Per the MCP spec's own display precedence order (title, then annotations.title, then name), the
// effective title checked for a collision -- and the one rewritten -- is the top-level "title" when
// present, falling back to "annotations.title" only when the tool has no top-level title at all.
// This runs once per cache hydration cycle (not on the request hot path), so the jakarta.json object
// model is used freely, mirroring McpProxyCacheHydrater's own per-item JSON rewrites (nameFirst,
// injectToolScopes).
final class McpToolTitleDisambiguator
{
    private static final String TITLE = "title";
    private static final String ANNOTATIONS = "annotations";

    private McpToolTitleDisambiguator()
    {
    }

    static void disambiguate(
        Map<String, String> fragmentsByPrefix,
        Map<String, String> toolkitsByPrefix)
    {
        final Map<String, List<JsonObject>> itemsByPrefix = new LinkedHashMap<>();
        final Map<String, Set<String>> toolkitsByTitle = new LinkedHashMap<>();

        for (Map.Entry<String, String> fragment : fragmentsByPrefix.entrySet())
        {
            final String prefix = fragment.getKey();
            final String items = fragment.getValue();
            if (items == null || items.isEmpty())
            {
                continue;
            }

            final List<JsonObject> parsed = parseItems(items);
            itemsByPrefix.put(prefix, parsed);

            final String toolkit = toolkitsByPrefix.get(prefix);
            if (toolkit != null)
            {
                for (JsonObject item : parsed)
                {
                    final String title = effectiveTitle(item);
                    if (title != null)
                    {
                        toolkitsByTitle.computeIfAbsent(title, t -> new LinkedHashSet<>()).add(toolkit);
                    }
                }
            }
        }

        final Set<String> collidingTitles = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : toolkitsByTitle.entrySet())
        {
            if (entry.getValue().size() > 1)
            {
                collidingTitles.add(entry.getKey());
            }
        }

        if (!collidingTitles.isEmpty())
        {
            for (Map.Entry<String, List<JsonObject>> entry : itemsByPrefix.entrySet())
            {
                final String prefix = entry.getKey();
                final String toolkit = toolkitsByPrefix.get(prefix);
                final String rewritten = toolkit != null
                    ? rewriteItems(entry.getValue(), collidingTitles, toolkit)
                    : null;
                if (rewritten != null)
                {
                    fragmentsByPrefix.put(prefix, rewritten);
                }
            }
        }
    }

    private static String rewriteItems(
        List<JsonObject> items,
        Set<String> collidingTitles,
        String toolkit)
    {
        boolean changed = false;
        final List<JsonObject> rewritten = new ArrayList<>(items.size());

        for (JsonObject item : items)
        {
            final String title = effectiveTitle(item);
            if (title != null && collidingTitles.contains(title))
            {
                rewritten.add(withDisambiguatedTitle(item, title, toolkit));
                changed = true;
            }
            else
            {
                rewritten.add(item);
            }
        }

        return changed ? writeItems(rewritten) : null;
    }

    private static String effectiveTitle(
        JsonObject item)
    {
        final String title = item.getString(TITLE, null);
        return title != null ? title : annotationsTitle(item);
    }

    private static String annotationsTitle(
        JsonObject item)
    {
        return item.get(ANNOTATIONS) instanceof JsonObject annotations ? annotations.getString(TITLE, null) : null;
    }

    private static JsonObject withDisambiguatedTitle(
        JsonObject item,
        String title,
        String toolkit)
    {
        final String disambiguated = title + " (" + toolkit + ")";
        return item.containsKey(TITLE)
            ? withField(item, TITLE, Json.createValue(disambiguated))
            : withField(item, ANNOTATIONS, withField(item.getJsonObject(ANNOTATIONS), TITLE, Json.createValue(disambiguated)));
    }

    private static JsonObject withField(
        JsonObject object,
        String key,
        JsonValue value)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, JsonValue> field : object.entrySet())
        {
            builder.add(field.getKey(), key.equals(field.getKey()) ? value : field.getValue());
        }
        return builder.build();
    }

    private static List<JsonObject> parseItems(
        String items)
    {
        final List<JsonObject> result = new ArrayList<>();
        try (JsonReader reader = Json.createReader(new StringReader("[" + items + "]")))
        {
            for (JsonValue item : reader.readArray())
            {
                result.add((JsonObject) item);
            }
        }
        catch (JsonException | ClassCastException ex)
        {
            result.clear();
        }
        return result;
    }

    private static String writeItems(
        List<JsonObject> items)
    {
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        items.forEach(arrayBuilder::add);

        final StringWriter writer = new StringWriter();
        try (JsonWriter jsonWriter = Json.createWriter(writer))
        {
            jsonWriter.writeArray(arrayBuilder.build());
        }
        final String written = writer.toString();
        return written.substring(1, written.length() - 1);
    }
}
