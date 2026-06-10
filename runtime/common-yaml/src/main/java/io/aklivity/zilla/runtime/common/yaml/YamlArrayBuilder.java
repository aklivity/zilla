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
import java.util.Collection;
import java.util.List;

public interface YamlArrayBuilder
{
    YamlArrayBuilder add(
        YamlValue value);

    YamlArrayBuilder add(
        String value);

    YamlArrayBuilder add(
        BigDecimal value);

    YamlArrayBuilder add(
        BigInteger value);

    YamlArrayBuilder add(
        int value);

    YamlArrayBuilder add(
        long value);

    YamlArrayBuilder add(
        double value);

    YamlArrayBuilder add(
        boolean value);

    YamlArrayBuilder addNull();

    YamlArrayBuilder add(
        YamlObjectBuilder builder);

    YamlArrayBuilder add(
        YamlArrayBuilder builder);

    YamlArrayBuilder addAll(
        Collection<?> collection);

    YamlArrayBuilder addAll(
        YamlArrayBuilder builder);

    YamlArrayBuilder add(
        int index,
        YamlValue value);

    YamlArrayBuilder add(
        int index,
        String value);

    YamlArrayBuilder add(
        int index,
        BigDecimal value);

    YamlArrayBuilder add(
        int index,
        BigInteger value);

    YamlArrayBuilder add(
        int index,
        int value);

    YamlArrayBuilder add(
        int index,
        long value);

    YamlArrayBuilder add(
        int index,
        double value);

    YamlArrayBuilder add(
        int index,
        boolean value);

    YamlArrayBuilder addNull(
        int index);

    YamlArrayBuilder add(
        int index,
        YamlObjectBuilder builder);

    YamlArrayBuilder add(
        int index,
        YamlArrayBuilder builder);

    YamlArrayBuilder set(
        int index,
        YamlValue value);

    YamlArrayBuilder set(
        int index,
        String value);

    YamlArrayBuilder set(
        int index,
        BigDecimal value);

    YamlArrayBuilder set(
        int index,
        BigInteger value);

    YamlArrayBuilder set(
        int index,
        int value);

    YamlArrayBuilder set(
        int index,
        long value);

    YamlArrayBuilder set(
        int index,
        double value);

    YamlArrayBuilder set(
        int index,
        boolean value);

    YamlArrayBuilder setNull(
        int index);

    YamlArrayBuilder set(
        int index,
        YamlObjectBuilder builder);

    YamlArrayBuilder set(
        int index,
        YamlArrayBuilder builder);

    YamlArrayBuilder remove(
        int index);

    YamlArrayBuilder withTag(
        String tag);

    YamlArrayBuilder withAnchor(
        String anchor);

    YamlArrayBuilder withStyle(
        String style);

    YamlArrayBuilder withLeadingComment(
        String comment);

    YamlArrayBuilder withLeadingComments(
        List<String> comments);

    YamlArrayBuilder withLineComment(
        String comment);

    YamlArray build();
}
