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
            self.create_project_folder()
            #self.enable_ssl()
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
