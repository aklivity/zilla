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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPointer;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * Applies an OpenAPI Overlay Specification (1.0.0) document to a target {@link JsonValue}
 * document, producing the materialized result. Each action's {@code target} JSONPath (see
 * {@link JsonPath}) is re-evaluated against the document as it stands after every preceding
 * action, so later actions observe earlier edits.
 * <p>
 * For a matched node that is an object, {@code update} is recursively merged into it
 * (existing properties are overwritten, new properties are added). For a matched node that
 * is an array, {@code update} is appended as a new element. For any other matched node
 * (a scalar or {@code null}), {@code update} replaces it outright. A {@code remove} action
 * deletes every matched node from its containing object or array; when a single action
 * matches multiple array elements, removal proceeds from the highest index to the lowest so
 * earlier indices remain valid. A target that matches nothing is a no-op.
 */
public final class JsonOverlay
{
    private final List<JsonOverlayAction> actions;

    private JsonOverlay(
        List<JsonOverlayAction> actions)
    {
        this.actions = actions;
    }

    public static JsonOverlay of(
        List<JsonOverlayAction> actions)
    {
        if (actions.isEmpty())
        {
            throw new IllegalArgumentException("Overlay must contain at least one action");
        }
        return new JsonOverlay(new ArrayList<>(actions));
    }

    public static JsonOverlay of(
        JsonValue document)
    {
        JsonObject object = document.asJsonObject();
        if (!object.containsKey("overlay") || object.getString("overlay", "").isEmpty())
        {
            throw new IllegalArgumentException("Overlay document missing required \"overlay\" version field");
        }

        JsonArray actionsArray = object.containsKey("actions") ? object.getJsonArray("actions") : null;
        if (actionsArray == null || actionsArray.isEmpty())
        {
            throw new IllegalArgumentException("Overlay document must contain at least one action");
        }

        List<JsonOverlayAction> actions = new ArrayList<>(actionsArray.size());
        for (JsonValue actionValue : actionsArray)
        {
            JsonObject actionObject = actionValue.asJsonObject();
            if (!actionObject.containsKey("target"))
            {
                throw new IllegalArgumentException("Overlay action missing required \"target\" field");
            }
            String target = actionObject.getString("target");
            JsonValue update = actionObject.get("update");
            boolean remove = actionObject.getBoolean("remove", false);
            actions.add(new JsonOverlayAction(target, update, remove));
        }

        return of(actions);
    }

    public JsonValue apply(
        JsonValue document)
    {
        JsonStructure result = (JsonStructure) document;
        for (JsonOverlayAction action : actions)
        {
            result = applyAction(action, result);
        }
        return result;
    }

    private static JsonStructure applyAction(
        JsonOverlayAction action,
        JsonStructure document)
    {
        List<String> pointers = JsonPath.compile(action.target()).matches(document);

        JsonStructure result = document;
        if (action.remove())
        {
            List<String> reversed = new ArrayList<>(pointers);
            Collections.reverse(reversed);
            for (String pointer : reversed)
            {
                result = Json.createPointer(pointer).remove(result);
            }
        }
        else if (action.update() != null)
        {
            for (String pointer : pointers)
            {
                JsonPointer target = Json.createPointer(pointer);
                JsonValue merged = merge(target.getValue(result), action.update());
                result = target.replace(result, merged);
            }
        }
        return result;
    }

    private static JsonValue merge(
        JsonValue existing,
        JsonValue update)
    {
        JsonValue merged;
        if (existing.getValueType() == JsonValue.ValueType.OBJECT && update.getValueType() == JsonValue.ValueType.OBJECT)
        {
            JsonObject existingObject = existing.asJsonObject();
            JsonObjectBuilder builder = Json.createObjectBuilder(existingObject);
            for (Map.Entry<String, JsonValue> entry : update.asJsonObject().entrySet())
            {
                JsonValue existingChild = existingObject.get(entry.getKey());
                JsonValue mergedChild = existingChild != null ? merge(existingChild, entry.getValue()) : entry.getValue();
                builder.add(entry.getKey(), mergedChild);
            }
            merged = builder.build();
        }
        else if (existing.getValueType() == JsonValue.ValueType.ARRAY)
        {
            merged = Json.createArrayBuilder(existing.asJsonArray()).add(update).build();
        }
        else
        {
            merged = update;
        }
        return merged;
    }
}
