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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
    private static final String STR_TAG = "tag:yaml.org,2002:str";
    private static final String INT_TAG = "tag:yaml.org,2002:int";
    private static final String FLOAT_TAG = "tag:yaml.org,2002:float";
    private static final String BOOL_TAG = "tag:yaml.org,2002:bool";
    private static final String NULL_TAG = "tag:yaml.org,2002:null";
    private static final String MAP_TAG = "tag:yaml.org,2002:map";
    private static final String SEQ_TAG = "tag:yaml.org,2002:seq";
    private static final String NON_SPECIFIC_TAG = "!";

    private static final Pattern HEX_INTEGER_PATTERN = Pattern.compile("-?0x[0-9a-fA-F]+");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?(?:0|[1-9][0-9]*)");
    private static final Pattern FLOAT_PATTERN = Pattern.compile(
        "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)|-?(?:0|[1-9][0-9]*)\\.[0-9]+");

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
        // a stream may hold several documents; resolve each top-level value in turn
        while (cursor < scanner.count())
        {
            emitValue();
        }
    }

    private void emitValue()
    {
        int at = cursor;
        String tag = scanner.tag(at);
        String anchor = scanner.anchor(at);
        int start = count;
        byte kind = scanner.kind(at);
        switch (kind)
        {
        case YamlStreamScanner.ALIAS ->
        {
            if (tag != null)
            {
                throw new Unsupported();
            }
            emitAlias(at);
        }
        case YamlStreamScanner.START_OBJECT ->
        {
            requireContainerTag(tag, kind);
            emitObject(at);
        }
        case YamlStreamScanner.START_ARRAY ->
        {
            requireContainerTag(tag, kind);
            emitArray(at);
        }
        default ->
        {
            emitScalar(kind, at, tag);
            cursor++;
        }
        }

        if (anchor != null)
        {
            anchors.put(anchor, new int[] {start, count});
        }
    }

    private void emitScalar(
        byte kind,
        int at,
        String tag)
    {
        if (tag == null)
        {
            append(kind, scalarValue(kind, at), at);
        }
        else
        {
            String text = scalarText(kind, at);
            switch (tag)
            {
            case STR_TAG, NON_SPECIFIC_TAG -> append(YamlStreamScanner.VALUE_STRING, text, at);
            case NULL_TAG -> append(YamlStreamScanner.VALUE_NULL, null, at);
            case BOOL_TAG -> emitBool(text, at);
            case INT_TAG -> emitInteger(text, at);
            case FLOAT_TAG -> emitFloat(text, at);
            default ->
            {
                if (MAP_TAG.equals(tag) || SEQ_TAG.equals(tag))
                {
                    throw new Unsupported();
                }
                append(kind, scalarValue(kind, at), at);
            }
            }
        }
    }

    private void emitBool(
        String text,
        int at)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        if ("true".equals(lower))
        {
            append(YamlStreamScanner.VALUE_TRUE, null, at);
        }
        else if ("false".equals(lower))
        {
            append(YamlStreamScanner.VALUE_FALSE, null, at);
        }
        else
        {
            throw new Unsupported();
        }
    }

    private void emitInteger(
        String text,
        int at)
    {
        if (HEX_INTEGER_PATTERN.matcher(text).matches() || INTEGER_PATTERN.matcher(text).matches())
        {
            append(YamlStreamScanner.VALUE_NUMBER, numberText(text), at);
        }
        else
        {
            throw new Unsupported();
        }
    }

    private void emitFloat(
        String text,
        int at)
    {
        if (!nonFinite(text) && (FLOAT_PATTERN.matcher(text).matches() || INTEGER_PATTERN.matcher(text).matches()))
        {
            append(YamlStreamScanner.VALUE_NUMBER, text, at);
        }
        else
        {
            throw new Unsupported();
        }
    }

    private void requireContainerTag(
        String tag,
        byte kind)
    {
        if (tag != null)
        {
            boolean supported = switch (tag)
            {
            case MAP_TAG -> kind == YamlStreamScanner.START_OBJECT;
            case SEQ_TAG -> kind == YamlStreamScanner.START_ARRAY;
            case STR_TAG, INT_TAG, FLOAT_TAG, BOOL_TAG, NULL_TAG, NON_SPECIFIC_TAG -> false;
            default -> true;
            };
            if (!supported)
            {
                throw new Unsupported();
            }
        }
    }

    private String scalarText(
        byte kind,
        int at)
    {
        return switch (kind)
        {
        case YamlStreamScanner.VALUE_STRING, YamlStreamScanner.VALUE_NUMBER -> scanner.string(at);
        case YamlStreamScanner.VALUE_TRUE -> "true";
        case YamlStreamScanner.VALUE_FALSE -> "false";
        default -> "";
        };
    }

    private static String numberText(
        String text)
    {
        boolean negative = text.startsWith("-");
        String scalar = negative ? text.substring(1) : text;
        String result;
        if (scalar.startsWith("0x"))
        {
            String value = new BigInteger(scalar.substring(2), 16).toString();
            result = negative ? "-" + value : value;
        }
        else
        {
            result = text;
        }
        return result;
    }

    private static boolean nonFinite(
        String text)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        return ".nan".equals(lower) || ".inf".equals(lower) || "+.inf".equals(lower) || "-.inf".equals(lower);
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
