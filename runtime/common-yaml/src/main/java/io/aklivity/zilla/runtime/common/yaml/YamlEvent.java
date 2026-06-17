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

/**
 * The event currency of the streaming {@link YamlParser}: {@link YamlParser#next()} returns the next
 * event as a plain enum and the caller pulls the associated scalar or value through the parser
 * accessors ({@link YamlParser#getString()}, {@link YamlParser#getStringView()},
 * {@link YamlParser#getValue()}, …). This pull model mirrors {@code jakarta.json.stream.JsonParser} and
 * lets a streaming source expose the current token without materializing a {@code YamlValue} per event.
 */
public enum YamlEvent
{
    START_OBJECT,
    END_OBJECT,
    START_ARRAY,
    END_ARRAY,
    KEY_NAME,
    VALUE_STRING,
    VALUE_NUMBER,
    VALUE_TRUE,
    VALUE_FALSE,
    VALUE_NULL,
    ALIAS
}
