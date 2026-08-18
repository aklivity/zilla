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
package io.aklivity.zilla.runtime.catalog.inline.internal;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32C;

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Object2IntHashMap;

import io.aklivity.zilla.config.catalog.inline.InlineOptionsConfig;
import io.aklivity.zilla.config.catalog.inline.InlineSchemaConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

public class InlineCatalogHandler implements CatalogHandler
{
    private static final String VERSION_LATEST = "latest";
    private static final int NO_REFERENCES = 0;
    private static final int[] NO_SCHEMA_IDS = new int[0];

    // matches the same ${guarded['name'].identity} / ${guarded['name'].attributes.x} syntax
    // kafka-proxy's topics[].alias already resolves per-connection, so a subject configured
    // on the consuming model (e.g. catalog.subject) can reference the caller's identity the
    // same way a topic alias does. The guard name inside the brackets is resolved dynamically
    // per expression, the same way TlsWithResolver / HttpKafkaWithResolver do it, rather than
    // via a single statically-configured guard on the catalog.
    private static final Pattern IDENTITY_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).identity\\}");
    private static final Pattern ATTRIBUTE_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).attributes" +
            ".([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)\\}");

    private static final char WILDCARD = '*';

    private final Int2ObjectHashMap<String> schemas;
    private final Object2IntHashMap<String> schemaIds;
    private final Map<String, Pattern> schemaIdPatterns;
    private final Int2IntHashMap references;
    private final CRC32C crc32c;
    private final Function<String, GuardHandler> guards;
    private final Map<String, GuardHandler> guardsByName;
    private final Matcher identityMatcher;
    private final Matcher attributeMatcher;

    public InlineCatalogHandler(
        InlineOptionsConfig config,
        Function<String, GuardHandler> guards)
    {
        this.schemas = new Int2ObjectHashMap<>();
        this.schemaIds = new Object2IntHashMap<>(NO_SCHEMA_ID);
        this.schemaIdPatterns = new LinkedHashMap<>();
        this.references = new Int2IntHashMap(NO_REFERENCES);
        this.crc32c = new CRC32C();
        this.guards = guards;
        this.guardsByName = new HashMap<>();
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
        this.attributeMatcher = ATTRIBUTE_PATTERN.matcher("");
        if (config != null)
        {
            registerSchema(config.subjects);
        }
    }

    @Override
    public int register(
        String subject,
        String schema)
    {
        return register(subject, VERSION_LATEST, schema);
    }

    @Override
    public int[] unregister(
        String subject)
    {
        String key = subject + VERSION_LATEST;
        int schemaId = schemaIds.removeKey(key);
        schemaIdPatterns.remove(key);
        int[] removed = NO_SCHEMA_IDS;
        if (schemaId != NO_SCHEMA_ID)
        {
            release(schemaId);
            removed = new int[] { schemaId };
        }
        return removed;
    }

    @Override
    public String resolve(
        int schemaId)
    {
        return schemas.get(schemaId);
    }

    @Override
    public int resolve(
        String subject,
        String version)
    {
        return lookup(subject + version);
    }

    @Override
    public int resolve(
        String subject,
        String version,
        long authorization)
    {
        String resolved = resolveExpression(subject, authorization);
        return lookup(resolved + version);
    }

    private int lookup(
        String key)
    {
        int schemaId = schemaIds.getValue(key);
        if (schemaId == NO_SCHEMA_ID && !schemaIdPatterns.isEmpty())
        {
            for (Map.Entry<String, Pattern> entry : schemaIdPatterns.entrySet())
            {
                if (entry.getValue().matcher(key).matches())
                {
                    schemaId = schemaIds.getValue(entry.getKey());
                    break;
                }
            }
        }
        return schemaId;
    }

    private String resolveExpression(
        String subject,
        long authorization)
    {
        String resolved = findAndReplace(subject, identityMatcher,
            r -> orEmpty(identity(r.group(1), authorization)));
        resolved = findAndReplace(resolved, attributeMatcher,
            r -> orEmpty(attribute(r.group(1), authorization, r.group(2))));
        return resolved;
    }

    private String identity(
        String guardName,
        long authorization)
    {
        GuardHandler guard = supplyGuard(guardName);
        return guard != null ? guard.identity(authorization) : null;
    }

    private String attribute(
        String guardName,
        long authorization,
        String name)
    {
        GuardHandler guard = supplyGuard(guardName);
        return guard != null ? guard.attribute(authorization, name) : null;
    }

    private GuardHandler supplyGuard(
        String guardName)
    {
        return guards != null ? guardsByName.computeIfAbsent(guardName, guards) : null;
    }

    private static String orEmpty(
        String value)
    {
        return value != null ? value : "";
    }

    private static String findAndReplace(
        String value,
        Matcher matcher,
        Function<MatchResult, String> replacer)
    {
        matcher.reset(value);
        while (matcher.find())
        {
            value = matcher.replaceAll(replacer);
            matcher.reset(value);
        }
        return value;
    }

    private int register(
        String subject,
        String version,
        String schema)
    {
        int schemaId = generateCRC32C(schema);
        String key = subject + version;
        int current = schemaIds.getValue(key);
        if (current != schemaId)
        {
            if (current != NO_SCHEMA_ID)
            {
                release(current);
            }
            schemaIds.put(key, schemaId);
            schemas.putIfAbsent(schemaId, schema);
            references.put(schemaId, references.get(schemaId) + 1);
            if (subject.indexOf(WILDCARD) != -1)
            {
                schemaIdPatterns.put(key, toPattern(key));
            }
        }
        return schemaId;
    }

    private static Pattern toPattern(
        String key)
    {
        StringBuilder pattern = new StringBuilder("^");
        for (String literal : key.split("\\" + WILDCARD, -1))
        {
            pattern.append(Pattern.quote(literal)).append(".*");
        }
        pattern.setLength(pattern.length() - 2);
        pattern.append('$');
        return Pattern.compile(pattern.toString());
    }

    private void release(
        int schemaId)
    {
        int count = references.get(schemaId);
        if (count != NO_REFERENCES)
        {
            if (count <= 1)
            {
                references.remove(schemaId);
                schemas.remove(schemaId);
            }
            else
            {
                references.put(schemaId, count - 1);
            }
        }
    }

    private int generateCRC32C(
        String schema)
    {
        byte[] bytes = schema.getBytes();
        crc32c.reset();
        crc32c.update(bytes, 0, bytes.length);
        return (int) crc32c.getValue();
    }

    private void registerSchema(
        List<InlineSchemaConfig> configs)
    {
        if (configs != null)
        {
            for (InlineSchemaConfig config : configs)
            {
                register(config.subject, config.version, config.schema);
            }
        }
    }
}
