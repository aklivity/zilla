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

FROM ubuntu:24.04

RUN apt update
RUN DEBIAN_FRONTEND=noninteractive apt install -y tshark
RUN useradd -ms /bin/bash tshark

USER tshark
WORKDIR /home/tshark
