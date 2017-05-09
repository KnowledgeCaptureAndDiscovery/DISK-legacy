#!/bin/bash

set -e


# -----
# WINGS
# -----

WINGS_DIR='/opt/wings'
EFS_DIR='/efs'
EFS_WINGS_DIR="${EFS_DIR}/wings"
EFS_PEGASUS_DIR="${EFS_DIR}/pegasus"

mkdir --parent ${WINGS_DIR}/server
chown --recursive tomcat:tomcat ${WINGS_DIR}

cat > /etc/tomcat/Catalina/localhost/wings-portal.xml << EOT
<Context docBase="${WINGS_DIR}/server" debug="0" reloadable="true" crossContext="true">
   <Parameter name="config.file" value="${WINGS_DIR}/server/portal.properties"/>
   <ResourceLink name="users" global="UserDatabase"/>
</Context>
EOT

cat > ${WINGS_DIR}/server/portal.properties <<EOT
{
    storage =
    {
        local = ${EFS_WINGS_DIR}/storage/default;
        tdb   = ${EFS_WINGS_DIR}/storage/default/TDB;
    }

    server         = http://localhost:8080;
    graphviz       = /usr/bin/dot;

    ontology =
    {
        data = http://www.wings-workflows.org/ontology/data.owl;
        component = http://www.wings-workflows.org/ontology/component.owl;
        workflow = http://www.wings-workflows.org/ontology/workflow.owl;
        execution = http://www.wings-workflows.org/ontology/execution.owl;
    }

    execution =
    {
        engine =
        {
            name = Local;
            implementation = edu.isi.wings.execution.engine.api.impl.local.LocalExecutionEngine;
            type = BOTH;
        }

        engine =
        {
            name = Distributed;
            implementation = edu.isi.wings.execution.engine.api.impl.distributed.DistributedExecutionEngine;
            type = BOTH;
        }


        engine =
        {
            name = Pegasus;
            implementation = edu.isi.wings.execution.engine.api.impl.pegasus.PegasusExecutionEngine;
            type = BOTH;
            properties =
            {
                pegasus =
                {
                    home         = /usr;
                    storage-dir  = ${EFS_PEGASUS_DIR}/storage;
                    site-catalog = /etc/pegasus/local.sites.xml;
                    site         = condor_pool;
                }
            }
        }
    }
}
EOT

cd ${WINGS_DIR}/server

curl --output wings-portal.war \
     "http://www.isi.edu/~mayani/wings-portal.war"

unzip wings-portal.war
rm --force wings-portal.war

sed -i 's/Resource name="UserDatabase" auth/Resource name="UserDatabase" readonly="false" auth/' /etc/tomcat/server.xml
sed -i 's/<\/tomcat-users>/  <user username="admin" password="4dm1n!23" roles="WingsUser,WingsAdmin"\/>\n<\/tomcat-users>/' /etc/tomcat/tomcat-users.xml
