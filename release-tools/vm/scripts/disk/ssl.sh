#!/bin/bash


# ---
# SSL
# ---

yum -y install openssl

mkdir --parent ${TOMCAT_HOME}/certs
openssl genrsa -des3 -passout pass:x -out server.pass.key 2048
openssl rsa -passin pass:x -in server.pass.key -out server.key
openssl req -new -key server.key -out server.csr -subj "/C=US/ST=CA/L=Marina Del Rey/O=ISI/CN=${HOSTNAME}"
openssl x509 -req -days 1000 -in server.csr -signkey server.key -out server.crt
mv server.crt ${TOMCAT_HOME}/certs/server.crt
mv server.key ${TOMCAT_HOME}/certs/server.key
rm --force server.pass.key server.csr
