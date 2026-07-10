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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import java.util.List;
import java.util.Map;

public class AsyncapiOperation extends AbstractAsyncapiResolvable
{
    public AsyncapiChannel channel;
    public String action;
    public AsyncapiReply reply;
    public List<AsyncapiMessage> messages;
    public List<AsyncapiSecurityScheme> security;
    public List<AsyncapiTag> tags;
    public Map<String, Object> bindings;

    public Map<String, Object> extensions;
}
