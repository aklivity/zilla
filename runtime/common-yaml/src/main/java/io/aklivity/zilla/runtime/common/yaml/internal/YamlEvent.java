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
package io.aklivity.zilla.runtime.common.yaml.internal;

/**
 * A reusable cursor over a single {@link YamlParser} event. The parser mutates and returns the same instance
 * on every step to avoid per-event allocation on the hot path, so a caller must read an event's properties
 * before advancing the parser. Scalar and alias text is exposed as a {@link CharSequence} view; structural
 * events carry only their {@link #type()} and any node {@link #anchor()} / {@link #tag()} properties.
 */
public final class YamlEvent
{
    private YamlEventType type;
    private CharSequence value;
    private YamlScalarType scalarType;
    private String anchor;
    private String tag;
    private String alias;

    YamlEvent()
    {
    }

    public YamlEventType type()
    {
        return type;
    }

    public CharSequence value()
    {
        return value;
    }

    public YamlScalarType scalarType()
    {
        return scalarType;
    }

    public String anchor()
    {
        return anchor;
    }

    public String tag()
    {
        return tag;
    }

    public String alias()
    {
        return alias;
    }

    YamlEvent reset(
        YamlEventType type,
        CharSequence value,
        YamlScalarType scalarType,
        String anchor,
        String tag,
        String alias)
    {
        this.type = type;
        this.value = value;
        this.scalarType = scalarType;
        this.anchor = anchor;
        this.tag = tag;
        this.alias = alias;
        return this;
    }
}
