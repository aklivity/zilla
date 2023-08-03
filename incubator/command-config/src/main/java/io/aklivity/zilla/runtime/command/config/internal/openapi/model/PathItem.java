/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.config.internal.openapi.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PathItem
{
    // TODO: Ati - Objects everywhere
    private Map<String, Map<String, Object>> methods;

    public List<Server> servers;
    public Map<String, Object> get;
    public Map<String, Object> put;
    public Map<String, Object> post;
    public Map<String, Object> delete;
    public Map<String, Object> options;
    public Map<String, Object> head;
    public Map<String, Object> patch;
    public Map<String, Object> trace;

    public void initMethods()
    {
        methods = new LinkedHashMap<>();
        if (get != null)
        {
            methods.put("GET", get);
        }
        if (put != null)
        {
            methods.put("PUT", put);
        }
        if (post != null)
        {
            methods.put("POST", post);
        }
        if (delete != null)
        {
            methods.put("DELETE", delete);
        }
        if (options != null)
        {
            methods.put("OPTIONS", options);
        }
        if (head != null)
        {
            methods.put("HEAD", head);
        }
        if (patch != null)
        {
            methods.put("PATCH", patch);
        }
        if (trace != null)
        {
            methods.put("TRACE", trace);
        }
    }

    public Map<String, Map<String, Object>> methods()
    {
        return methods;
    }
}
