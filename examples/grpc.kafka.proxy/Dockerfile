FROM ubuntu:22.04

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update \
	&& apt-get install --no-install-recommends nodejs npm git -y

WORKDIR /usr/app

ADD "https://api.github.com/repos/akrambek/grpc-echo/commits?per_page=1" latest_commit
RUN npm install git+https://github.com/akrambek/grpc-echo.git

EXPOSE 8080

ENTRYPOINT ["node", "/usr/app/node_modules/grpc-echo/server.js"]
