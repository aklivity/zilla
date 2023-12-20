FROM ubuntu:24.04

RUN apt update
RUN DEBIAN_FRONTEND=noninteractive apt install -y tshark
RUN useradd -ms /bin/bash tshark

USER tshark
WORKDIR /home/tshark
