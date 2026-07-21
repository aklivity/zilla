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
package io.aklivity.zilla.runtime.command.dump.internal.airline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class ZillaDumpDissectorsTest
{
    @Test
    public void shouldAssembleDissectorMatchingGolden() throws IOException
    {
        String golden;
        try (InputStream in = getClass().getResourceAsStream("golden.lua"))
        {
            assert in != null;
            golden = new String(in.readAllBytes(), UTF_8);
        }

        String assembled = ZillaDumpDissectors.assemble()
            .replaceFirst("local zilla_version = \"[^\"]*\"", "local zilla_version = \"@version@\"");

        assertEquals(golden, assembled);
    }
}
