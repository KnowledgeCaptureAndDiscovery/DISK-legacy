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
from functools import wraps
from urlparse import urlparse

import os
import requests
from requests.exceptions import MissingSchema
from .errors import LabkeyException, AuthenticationException

import labkey


class Labkey(object):
    def __init__(self, base_url, username=None, password=None):
        self._base_url = base_url
        self._username = username
        self._password = password

        self._session = requests.Session()
        self._session.auth = (self._username, self._password)
        self._session.headers = {
            'Accept': 'application/json'
        }
        self._log = logging.getLogger(__name__)

        url = urlparse(self._base_url)

        self._context = {
            'domain': url.netloc,
            'container_path': 'Test',
            'context_path': url.path,
            'scheme': url.scheme + '://',
            'session': self._session
        }

        del url

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
    def upload_file(self, destination, files=None, create=False, overwrite=False):
        params = {
            'Accept': 'application/json',
            'overwrite': 'T' if isinstance(overwrite, bool) and overwrite else 'F'
        }

        response = self._session.post(self._construct_url('_webdav', destination), files=files, params=params)
        logging.debug('status_code %s, text %s' % (response.status_code, response.text))

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
                        self.upload_file(destination, files=files, create=False, overwrite=overwrite)

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
            response = self._session.request('MKCOL', self._construct_url('_webdav', dest, d), params=params)
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

    def insert_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.insert_rows(self._context, *args, **kwargs)

    def update_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.update_rows(self._context, *args, **kwargs)

    def select_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.select_rows(self._context, *args, **kwargs)

    def execute_sql(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.execute_sql(self._context, *args, **kwargs)

    def delete_rows(self, *args, **kwargs):
        """Proxy to Labkey's Python API"""
        return labkey.query.delete_rows(self._context, *args, **kwargs)

    def __del__(self):
        if self._session:
            self._log.debug('closing_requests_session')
            self._session.close()
