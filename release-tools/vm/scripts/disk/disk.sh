#!/bin/bash


# ----
# DISK
# ----

cat >> /etc/tomcat/tomcat.conf <<EOT

# https://www.labkey.org/home/Documentation/Archive/16.1/wiki-page.view?name=configWebappMemory
CATALINA_OPTS = -Xms128m -Xmx2048m -XX:-HeapDumpOnOutOfMemoryError
EOT

systemctl restart tomcat

