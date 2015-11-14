#  Copyright 2007-2015 University Of Southern California
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

from __future__ import absolute_import

__author__ = 'Rajiv Mayani <mayani@isi.edu>'

import json
import logging
import ConfigParser
from functools import wraps
from urlparse import urlparse

import os
import requests
from requests.exceptions import MissingSchema
from disk.errors import LabkeyException, AuthenticationException

import labkey
from labkey.exceptions import ServerNotFoundError, QueryNotFoundError, RequestError


def file_exists(*files):
    missing = []

    for f in files:
        if not os.path.isfile(f):
            missing.append(os.path.abspath(f))

    if missing:
        if len(missing) == 1:
            msg = 'File %r either does not exist or is not readable' % ', '.join(missing)
        else:
            msg = 'File(s) %r either do not exist or are not readable' % ', '.join(missing)

        logging.getLogger(__name__).debug(msg)
        raise ValueError(msg)


class Labkey(object):
    def __init__(self, base_url=None, username=None, password=None, project=None, config=None):
        self._log = logging.getLogger(__name__)

        self._base_url = base_url
        self._username = username
        self._password = password
        self._project = project
        self._config = config

        self._session = None
        self.__configure_labkey(base_url=base_url, username=username, password=password, project=project, config=config)
        self._session = requests.Session()
        self._session.auth = (self._username, self._password)
        self._session.headers = {
            'Accept': 'application/json'
        }

        url = urlparse(self._base_url)

        self._context = {
            'domain': url.netloc,
            'container_path': self.project,
            'context_path': url.path.lstrip('/'),
            'scheme': url.scheme + '://',
            'session': self._session
        }

        del url

    #def __configure_labkey(self, base_url=None, username=None, password=None, project=None, config=None):
    def __configure_labkey(self, **kwargs):
        """
        Load configuration in priority from lowest to the highest
        .labkey-config.txt file in user's home directory
        .labkey-config.txt file in current directory
        User provided file in config option
        User provided options
        """
        config_files = [
            os.path.expanduser('~/.labkey-config.txt'),
            os.path.abspath('.labkey-config.txt')
        ]

        config = kwargs.get('config', None)

        if config:
            file_exists(config)
            config_files.append(os.path.abspath(config))

        config_parser = ConfigParser.ConfigParser()
        config_parser.read(config_files)

        self._config = config_parser

        required = ['base-url', 'username', 'password', 'project']

        # Highest priority override
        for v in required:
            key = v.replace('-', '_')
            if kwargs.get(key, None):
                self._config.set('default', v, kwargs[key])

        missing = []

        if self._config.has_section('default'):
            for k in required:
                try:
                    self._config.get('default', k)
                except ConfigParser.NoOptionError:
                    missing.append(k)
        else:
            missing = required

        if missing:
            self._log.debug('Required configuration options not found')
            raise ValueError('Missing configuration %s' % ', '.join(missing))

        self._base_url = self._config.get('default', 'base-url')
        self._username = self._config.get('default', 'username')
        self._password = self._config.get('default', 'password')
        self._project = self._config.get('default', 'project')

    @property
    def project(self):
        return self._project

    @project.setter
    def project(self, project):
        self._project = project
        self._context['container_path'] = project

    def _construct_url(self, *args):
        return os.path.join(self._base_url, *args)

    @staticmethod
    def _get_json(text):
        j = text.strip('<html><body><textarea>').strip('</textarea></body></html>')
        j = json.loads(j)
        return j

    def wrapper(f):
        @wraps(f)
        def decorator(*args, **kwargs):
            try:
                return f(*args, **kwargs)
            except MissingSchema as e:
                raise LabkeyException(e)

        return decorator

    @wrapper
    def upload_file(self, destination, input_file, create=False, overwrite=False):
        # Sanity Checks
        file_exists(input_file)

        files = {
            'file': (os.path.basename(input_file), open(input_file, 'rb').read())
        }

        params = {
            'Accept': 'application/json',
            'overwrite': 'T' if isinstance(overwrite, bool) and overwrite else 'F'
        }

        response = self._session.post(self._construct_url('_webdav', self.project, destination), files=files,
                                      params=params)
        self._log.debug('status_code %s, text %s' % (response.status_code, response.text))

        if response.status_code != 207 and response.text:
            text = self._get_json(response.text)

            if text['success'] is False:
                if text['status'] == 208:
                    raise LabkeyException(text['exception'])

                elif text['status'] == 401:
                    raise AuthenticationException('Authentication failed')

                elif text['status'] == 403:
                    raise AuthenticationException('Authorization failed')

                elif text['status'] == 404:
                    if create:
                        self.create_directory(destination)
                        self.upload_file(destination, input_file=input_file, create=False, overwrite=overwrite)

                    else:
                        raise LabkeyException('Either URL is invalid or the directory does not exist')

                else:
                    self._log.error(text)

    @wrapper
    def create_directory(self, destination):
        params = {
            'Accept': 'application/json'
        }

        dest = ''
        last_status = False

        for d in destination.split('/'):
            response = self._session.request('MKCOL', self._construct_url('_webdav', self.project, dest, d),
                                             params=params)
            self._log.debug('status_code %s, text %s' % (response.status_code, response.text))
            dest = os.path.join(dest, d)

            if response.text:
                text = self._get_json(response.text)

                if text['success'] is False:
                    if text['status'] == 208:
                        raise LabkeyException(text['exception'])

                    elif text['status'] == 401:
                        raise AuthenticationException('Authentication failed')

                    elif text['status'] == 403:
                        raise AuthenticationException('Authorization failed')

                    elif text['status'] == 404:
                        raise LabkeyException(text['exception'])

                    else:
                        self._log.error(response.text)

            else:
                last_status = True

        if last_status is False:
            raise LabkeyException('Directory creation failed')

    @wrapper
    def create_protocol(self, protocol_file, protocol_name=None):
        # Upload Protocol File
        pass

    def _configure_ms2_analysis(self, **kwargs):
        overrides = {}
        required = ['search-engine', 'input-location', 'fasta-location', 'protocol-location']

        # Highest priority override
        for v in required:
            key = v.replace('-', '_')
            if kwargs.get(key, None):
                overrides[v] = kwargs[key]

        required = ['search-engine', 'input-location', 'fasta-location', 'protocol-location']
        missing = []
        if self._config.has_section('ms2'):
            for k in required:
                try:
                    self._config.get('ms2', k, overrides)
                except ConfigParser.NoOptionError:
                    missing.append(k)
        else:
            missing = required

        if missing:
            self._log.debug('Required configuration options not found')
            raise ValueError('Missing configuration %s' % ', '.join(missing))

        return overrides

    def ms2_analysis(self, input_file, fasta_file, protocol_file, search_engine=None, input_location=None,
                     fasta_location=None, protocol_location=None):

        # Sanity Checks
        file_exists(input_file, fasta_file, protocol_file)

        overrides = self._configure_ms2_analysis(search_engine=search_engine, input_location=input_location,
                                                 fasta_location=fasta_location, protocol_location=protocol_location)
        print self._config.items('ms2', vars=overrides)

    def insert_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.insert_rows(self._context, *args, **kwargs)

    def update_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.update_rows(self._context, *args, **kwargs)

    def select_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        try:
            r = labkey.query.select_rows(self._context, *args, **kwargs)
            self._log.debug('Found %d rows' % r[u'rowCount'])
            return r

        except ServerNotFoundError as e:
            self._log.debug('Invalid URL %r, username %r, or password' % (self._base_url, self._username))
            raise LabkeyException('Invalid URL, username, or password')

        except QueryNotFoundError as e:
            self._log.debug('Invalid schema %r, or query %r' % (args[0], args[1]))
            raise LabkeyException('Invalid schema, or query')

    def execute_sql(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        try:
            r = labkey.query.execute_sql(self._context, *args, **kwargs)
            self._log.debug('Found %d rows' % r[u'rowCount'])
            return r

        except ServerNotFoundError as e:
            self._log.debug('Invalid URL %r, username %r, or password' % (self._base_url, self._username))
            raise LabkeyException('Invalid URL, username, or password')

        except (QueryNotFoundError, RequestError) as e:
            self._log.debug('Invalid schema %r, or query %r' % (args[0], args[1]))
            raise LabkeyException('Invalid schema, or query')

    def delete_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.delete_rows(self._context, *args, **kwargs)

    def __del__(self):
        if self._session:
            self._log.debug('closing_requests_session')
            self._session.close()
