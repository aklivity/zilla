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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable Protobuf enum descriptor mapping value names to numbers in both directions, so the
 * proto3 JSON mapping can render a value as its name and parse a name back to its number.
 */
public final class ProtobufEnum
{
    private final String name;
    private final Map<Integer, String> nameByNumber;
    private final Map<String, Integer> numberByName;

    private ProtobufEnum(
        String name,
        Map<Integer, String> nameByNumber,
        Map<String, Integer> numberByName)
    {
        this.name = name;
        this.nameByNumber = nameByNumber;
        this.numberByName = numberByName;
    }

    public String name()
    {
        return name;
    }

    public String name(
        int number)
    {
        return nameByNumber.get(number);
    }

    public Integer number(
        String name)
    {
        return numberByName.get(name);
    }

    public static Builder builder(
        String name)
    {
        return new Builder(name);
    }

    public static final class Builder
    {
        private final String name;
        private final Map<Integer, String> nameByNumber;
        private final Map<String, Integer> numberByName;

        private Builder(
            String name)
        {
            this.name = name;
            this.nameByNumber = new LinkedHashMap<>();
            this.numberByName = new LinkedHashMap<>();
        }

        public Builder value(
            String name,
            int number)
        {
            nameByNumber.putIfAbsent(number, name);
            numberByName.put(name, number);
            return this;
        }

        public ProtobufEnum build()
        {
            return new ProtobufEnum(name, nameByNumber, numberByName);
        }
    }
}
