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
package io.aklivity.zilla.runtime.common.avro.internal;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.common.avro.AvroKind;
import io.aklivity.zilla.runtime.common.avro.AvroValidationException;

/**
 * Compiles an Avro schema document (JSON) into an immutable {@link AvroNode} tree. Runs once per
 * schema, off the hot path; uses {@code jakarta.json} for the document parse. Named types
 * (records, enums, fixed) are registered so later references by name resolve to the same node.
 */
final class AvroSchemaCompiler
{
    private final Map<String, AvroNode> named = new HashMap<>();

    private AvroSchemaCompiler()
    {
    }

    static AvroNode compile(
        String schema)
    {
        AvroNode root;
        try (JsonReader reader = Json.createReader(new StringReader(schema)))
        {
            root = new AvroSchemaCompiler().parse(reader.readValue(), null);
        }
        catch (JsonException ex)
        {
            throw new AvroValidationException("malformed Avro schema document", ex);
        }
        return root;
    }

    private AvroNode parse(
        JsonValue value,
        String enclosingNamespace)
    {
        AvroNode node;
        switch (value.getValueType())
        {
        case STRING:
            node = resolve(((JsonString) value).getString(), enclosingNamespace);
            break;
        case ARRAY:
            node = parseUnion((JsonArray) value, enclosingNamespace);
            break;
        case OBJECT:
            node = parseObject((JsonObject) value, enclosingNamespace);
            break;
        default:
            throw new AvroValidationException("unexpected schema node: " + value);
        }
        return node;
    }

    private AvroNode parseUnion(
        JsonArray array,
        String enclosingNamespace)
    {
        AvroNode[] branches = new AvroNode[array.size()];
        for (int i = 0; i < branches.length; i++)
        {
            branches[i] = parse(array.get(i), enclosingNamespace);
        }
        return AvroNode.ofUnion(branches);
    }

    private AvroNode parseObject(
        JsonObject object,
        String enclosingNamespace)
    {
        String type = object.getString("type");
        String namespace = object.containsKey("namespace")
            ? object.getString("namespace")
            : enclosingNamespace;
        String logicalType = object.containsKey("logicalType")
            ? object.getString("logicalType")
            : null;
        int precision = object.containsKey("precision") ? object.getInt("precision") : 0;
        int scale = object.containsKey("scale") ? object.getInt("scale") : 0;

        AvroNode node;
        switch (type)
        {
        case "record":
            node = parseRecord(object, namespace);
            break;
        case "enum":
            node = register(object, namespace,
                AvroNode.ofEnum(object.getString("name"), toStringArray(object.getJsonArray("symbols"))));
            break;
        case "fixed":
            node = register(object, namespace,
                AvroNode.ofFixed(object.getString("name"), object.getInt("size"), logicalType, precision, scale));
            break;
        case "array":
            node = AvroNode.ofArray(parse(object.get("items"), namespace));
            break;
        case "map":
            node = AvroNode.ofMap(parse(object.get("values"), namespace));
            break;
        default:
            node = primitive(type, logicalType, precision, scale);
            break;
        }
        return node;
    }

    private AvroNode parseRecord(
        JsonObject object,
        String namespace)
    {
        List<JsonValue> fields = object.getJsonArray("fields");
        String[] fieldNames = new String[fields.size()];
        AvroNode[] fieldTypes = new AvroNode[fields.size()];
        AvroNode record = AvroNode.ofRecord(object.getString("name"), fieldNames, fieldTypes);
        register(object, namespace, record);
        for (int i = 0; i < fields.size(); i++)
        {
            JsonObject field = fields.get(i).asJsonObject();
            fieldNames[i] = field.getString("name");
            fieldTypes[i] = parse(field.get("type"), namespace);
        }
        return record;
    }

    private AvroNode register(
        JsonObject object,
        String namespace,
        AvroNode node)
    {
        String name = object.getString("name");
        named.put(name, node);
        if (namespace != null)
        {
            named.put(namespace + "." + name, node);
        }
        return node;
    }

    private AvroNode resolve(
        String name,
        String enclosingNamespace)
    {
        AvroNode node = named.get(name);
        if (node == null && enclosingNamespace != null)
        {
            node = named.get(enclosingNamespace + "." + name);
        }
        if (node == null)
        {
            node = primitive(name, null, 0, 0);
        }
        if (node == null)
        {
            throw new AvroValidationException("unresolved schema reference: " + name);
        }
        return node;
    }

    private static AvroNode primitive(
        String type,
        String logicalType,
        int precision,
        int scale)
    {
        AvroNode node;
        switch (type)
        {
        case "null":
            node = AvroNode.ofPrimitive(AvroKind.NULL, logicalType, precision, scale);
            break;
        case "boolean":
            node = AvroNode.ofPrimitive(AvroKind.BOOLEAN, logicalType, precision, scale);
            break;
        case "int":
            node = AvroNode.ofPrimitive(AvroKind.INT, logicalType, precision, scale);
            break;
        case "long":
            node = AvroNode.ofPrimitive(AvroKind.LONG, logicalType, precision, scale);
            break;
        case "float":
            node = AvroNode.ofPrimitive(AvroKind.FLOAT, logicalType, precision, scale);
            break;
        case "double":
            node = AvroNode.ofPrimitive(AvroKind.DOUBLE, logicalType, precision, scale);
            break;
        case "bytes":
            node = AvroNode.ofPrimitive(AvroKind.BYTES, logicalType, precision, scale);
            break;
        case "string":
            node = AvroNode.ofPrimitive(AvroKind.STRING, logicalType, precision, scale);
            break;
        default:
            node = null;
            break;
        }
        return node;
    }

    private static String[] toStringArray(
        JsonArray array)
    {
        String[] values = new String[array.size()];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = array.getString(i);
        }
        return values;
    }
}
