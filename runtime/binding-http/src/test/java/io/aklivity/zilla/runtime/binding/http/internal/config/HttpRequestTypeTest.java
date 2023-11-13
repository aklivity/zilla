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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.regex.Matcher;

import org.junit.Test;

public class HttpRequestTypeTest
{
    @Test
    public void shouldParsePath()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathWithSlash()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield/";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathWithQuestionMark()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield?";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathWithSlashAndQuestionMark()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield/?";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathAndQuery()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield?hello=ciao&day=nap&answer=42";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(true));
        assertThat(queryMatcher.group(1), equalTo("hello"));
        assertThat(queryMatcher.group(2), equalTo("ciao"));

        queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(true));
        assertThat(queryMatcher.group(1), equalTo("day"));
        assertThat(queryMatcher.group(2), equalTo("nap"));

        queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(true));
        assertThat(queryMatcher.group(1), equalTo("answer"));
        assertThat(queryMatcher.group(2), equalTo("42"));

        queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathWithSlashAndQuery()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield/?answer=42";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(true));
        assertThat(queryMatcher.group(1), equalTo("answer"));
        assertThat(queryMatcher.group(2), equalTo("42"));

        queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathAndQueryWithAmpersand()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield/?answer=42&";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(true));
        assertThat(queryMatcher.group(1), equalTo("answer"));
        assertThat(queryMatcher.group(2), equalTo("42"));

        queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathAndIncompleteQuery1()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield/?hello=ciao&answer=";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(true));
        assertThat(queryMatcher.group(1), equalTo("hello"));
        assertThat(queryMatcher.group(2), equalTo("ciao"));

        queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }

    @Test
    public void shouldParsePathAndIncompleteQuery2()
    {
        // GIVEN
        String configPath = "/valid/{category}/{id}";
        String actualPath = "/valid/cat/garfield/?hello=ciao&answer";
        HttpRequestType requestType = HttpRequestType.builder()
            .path(configPath)
            .build();

        // WHEN
        Matcher pathMatcher = requestType.pathMatcher.reset(actualPath);
        Matcher queryMatcher = requestType.queryMatcher.reset(actualPath);

        // THEN
        boolean pathMatches = pathMatcher.matches();
        assertThat(pathMatches, equalTo(true));
        assertThat(pathMatcher.group("category"), equalTo("cat"));
        assertThat(pathMatcher.group("id"), equalTo("garfield"));

        boolean queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(true));
        assertThat(queryMatcher.group(1), equalTo("hello"));
        assertThat(queryMatcher.group(2), equalTo("ciao"));

        queryFound = queryMatcher.find();
        assertThat(queryFound, equalTo(false));
    }
}
