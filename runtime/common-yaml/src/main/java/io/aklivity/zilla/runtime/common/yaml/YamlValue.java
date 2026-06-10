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

import java.util.List;

public interface YamlValue
{
    ValueType getValueType();

    String getTag();

    String getAnchor();

    String getAlias();

    String getStyle();

    List<String> getLeadingComments();

    String getLineComment();

    YamlValue withTag(
        String tag);

    YamlValue withAnchor(
        String anchor);

    YamlValue withAlias(
        String alias);

    YamlValue withStyle(
        String style);

    YamlValue withLeadingComment(
        String comment);

    YamlValue withLeadingComments(
        List<String> comments);

    YamlValue withLineComment(
        String comment);

    default YamlObject asYamlObject()
    {
        return (YamlObject) this;
    }

    default YamlArray asYamlArray()
    {
        return (YamlArray) this;
    }

    default YamlScalar asYamlScalar()
    {
        return (YamlScalar) this;
    }

    enum ValueType
    {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        TRUE,
        FALSE,
        NULL
    }
}
