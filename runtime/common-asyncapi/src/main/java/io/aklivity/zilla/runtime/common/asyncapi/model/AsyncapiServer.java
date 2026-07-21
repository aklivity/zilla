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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import java.util.List;
import java.util.Map;

public class AsyncapiServer extends AbstractAsyncapiResolvable
{
    public String host;
    public String url;
    public String pathname;
    public String title;
    public String summary;
    public String description;
    public String protocol;
    public String protocolVersion;
    public List<AsyncapiSecurityScheme> security;
    public Map<String, AsyncapiServerVariable> variables;
    public List<AsyncapiTag> tags;
    public Map<String, Object> bindings;

    public Map<String, Object> extensions;
}
