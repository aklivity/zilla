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
package io.aklivity.zilla.runtime.common.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class YamlStringViewTest
{
    private static final String DOCUMENT = """
        name: example
        port: 7114
        host: 0.0.0.0
        nested:
          quoted: "a value"
        """;

    @Test
    void shouldReadScalarsAsZeroCopyView()
    {
        YamlParser parser = Yaml.createParser(new StringReader(DOCUMENT));

        List<String> viewed = new ArrayList<>();
        while (parser.hasNext())
        {
            parser.next();
            String string = parser.getString();
            CharSequence view = parser.getStringView();
            if (string != null)
            {
                assertEquals(string.length(), view.length());
                assertEquals(string, view.toString());
                assertEquals(string.charAt(0), view.charAt(0));
                assertEquals(string.subSequence(0, 1).toString(), view.subSequence(0, 1).toString());
                viewed.add(view.toString());
            }
            else
            {
                assertNull(view);
            }
        }

        assertTrue(viewed.contains("name"));
        assertTrue(viewed.contains("example"));
        assertTrue(viewed.contains("7114"));
        assertTrue(viewed.contains("a value"));
    }

    @Test
    void shouldStreamFlowDocumentWithZeroCopyViews()
    {
        YamlParser parser = Yaml.createParser(new StringReader(
            "{\"name\":\"test\",\"port\":7114,\"items\":[1,true,null]}\n"));

        assertEquals(YamlEvent.START_OBJECT, parser.next());
        assertEquals(YamlValue.ValueType.OBJECT, parser.getValue().getValueType());

        List<String> events = new ArrayList<>();
        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            String string = parser.getString();
            events.add(string != null ? event + ":" + string : event.toString());
            if (string != null)
            {
                assertEquals(string, parser.getStringView().toString());
            }
            else
            {
                assertNull(parser.getStringView());
            }
        }

        assertEquals(List.of(
            "KEY_NAME:name",
            "VALUE_STRING:test",
            "KEY_NAME:port",
            "VALUE_NUMBER:7114",
            "KEY_NAME:items",
            "START_ARRAY",
            "VALUE_NUMBER:1",
            "VALUE_TRUE",
            "VALUE_NULL",
            "END_ARRAY",
            "END_OBJECT"), events);
    }
}
