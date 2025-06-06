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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model;

import java.util.List;
import java.util.Map;

import jakarta.json.bind.annotation.JsonbProperty;

public class AsyncapiSchema extends AsyncapiSchemaItem
{
    public String type;
    public AsyncapiSchema items;
    public Map<String, AsyncapiSchema> properties;
    public List<String> required;
    public String format;
    public String description;
    public Integer minimum;
    public Integer maximum;
    @JsonbProperty("enum")
    public List<String> values;
    public AsyncapiSchema schema;
    public List<AsyncapiSchema> oneOf;
    public List<AsyncapiSchema> allOf;
    public List<AsyncapiSchema> anyOf;
    public Map<String, String> discriminator;
}
