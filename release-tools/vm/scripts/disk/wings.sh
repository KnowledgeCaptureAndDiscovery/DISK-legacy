#!/bin/bash

set -e


# -----
# WINGS
# -----

EFS_DIR='/efs'
EFS_WINGS_DIR="${EFS_DIR}/wings"
EFS_PEGASUS_DIR="${EFS_DIR}/pegasus"
EFS_WINGS_SERVER_DIR="${EFS_WINGS_DIR}/server"

cat > /etc/tomcat/Catalina/localhost/wings-portal.xml << EOT
<Context docBase="${EFS_WINGS_SERVER_DIR}" debug="0" reloadable="true" crossContext="true">
   <Parameter name="config.file" value="${EFS_WINGS_SERVER_DIR}/portal.properties"/>
   <ResourceLink name="users" global="UserDatabase"/>
</Context>
EOT

sed -i 's/Resource name="UserDatabase" auth/Resource name="UserDatabase" readonly="false" auth/' /etc/tomcat/server.xml
sed -i 's/<\/tomcat-users>/  <user username="admin" password="4dm1n!23" roles="WingsUser,WingsAdmin"\/>\n<\/tomcat-users>/' /etc/tomcat/tomcat-users.xml
