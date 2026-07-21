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
package io.aklivity.zilla.runtime.common.yaml.internal;

/**
 * The presentation style of a {@link YamlEvent#SCALAR} event, mirroring the YAML 1.2 scalar styles and the
 * style indicators used by the canonical {@code test.event} format ({@code :} plain, {@code '} single-quoted,
 * {@code "} double-quoted, {@code |} literal, {@code >} folded). The style is presentation-only; it does not
 * change the scalar's resolved value or type.
 */
public enum YamlScalarStyle
{
    PLAIN,
    SINGLE,
    DOUBLE,
    LITERAL,
    FOLDED
}
