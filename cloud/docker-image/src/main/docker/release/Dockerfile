#
# Copyright 2021-2023 Aklivity Inc
#
# Licensed under the Aklivity Community License (the "License"); you may not use
# this file except in compliance with the License.  You may obtain a copy of the
# License at
#
#   https://www.aklivity.io/aklivity-community-license/
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OF ANY KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations under the License.
#

FROM eclipse-temurin:20-jdk AS build

RUN apt update && apt install -y gettext

COPY maven /root/.m2/repository

COPY zpmw zpmw
COPY zpm.json.template zpm.json.template

RUN cat zpm.json.template | env VERSION=${project.version} envsubst > zpm.json

RUN ./zpmw install --debug --exclude-remote-repositories
RUN ./zpmw clean --keep-image

FROM ubuntu:jammy-20231128

ENV ZILLA_VERSION ${project.version}

COPY --from=build /.zpm /opt/zilla/.zpm
COPY --from=build /zilla /opt/zilla/zilla
COPY zilla.properties /opt/zilla/.zilla/zilla.properties

ENTRYPOINT ["/opt/zilla/zilla"]
