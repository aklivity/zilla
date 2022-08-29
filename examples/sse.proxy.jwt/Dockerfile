FROM ubuntu:22.04

ENTRYPOINT ["/usr/app/node_modules/.bin/sse-server"]

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update \
	&& apt-get install --no-install-recommends nodejs npm git -y

WORKDIR /usr/app

ADD "https://api.github.com/repos/jfallows/sse-server/commits?per_page=1" latest_commit
RUN npm install git+https://github.com/jfallows/sse-server.git
