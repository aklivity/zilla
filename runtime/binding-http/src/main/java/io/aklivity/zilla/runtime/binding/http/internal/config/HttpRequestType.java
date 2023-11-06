/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.config;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.config.HttpRequestConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.validator.Validator;

public class HttpRequestType
{
    // selectors
    public String path;
    public HttpRequestConfig.Method method;
    public List<String> contentType;

    // path parsing helpers
    public Pattern pathPattern;
    public Map<String, String8FW> pathParamValues;
    public Map<String, String8FW> queryParamValues;

    // validators
    public Map<String8FW, Validator> headers;
    public Map<String, Validator> pathParams;
    public Map<String, Validator> queryParams;
    public Validator content;
}
