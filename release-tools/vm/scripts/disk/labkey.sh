#!/bin/bash

set -e


# ---------------------------------------------------------------------------------
# PostgreSQL
# http://www.liquidweb.com/kb/how-to-install-and-connect-to-postgresql-on-centos-7/
# ---------------------------------------------------------------------------------

groupadd --gid 26 postgres

useradd --comment "PostgreSQL Server" \
        --home-dir /var/lib/pgsql \
        --no-create-home \
        --uid 26 \
        --gid 26 \
        --shell /bin/bash \
        postgres

yum -y install https://download.postgresql.org/pub/repos/yum/9.5/redhat/rhel-7-x86_64/pgdg-centos95-9.5-2.noarch.rpm
yum -y install postgresql95-server

/usr/pgsql-9.5/bin/postgresql95-setup initdb
perl -pi.bak -e "s/^(host.*)ident/\1md5/g" /var/lib/pgsql/9.5/data/pg_hba.conf

systemctl enable  postgresql-9.5
systemctl start   postgresql-9.5

su - postgres --command psql << EOT
ALTER  USER     postgres     WITH ENCRYPTED PASSWORD '${ROOT_PASSWORD}';
CREATE USER     ${JDBC_USER} WITH ENCRYPTED PASSWORD '${JDBC_PASSWORD}';
CREATE DATABASE labkey;
GRANT  ALL PRIVILEGES ON DATABASE labkey TO labkey;
EOT

cat > ~root/.pgpass << EOT
localhost:*:*:postgres:${ROOT_PASSWORD}
EOT
chmod 600 ~root/.pgpass


# ---------------------------------------------------------------------------
# Labkey
# https://www.labkey.org/wiki/home/Documentation/page.view?name=manualInstall
# ---------------------------------------------------------------------------

TOMCAT_VERSION=`yum info tomcat | grep Version | cut -d':' -f2 | sed -e 's/ //g'`
curl --output ${TOMCAT_HOME}/lib/tomcat-dbcp-${TOMCAT_VERSION}.jar \
     http://central.maven.org/maven2/org/apache/tomcat/tomcat-dbcp/${TOMCAT_VERSION}/tomcat-dbcp-${TOMCAT_VERSION}.jar

mkdir --parent ${LABKEY_HOME}

curl --output ${LABKEY_VERSION}.tar.gz \
     http://labkey.s3.amazonaws.com/downloads/general/r/16.2/${LABKEY_VERSION}.tar.gz

tar --gzip --extract --verbose --file ${LABKEY_VERSION}.tar.gz
cd  ${LABKEY_VERSION}

cp tomcat-lib/{ant.jar,jtds.jar,mail.jar,mysql.jar,postgresql.jar,labkeyBootstrap.jar} ${TOMCAT_HOME}/lib/
cp --recursive --force {labkeywebapp,modules,pipeline-lib} ${LABKEY_HOME}
chown --recursive tomcat:tomcat ${LABKEY_HOME}/{labkeywebapp,modules}

sed -e "s#@@appDocBase@@#${LABKEY_HOME}\/labkeywebapp#g" \
    -e "s#@@jdbcUser@@#${JDBC_USER}#g" \
    -e "s#@@jdbcPassword@@#${JDBC_PASSWORD}#g" \
    labkey.xml > ${TOMCAT_HOME}/conf/Catalina/localhost/labkey.xml


cp ${TOMCAT_HOME}/conf/server.xml ${TOMCAT_HOME}/conf/server.xml.bak

sed -e 's#<Connector port="8080"#<Connector port="8080" compressableMimeType="text/html, text/xml, text/javascript, text/plain, text/css, application/json"#g' \
    -e 's#<Connector port="8080"#<Connector port="8080" noCompressionUserAgents="gozilla, traviata"#g' \
    -e 's#<Connector port="8080"#<Connector port="8080" compressionMinSize="2048"#g' \
    -e 's#<Connector port="8080"#<Connector port="8080" compression="on"#g' \
    -e 's#<Connector port="8080"#<Connector port="8080" URIEncoding="UTF-8"#g' \
    -e 's#<Connector port="8080"#<Connector port="8080" useBodyEncodingForURI="true"#g' \
    ${TOMCAT_HOME}/conf/server.xml.bak > ${TOMCAT_HOME}/conf/server.xml

