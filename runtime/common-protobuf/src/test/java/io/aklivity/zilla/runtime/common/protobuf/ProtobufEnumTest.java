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
package io.aklivity.zilla.runtime.common.protobuf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProtobufEnumTest
{
    @Test
    void shouldListValueNamesInDeclarationOrder()
    {
        ProtobufEnum enumeration = ProtobufEnum.builder("test.Kind")
            .value("UNKNOWN", 0)
            .value("WRITE", 2)
            .value("READ", 1)
            .build();

        assertEquals(List.of("UNKNOWN", "WRITE", "READ"), List.copyOf(enumeration.names()));
    }

    @Test
    void shouldListNoValueNamesForEmptyEnum()
    {
        ProtobufEnum enumeration = ProtobufEnum.builder("test.Empty").build();

        assertTrue(enumeration.names().isEmpty());
    }
}
