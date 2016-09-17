#!/bin/bash


# -----
# WINGS
# -----

WINGS_DIR='/opt/wings'

mkdir --parent ${WINGS_DIR}/storage/default ${WINGS_DIR}/server
chown -R tomcat:tomcat ${WINGS_DIR}

curl --output /etc/tomcat/Catalina/localhost/wings-portal.xml \
     "http://www.wings-workflows.org/downloads/docker/latest/portal/wings-portal.xml"

curl --output ${WINGS_DIR}/storage/default/portal.properties \
     "http://www.wings-workflows.org/downloads/docker/latest/portal/portal.properties"

cd ${WINGS_DIR}/server

curl --output wings-portal.war \
     "http://www.wings-workflows.org/downloads/docker/latest/portal/wings-portal.war"
unzip wings-portal.war
rm wings-portal.war

sed -i 's/Resource name="UserDatabase" auth/Resource name="UserDatabase" readonly="false" auth/' /etc/tomcat/server.xml
sed -i 's/<\/tomcat-users>/  <user username="admin" password="4dm1n!23" roles="WingsUser,WingsAdmin"\/>\n<\/tomcat-users>/' /etc/tomcat/tomcat-users.xml