cd ..
rm --recursive --force ${LABKEY_VERSION} ${LABKEY_VERSION}.tar.gz

systemctl restart tomcat


# -------------------------------------------------------------------------------
# Labkey Initialization
# https://www.labkey.org/home/Documentation/wiki-page.view?name=stagingServerTips
# -------------------------------------------------------------------------------

easy_install pip
pip install --upgrade pip setuptools

pip install selenium
curl --output phantomjs-${PHANTOMJS_VERSION}-linux-x86_64.tar.bz2 \
    --location "https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-${PHANTOMJS_VERSION}-linux-x86_64.tar.bz2"

tar --bzip2 --extract --verbose --file phantomjs-${PHANTOMJS_VERSION}-linux-x86_64.tar.bz2

mkdir ${LABKEY_HOME}/files
chown --recursive tomcat:tomcat ${LABKEY_HOME}/files

cat > /tmp/labkey-init.py << EOT
#!/usr/bin/env python

import unittest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


class LabkeyInit(unittest.TestCase):
    def setUp(self):
        self.base_url = 'http://localhost:8080/labkey'
        self.driver = self._get_driver()
        self.driver.set_window_size(1024, 768)

    def _get_driver(self):
        return webdriver.PhantomJS(service_args=['--ignore-ssl-errors=yes'])
        #return webdriver.Firefox()

    def __url(self, resource, base=None):
        base = base if base else self.base_url
        return '%s%s' % (base, resource)

    def account_setup(self):
        # Account Setup (/login-initialUser.view)

        self.driver.get(self.__url('/'))

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.ID, 'email')))

        email = self.driver.find_element_by_id('email')
        email.send_keys('varunr@isi.edu')

        password = self.driver.find_element_by_name('password')
        password.send_keys('pegasus123')

        password2 = self.driver.find_element_by_name('password2')
        password2.send_keys('pegasus123')

        next = self.driver.find_element_by_class_name('labkey-button')
        next.click()

        # Install Modules (/admin-moduleStatus.view)

        WebDriverWait(self.driver, 360).until(EC.visibility_of_element_located((By.CLASS_NAME, 'labkey-button')))

        next = self.driver.find_element_by_class_name('labkey-button')
        next.click()

        # Set Defaults (/admin-newInstallSiteSettings.view)

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.ID, 'rootPath')))

        root_path = self.driver.find_element_by_id('rootPath')
        root_path.clear()
        root_path.send_keys('/usr/local/labkey/files')

        error_reporting = self.driver.find_element_by_id('allowReporting')
        error_reporting.click()

        next = self.driver.find_element_by_class_name('labkey-button')
        next.click()

    def create_project_folder(self):
        # Create MS2 Folder

        self.driver.get(self.__url('/admin-createFolder.view'))

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.CLASS_NAME, 'x4-form-required-field')))

        folder = self.driver.find_element_by_class_name('x4-form-required-field')
        folder.send_keys('disk')

        ms2_label = self.driver.find_elements_by_xpath("//label[contains(text(), 'MS2')]")[0]
        ms2_label = ms2_label.get_attribute('id').replace('-boxLabelEl', '-inputEl')
        ms2_label = self.driver.find_element_by_id(ms2_label)
        ms2_label.click()

        next = self.driver.find_element_by_id('button-1052')
        next.click()

        # User Setup (/<folder-name>/admin-setFolderPermissions.view?wizard=true)

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.ID, 'button-1074')))

        next = self.driver.find_element_by_id('button-1074')
        next.click()

        # Finish (/<folder-name>/admin-setFolderPermissions.view?wizard=true)

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.ID, 'button-1081')))

        finish = self.driver.find_element_by_id('button-1081')
        finish.click()

    def enable_ssl_2(self):
        # Enable SSL (/admin-showAdmin.view)

        self.driver.get(self.__url('/admin-showAdmin.view'))

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.LINK_TEXT, 'site settings'.upper())))

        site_settings = self.driver.find_element_by_link_text('site settings'.upper())
        site_settings.click()

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.NAME, 'defaultDomain')))

        default_domain = self.driver.find_element_by_name('defaultDomain')
        default_domain.clear()

        base_server_url = self.driver.find_element_by_name('baseServerUrl')
        base_server_url.clear()
        base_server_url.send_keys('https://localhost:8080')

        ssl = self.driver.find_element_by_name('sslRequired')
        ssl.click()

        ssl_port = self.driver.find_element_by_name('sslPort')
        ssl_port.clear()
        ssl_port.send_keys('8080')

        pipeline_tools_dir = self.driver.find_element_by_name('pipelineToolsDirectory')
        new_value = '%s:%s' % (pipeline_tools_dir.text, '/root/TPP-4.8.0/bin')
        pipeline_tools_dir.clear()
        pipeline_tools_dir.send_keys(new_value)

        save = self.driver.find_elements_by_class_name('labkey-button')[0]
        save.click()

    def enable_ssl(self):
        # Enable SSL (/admin-showAdmin.view)

        self.driver.get(self.__url('/admin-showAdmin.view'))

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.LINK_TEXT, 'site settings'.upper())))

        site_settings = self.driver.find_element_by_link_text('site settings'.upper())
        site_settings.click()

        WebDriverWait(self.driver, 60).until(EC.visibility_of_element_located((By.CLASS_NAME, 'labkey-button')))

        save = self.driver.find_elements_by_class_name('labkey-button')[0]
        save.click()

    def logout(self):
        self.driver.get(self.__url('/home/login-logout.view?'))

    def test_init(self):
        try:
            self.account_setup()
            self.enable_ssl()
            self.create_project_folder()
            self.logout()
        except Exception as e:
            #self.driver.save_screenshot('ss.png')
            print self.driver.page_source
            print self.driver.current_url
            raise

    def tearDown(self):
        def close(driver):
            if driver:
                try:
                    driver.close()
                except:
                    pass

        close(self.driver)


