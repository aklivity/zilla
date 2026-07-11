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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class JsonOverlayTest
{
    @Test
    void shouldMergeUpdateIntoMatchedObject()
    {
        JsonValue document = read("{\"info\":{\"title\":\"a\",\"version\":\"1.0\"}}");
        JsonOverlayAction action = new JsonOverlayAction("$.info", read("{\"version\":\"2.0\"}"), false);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"info\":{\"title\":\"a\",\"version\":\"2.0\"}}", result.toString());
    }

    @Test
    void shouldRecursivelyMergeNestedObjects()
    {
        JsonValue document = read("{\"a\":{\"b\":{\"x\":1,\"y\":2},\"c\":3}}");
        JsonOverlayAction action = new JsonOverlayAction("$.a", read("{\"b\":{\"y\":9}}"), false);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"a\":{\"b\":{\"x\":1,\"y\":9},\"c\":3}}", result.toString());
    }

    @Test
    void shouldAddNewPropertyWhenMerging()
    {
        JsonValue document = read("{\"info\":{\"title\":\"a\"}}");
        JsonOverlayAction action = new JsonOverlayAction("$.info", read("{\"version\":\"2.0\"}"), false);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"info\":{\"title\":\"a\",\"version\":\"2.0\"}}", result.toString());
    }

    @Test
    void shouldReplaceScalarWhenTargetIsScalar()
    {
        JsonValue document = read("{\"info\":{\"title\":\"a\",\"version\":\"1.0\"}}");
        JsonOverlayAction action = new JsonOverlayAction("$.info.version", read("\"2.0\""), false);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"info\":{\"title\":\"a\",\"version\":\"2.0\"}}", result.toString());
    }

    @Test
    void shouldAppendUpdateEntryToMatchedArray()
    {
        JsonValue document = read("{\"tags\":[\"a\",\"b\"]}");
        JsonOverlayAction action = new JsonOverlayAction("$.tags", read("\"c\""), false);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"tags\":[\"a\",\"b\",\"c\"]}", result.toString());
    }

    @Test
    void shouldApplyUpdateToEveryWildcardMatchedObject()
    {
        JsonValue document = read("{\"paths\":{\"/a\":{\"get\":{}},\"/b\":{\"get\":{}}}}");
        JsonOverlayAction action = new JsonOverlayAction("$.paths[*].get", read("{\"deprecated\":true}"), false);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"paths\":{\"/a\":{\"get\":{\"deprecated\":true}},\"/b\":{\"get\":{\"deprecated\":true}}}}",
            result.toString());
    }

    @Test
    void shouldRemoveMatchedObjectProperty()
    {
        JsonValue document = read("{\"a\":1,\"b\":2}");
        JsonOverlayAction action = new JsonOverlayAction("$.b", null, true);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"a\":1}", result.toString());
    }

    @Test
    void shouldRemoveMatchedArrayItem()
    {
        JsonValue document = read("{\"items\":[\"a\",\"b\",\"c\"]}");
        JsonOverlayAction action = new JsonOverlayAction("$.items[1]", null, true);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"items\":[\"a\",\"c\"]}", result.toString());
    }

    @Test
    void shouldReEvaluateTargetAgainstTheEvolvingDocumentForEachAction()
    {
        JsonValue document = read("{\"items\":[\"a\",\"b\",\"c\",\"d\"]}");
        JsonOverlayAction removeIndex2 = new JsonOverlayAction("$.items[2]", null, true);
        JsonOverlayAction removeIndex0OfRemainder = new JsonOverlayAction("$.items[0]", null, true);

        JsonValue result = JsonOverlay.of(List.of(removeIndex2, removeIndex0OfRemainder)).apply(document);

        assertEquals("{\"items\":[\"b\",\"d\"]}", result.toString());
    }

    @Test
    void shouldRemoveAllWildcardMatchesInSingleActionHighestIndexFirst()
    {
        JsonValue document = read("{\"items\":[\"a\",\"b\",\"c\",\"d\"]}");
        JsonOverlayAction action = new JsonOverlayAction("$.items[*]", null, true);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{\"items\":[]}", result.toString());
    }

    @Test
    void shouldBeNoOpWhenTargetDoesNotMatch()
    {
        JsonValue document = read("{\"a\":1}");
        JsonOverlayAction update = new JsonOverlayAction("$.missing", read("{\"x\":1}"), false);
        JsonOverlayAction remove = new JsonOverlayAction("$.alsoMissing", null, true);

        JsonValue result = JsonOverlay.of(List.of(update, remove)).apply(document);

        assertEquals("{\"a\":1}", result.toString());
    }

    @Test
    void shouldApplyActionsInOrderSoLaterActionsSeeEarlierEdits()
    {
        JsonValue document = read("{}");
        JsonOverlayAction first = new JsonOverlayAction("$", read("{\"info\":{\"title\":\"a\"}}"), false);
        JsonOverlayAction second = new JsonOverlayAction("$.info", read("{\"version\":\"1.0\"}"), false);

        JsonValue result = JsonOverlay.of(List.of(first, second)).apply(document);

        assertEquals("{\"info\":{\"title\":\"a\",\"version\":\"1.0\"}}", result.toString());
    }

    @Test
    void shouldParseOverlayDocumentAndApplyActionsInOrder()
    {
        JsonValue overlayDoc = read(
            "{\"overlay\":\"1.0.0\",\"info\":{\"title\":\"t\",\"version\":\"1.0\"}," +
            "\"actions\":[" +
                "{\"target\":\"$.info\",\"update\":{\"version\":\"2.0\"}}," +
                "{\"target\":\"$.deprecated\",\"remove\":true}" +
            "]}");
        JsonValue document = read("{\"info\":{\"title\":\"a\",\"version\":\"1.0\"},\"deprecated\":true}");

        JsonValue result = JsonOverlay.of(overlayDoc).apply(document);

        assertEquals("{\"info\":{\"title\":\"a\",\"version\":\"2.0\"}}", result.toString());
    }

    @Test
    void shouldRejectOverlayDocumentMissingVersion()
    {
        JsonValue overlayDoc = read(
            "{\"info\":{\"title\":\"t\",\"version\":\"1.0\"},\"actions\":[{\"target\":\"$\",\"remove\":true}]}");

        assertThrows(IllegalArgumentException.class, () -> JsonOverlay.of(overlayDoc));
    }

    @Test
    void shouldRejectOverlayDocumentWithNoActions()
    {
        JsonValue overlayDoc = read(
            "{\"overlay\":\"1.0.0\",\"info\":{\"title\":\"t\",\"version\":\"1.0\"},\"actions\":[]}");

        assertThrows(IllegalArgumentException.class, () -> JsonOverlay.of(overlayDoc));
    }

    @Test
    void shouldTreatRemoveTrueAsTakingPrecedenceOverUpdate()
    {
        JsonValue document = read("{\"a\":1}");
        JsonOverlayAction action = new JsonOverlayAction("$.a", read("2"), true);

        JsonValue result = JsonOverlay.of(List.of(action)).apply(document);

        assertEquals("{}", result.toString());
        assertTrue(action.remove());
    }

    private static JsonValue read(
        String text)
    {
        return Json.createReader(new StringReader(text)).readValue();
    }
}
