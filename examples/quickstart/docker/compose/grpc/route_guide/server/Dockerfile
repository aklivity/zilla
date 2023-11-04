FROM golang:1.19-alpine as build

WORKDIR /go/grpc-go
COPY . .

RUN go build server.go

FROM alpine
COPY --from=build /go/grpc-go/server .
ENTRYPOINT ["./server"]