if __name__ == '__main__':
    unittest.main()

EOT

chmod +x /tmp/labkey-init.py

PATH=`pwd`/phantomjs-${PHANTOMJS_VERSION}-linux-x86_64/bin:${PATH} /tmp/labkey-init.py

rm --recursive --force \
    phantomjs-${PHANTOMJS_VERSION}-linux-x86_64.tar.bz2 \
    phantomjs-${PHANTOMJS_VERSION}-linux-x86_64 \
    /tmp/labkey-init.py \
    ghostdriver.log


# -------------------------------------------------------------------------------
# https://www.labkey.org/home/Documentation/wiki-page.view?name=stagingServerTips
# -------------------------------------------------------------------------------

su - postgres --command "psql --dbname ${JDBC_USER}" << EOT
/*
UPDATE prop.properties p
SET    value = ''
WHERE  (SELECT s.category
        FROM   prop.propertysets s
        WHERE  s.set = p.set) = 'SiteConfig'
       AND p.name = 'defaultDomain';

UPDATE prop.properties p
SET    value = 'https://localhost:8443'
WHERE  (SELECT s.category
        FROM   prop.propertysets s
        WHERE  s.set = p.set) = 'SiteConfig'
       AND p.name = 'baseServerURL';

UPDATE prop.properties p
SET    value = TRUE
WHERE  (SELECT s.category
        FROM   prop.propertysets s
        WHERE  s.set = p.set) = 'SiteConfig'
       AND p.name = 'sslRequired';

UPDATE prop.properties p
SET    value = '8443'
WHERE  (SELECT s.category
        FROM   prop.propertysets s
        WHERE  s.set = p.set) = 'SiteConfig'
       AND p.name = 'sslPort';
*/

UPDATE prop.properties p
SET    value = value || ':/usr/local/tpp/bin'
WHERE  p.name = 'pipelineToolsDirectory';

EOT

systemctl restart tomcat

pip uninstall --yes selenium
