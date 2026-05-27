FROM golang:1.21 AS build
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /rag-server ./cmd/rag-server

FROM alpine:3.20
RUN apk add --no-cache ca-certificates wget
WORKDIR /
COPY --from=build /rag-server /rag-server
EXPOSE 8090
ENTRYPOINT ["/rag-server"]
