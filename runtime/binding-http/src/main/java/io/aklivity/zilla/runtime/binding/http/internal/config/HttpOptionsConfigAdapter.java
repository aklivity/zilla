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

import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpPolicyConfig.SAME_ORIGIN;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.internal.HttpBinding;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpAllowConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpExposeConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAuthorizationConfig.HttpCredentialsConfig;
import io.aklivity.zilla.runtime.binding.http.internal.config.HttpAuthorizationConfig.HttpPatternConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class HttpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String VERSIONS_NAME = "versions";
    private static final String OVERRIDES_NAME = "overrides";
    private static final String ACCESS_CONTROL_NAME = "access-control";
    private static final String POLICY_NAME = "policy";
    private static final String POLICY_VALUE_SAME_ORIGIN = "same-origin";
    private static final String POLICY_VALUE_CROSS_ORIGIN = "cross-origin";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_HEADERS_NAME = "headers";
    private static final String AUTHORIZATION_CREDENTIALS_QUERY_NAME = "query";
    private static final String AUTHORIZATION_CREDENTIALS_COOKIES_NAME = "cookies";
    private static final String ALLOW_NAME = "allow";
    private static final String ALLOW_ORIGINS_NAME = "origins";
    private static final String ALLOW_METHODS_NAME = "methods";
    private static final String ALLOW_HEADERS_NAME = "headers";
    private static final String ALLOW_CREDENTIALS_NAME = "credentials";
    private static final String MAX_AGE_NAME = "max-age";
    private static final String EXPOSE_NAME = "expose";
    private static final String EXPOSE_HEADERS_NAME = "headers";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return HttpBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        HttpOptionsConfig httpOptions = (HttpOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (httpOptions.versions != null &&
            !httpOptions.versions.isEmpty())
        {
            JsonArrayBuilder entries = Json.createArrayBuilder();
            httpOptions.versions.forEach(v -> entries.add(v.asString()));

            object.add(VERSIONS_NAME, entries);
        }

        HttpAccessControlConfig httpAccess = httpOptions.access;
        if (httpAccess != null)
        {
            JsonObjectBuilder access = Json.createObjectBuilder();

            switch (httpAccess.policy)
            {
            case SAME_ORIGIN:
                access.add(POLICY_NAME, POLICY_VALUE_SAME_ORIGIN);
                break;
            case CROSS_ORIGIN:
                access.add(POLICY_NAME, POLICY_VALUE_CROSS_ORIGIN);

                HttpAllowConfig httpAllow = httpAccess.allow;
                if (httpAllow != null)
                {
                    JsonObjectBuilder allow = Json.createObjectBuilder();

                    if (httpAllow.origins != null)
                    {
                        JsonArrayBuilder origins = Json.createArrayBuilder();
                        httpAllow.origins.forEach(origins::add);
                        allow.add(ALLOW_ORIGINS_NAME, origins);
                    }

                    if (httpAllow.methods != null)
                    {
                        JsonArrayBuilder methods = Json.createArrayBuilder();
                        httpAllow.methods.forEach(methods::add);
                        allow.add(ALLOW_METHODS_NAME, methods);
                    }

                    if (httpAllow.headers != null)
                    {
                        JsonArrayBuilder headers = Json.createArrayBuilder();
                        httpAllow.headers.forEach(headers::add);
                        allow.add(ALLOW_HEADERS_NAME, headers);
                    }

                    if (httpAllow.credentials)
                    {
                        allow.add(ALLOW_CREDENTIALS_NAME, httpAllow.credentials);
                    }

                    access.add(ALLOW_NAME, allow);
                }

                Duration maxAge = httpAccess.maxAge;
                if (maxAge != null)
                {
                    access.add(MAX_AGE_NAME, maxAge.toSeconds());
                }

                HttpExposeConfig httpExpose = httpAccess.expose;
                if (httpExpose != null)
                {
                    JsonObjectBuilder expose = Json.createObjectBuilder();

                    if (httpExpose.headers != null)
                    {
                        JsonArrayBuilder headers = Json.createArrayBuilder();
                        httpExpose.headers.forEach(headers::add);
                        expose.add(EXPOSE_HEADERS_NAME, headers);
                    }

                    access.add(EXPOSE_NAME, expose);
                }
                break;
            }

            object.add(ACCESS_CONTROL_NAME, access);
        }

        HttpAuthorizationConfig httpAuthorization = httpOptions.authorization;
        if (httpAuthorization != null)
        {
            JsonObjectBuilder authorizations = Json.createObjectBuilder();

            JsonObjectBuilder authorization = Json.createObjectBuilder();

            HttpCredentialsConfig httpCredentials = httpAuthorization.credentials;
            if (httpCredentials != null)
            {
                JsonObjectBuilder credentials = Json.createObjectBuilder();

                if (httpCredentials.headers != null)
                {
                    JsonObjectBuilder headers = Json.createObjectBuilder();

                    httpCredentials.headers.forEach(p -> headers.add(p.name, p.pattern));

                    credentials.add(AUTHORIZATION_CREDENTIALS_HEADERS_NAME, headers);
                }

                if (httpCredentials.parameters != null)
                {
                    JsonObjectBuilder parameters = Json.createObjectBuilder();

                    httpCredentials.parameters.forEach(p -> parameters.add(p.name, p.pattern));

                    credentials.add(AUTHORIZATION_CREDENTIALS_QUERY_NAME, parameters);
                }

                if (httpCredentials.cookies != null)
                {
                    JsonObjectBuilder cookies = Json.createObjectBuilder();

                    httpCredentials.cookies.forEach(p -> cookies.add(p.name, p.pattern));

                    credentials.add(AUTHORIZATION_CREDENTIALS_COOKIES_NAME, cookies);
                }

                authorization.add(AUTHORIZATION_CREDENTIALS_NAME, credentials);

                authorizations.add(httpAuthorization.name, authorization);
            }

            object.add(AUTHORIZATION_NAME, authorizations);
        }

        if (httpOptions.overrides != null &&
            !httpOptions.overrides.isEmpty())
        {
            JsonObjectBuilder entries = Json.createObjectBuilder();
            httpOptions.overrides.forEach((k, v) -> entries.add(k.asString(), v.asString()));

            object.add(OVERRIDES_NAME, entries);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        JsonArray versions = object.containsKey(VERSIONS_NAME)
                ? object.getJsonArray(VERSIONS_NAME)
                : null;

        SortedSet<HttpVersion> newVersions = null;

        if (versions != null)
        {
            SortedSet<HttpVersion> newVersions0 = new TreeSet<HttpVersion>();
            versions.forEach(v ->
                newVersions0.add(HttpVersion.of(JsonString.class.cast(v).getString())));
            newVersions = newVersions0;
        }

        HttpAuthorizationConfig newAuthorization = null;

        JsonObject authorizations = object.containsKey(AUTHORIZATION_NAME)
                ? object.getJsonObject(AUTHORIZATION_NAME)
                : null;

        if (authorizations != null)
        {
            for (String name : authorizations.keySet())
            {
                JsonObject authorization = authorizations.getJsonObject(name);

                HttpCredentialsConfig newCredentials = null;

                JsonObject credentials = authorization.getJsonObject(AUTHORIZATION_CREDENTIALS_NAME);

                if (credentials != null)
                {
                    List<HttpPatternConfig> newHeaders =
                            adaptPatternFromJson(credentials, AUTHORIZATION_CREDENTIALS_HEADERS_NAME);

                    List<HttpPatternConfig> newParameters =
                            adaptPatternFromJson(credentials, AUTHORIZATION_CREDENTIALS_QUERY_NAME);

                    List<HttpPatternConfig> newCookies =
                            adaptPatternFromJson(credentials, AUTHORIZATION_CREDENTIALS_COOKIES_NAME);

                    newCredentials = new HttpCredentialsConfig(newHeaders, newParameters, newCookies);
                }

                newAuthorization = new HttpAuthorizationConfig(name, newCredentials);
            }
        }

        HttpAccessControlConfig newAccess = null;

        JsonObject access = object.containsKey(ACCESS_CONTROL_NAME)
                ? object.getJsonObject(ACCESS_CONTROL_NAME)
                : null;

        if (access != null)
        {
            String policy = access.containsKey(POLICY_NAME)
                    ? access.getString(POLICY_NAME)
                    : null;

            switch (policy)
            {
            case POLICY_VALUE_SAME_ORIGIN:
                newAccess = new HttpAccessControlConfig(SAME_ORIGIN);
                break;
            case POLICY_VALUE_CROSS_ORIGIN:
                JsonObject allow = access.containsKey(ALLOW_NAME)
                        ? access.getJsonObject(ALLOW_NAME)
                        : null;

                HttpAllowConfig newAllow = null;
                if (allow != null)
                {
                    JsonArray origins = allow.containsKey(ALLOW_ORIGINS_NAME)
                            ? allow.getJsonArray(ALLOW_ORIGINS_NAME)
                            : null;

                    Set<String> newOrigins = null;
                    if (origins != null)
                    {
                        Set<String> newOrigins0 = new LinkedHashSet<>();
                        origins.forEach(v -> newOrigins0.add(JsonString.class.cast(v).getString()));
                        newOrigins = newOrigins0;
                    }

                    JsonArray methods = allow.containsKey(ALLOW_METHODS_NAME)
                            ? allow.getJsonArray(ALLOW_METHODS_NAME)
                            : null;

                    Set<String> newMethods = null;
                    if (methods != null)
                    {
                        Set<String> newMethods0 = new LinkedHashSet<>();
                        methods.forEach(v -> newMethods0.add(JsonString.class.cast(v).getString()));
                        newMethods = newMethods0;
                    }

                    JsonArray headers = allow.containsKey(ALLOW_HEADERS_NAME)
                            ? allow.getJsonArray(ALLOW_HEADERS_NAME)
                            : null;

                    Set<String> newHeaders = null;
                    if (headers != null)
                    {
                        Set<String> newHeaders0 = new LinkedHashSet<>();
                        headers.forEach(v -> newHeaders0.add(JsonString.class.cast(v).getString()));
                        newHeaders = newHeaders0;
                    }

                    boolean newCredentials = false;
                    if (allow.containsKey(ALLOW_CREDENTIALS_NAME))
                    {
                        newCredentials = allow.getBoolean(ALLOW_CREDENTIALS_NAME);
                    }

                    newAllow = new HttpAllowConfig(newOrigins, newMethods, newHeaders, newCredentials);
                }

                Duration newMaxAge = null;

                JsonNumber maxAge = access.containsKey(MAX_AGE_NAME)
                        ? access.getJsonNumber(MAX_AGE_NAME)
                        : null;

                if (maxAge != null)
                {
                    newMaxAge = Duration.ofSeconds(maxAge.longValue());
                }

                HttpExposeConfig newExpose = null;

                JsonObject expose = access.containsKey(EXPOSE_NAME)
                        ? access.getJsonObject(EXPOSE_NAME)
                        : null;

                if (expose != null)
                {
                    JsonArray headers = expose.containsKey(ALLOW_HEADERS_NAME)
                            ? expose.getJsonArray(ALLOW_HEADERS_NAME)
                            : null;

                    Set<String> newHeaders = null;
                    if (headers != null)
                    {
                        Set<String> newHeaders0 = new LinkedHashSet<>();
                        headers.forEach(v -> newHeaders0.add(JsonString.class.cast(v).getString()));
                        newHeaders = newHeaders0;
                    }

                    newExpose = new HttpExposeConfig(newHeaders);
                }

                newAccess = new HttpAccessControlConfig(CROSS_ORIGIN, newAllow, newMaxAge, newExpose);
                break;
            }
        }

        JsonObject overrides = object.containsKey(OVERRIDES_NAME)
                ? object.getJsonObject(OVERRIDES_NAME)
                : null;

        Map<String8FW, String16FW> newOverrides = null;

        if (overrides != null)
        {
            Map<String8FW, String16FW> newOverrides0 = new LinkedHashMap<>();
            overrides.forEach((k, v) ->
                newOverrides0.put(new String8FW(k), new String16FW(JsonString.class.cast(v).getString())));
            newOverrides = newOverrides0;
        }

        return new HttpOptionsConfig(newVersions, newOverrides, newAccess, newAuthorization);
    }

    private List<HttpPatternConfig> adaptPatternFromJson(
        JsonObject object,
        String property)
    {
        List<HttpPatternConfig> newPatterns = null;
        if (object.containsKey(property))
        {
            newPatterns = new ArrayList<>();

            JsonObject patterns = object.getJsonObject(property);
            for (String name : patterns.keySet())
            {
                String pattern = patterns.getString(name);

                newPatterns.add(new HttpPatternConfig(name, pattern));
            }
        }
        return newPatterns;
    }
}
