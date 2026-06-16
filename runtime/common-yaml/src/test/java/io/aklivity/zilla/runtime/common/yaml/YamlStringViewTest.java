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
            YamlEvent event = parser.next();
            String string = event.getString();
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
}
