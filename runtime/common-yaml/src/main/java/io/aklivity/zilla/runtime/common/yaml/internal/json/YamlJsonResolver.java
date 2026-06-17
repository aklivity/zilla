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
package io.aklivity.zilla.runtime.common.yaml.internal.json;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import jakarta.json.stream.JsonParsingException;

import io.aklivity.zilla.runtime.common.yaml.internal.YamlLocation;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlStreamScanner;

/**
 * Streaming reference resolver over the raw {@link YamlStreamScanner} event buffer. Anchors precede
 * their aliases in YAML, so a single forward pass suffices: each {@code &anchor} records the resolved
 * output range of its node and each {@code *alias} replays that range. The result is a flat resolved
 * event buffer that {@link YamlJsonParser} projects exactly like the scanner's own buffer, so no
 * eager DOM is built for reference documents.
 *
 * <p>Constructs it can't resolve in a single pass under the YAML 1.2 JSON Schema (currently tags, and
 * anchors or aliases used as mapping keys) raise {@link Unsupported}; the caller then falls back to
 * the eager path.
 */
final class YamlJsonResolver
{
    private final YamlStreamScanner scanner;
    private final Map<String, int[]> anchors;

    private byte[] kinds;
    private String[] values;
    private int[] sources;
    private int count;
    private int cursor;

    YamlJsonResolver(
        YamlStreamScanner scanner)
    {
        this.scanner = scanner;
        this.anchors = new HashMap<>();
        int capacity = Math.max(scanner.count(), 1);
        this.kinds = new byte[capacity];
        this.values = new String[capacity];
        this.sources = new int[capacity];
        resolve();
    }

    int count()
    {
        return count;
    }

    byte kind(
        int index)
    {
        return kinds[index];
    }

    String value(
        int index)
    {
        return values[index];
    }

    int line(
        int index)
    {
        return scanner.line(sources[index]);
    }

    int column(
        int index)
    {
        return scanner.column(sources[index]);
    }

    int offset(
        int index)
    {
        return scanner.offset(sources[index]);
    }

    private void resolve()
    {
        cursor = 0;
        emitValue();
    }

    private void emitValue()
    {
        int at = cursor;
        if (scanner.tag(at) != null)
        {
            throw new Unsupported();
        }

        String anchor = scanner.anchor(at);
        int start = count;
        byte kind = scanner.kind(at);
        switch (kind)
        {
        case YamlStreamScanner.ALIAS -> emitAlias(at);
        case YamlStreamScanner.START_OBJECT -> emitObject(at);
        case YamlStreamScanner.START_ARRAY -> emitArray(at);
        default ->
        {
            append(kind, scalarValue(kind, at), at);
            cursor++;
        }
        }

        if (anchor != null)
        {
            anchors.put(anchor, new int[] {start, count});
        }
    }

    private void emitAlias(
        int at)
    {
        String alias = scanner.alias(at);
        cursor++;
        int[] range = anchors.get(alias);
        if (range == null)
        {
            throw new JsonParsingException("Unresolved YAML alias",
                new YamlJsonLocation(new YamlLocation(scanner.line(at), scanner.column(at), scanner.offset(at))));
        }
        for (int index = range[0]; index < range[1]; index++)
        {
            append(kinds[index], values[index], sources[index]);
        }
    }

    private void emitObject(
        int at)
    {
        append(YamlStreamScanner.START_OBJECT, null, at);
        cursor++;
        while (scanner.kind(cursor) != YamlStreamScanner.END_OBJECT)
        {
            int key = cursor;
            if (scanner.kind(key) != YamlStreamScanner.KEY_NAME ||
                scanner.anchor(key) != null || scanner.tag(key) != null)
            {
                throw new Unsupported();
            }
            append(YamlStreamScanner.KEY_NAME, scanner.string(key), key);
            cursor++;
            emitValue();
        }
        append(YamlStreamScanner.END_OBJECT, null, cursor);
        cursor++;
    }

    private void emitArray(
        int at)
    {
        append(YamlStreamScanner.START_ARRAY, null, at);
        cursor++;
        while (scanner.kind(cursor) != YamlStreamScanner.END_ARRAY)
        {
            emitValue();
        }
        append(YamlStreamScanner.END_ARRAY, null, cursor);
        cursor++;
    }

    private String scalarValue(
        byte kind,
        int index)
    {
        return kind == YamlStreamScanner.VALUE_STRING || kind == YamlStreamScanner.VALUE_NUMBER ?
            scanner.string(index) : null;
    }

    private void append(
        byte kind,
        String value,
        int source)
    {
        if (count == kinds.length)
        {
            int capacity = kinds.length << 1;
            kinds = Arrays.copyOf(kinds, capacity);
            values = Arrays.copyOf(values, capacity);
            sources = Arrays.copyOf(sources, capacity);
        }
        kinds[count] = kind;
        values[count] = value;
        sources[count] = source;
        count++;
    }

    static final class Unsupported extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        Unsupported()
        {
            super(null, null, false, false);
        }
    }
}
