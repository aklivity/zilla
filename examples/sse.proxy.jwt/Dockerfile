FROM ubuntu:22.04

ENTRYPOINT ["/usr/app/node_modules/.bin/sse-server"]

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update \
	&& apt-get install --no-install-recommends nodejs npm git -y

WORKDIR /usr/app

RUN npm install --save "git://github.com/jfallows/sse-server.git#14142e3"
