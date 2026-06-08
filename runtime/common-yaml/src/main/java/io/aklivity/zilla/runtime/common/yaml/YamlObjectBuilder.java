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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public interface YamlObjectBuilder
{
    YamlObjectBuilder add(
        String name,
        YamlValue value);

    YamlObjectBuilder add(
        String name,
        String value);

    YamlObjectBuilder add(
        String name,
        BigDecimal value);

    YamlObjectBuilder add(
        String name,
        BigInteger value);

    YamlObjectBuilder add(
        String name,
        int value);

    YamlObjectBuilder add(
        String name,
        long value);

    YamlObjectBuilder add(
        String name,
        double value);

    YamlObjectBuilder add(
        String name,
        boolean value);

    YamlObjectBuilder addNull(
        String name);

    YamlObjectBuilder add(
        String name,
        YamlObjectBuilder builder);

    YamlObjectBuilder add(
        String name,
        YamlArrayBuilder builder);

    YamlObjectBuilder addAll(
        Map<String, ?> map);

    YamlObjectBuilder addAll(
        YamlObjectBuilder builder);

    YamlObjectBuilder remove(
        String name);

    YamlObject build();
}
