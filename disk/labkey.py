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

import re
import json
import time
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
    def __init__(self, base_url=None, username=None, password=None, project_name=None, config=None):
        self._log = logging.getLogger(__name__)

        self._base_url = base_url
        self._username = username
        self._password = password
        self._project_name = project_name
        self._config = config

        self._session = None
        self.__configure_labkey(base_url=base_url, username=username, password=password, project_name=project_name,
                                config=config)
        self._session = requests.Session()
        self._session.auth = (self._username, self._password)
        self._session.headers = {
            'Accept': 'application/json'
        }

        url = urlparse(self._base_url)

        self._context = {
            'domain': url.netloc,
            'container_path': self.project_name,
            'context_path': url.path.lstrip('/'),
            'scheme': url.scheme + '://',
            'session': self._session
        }

        del url

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

        required = ['base-url', 'username', 'password', 'project-name']

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
        self._project_name = self._config.get('default', 'project-name')

    @property
    def project_name(self):
        return self._project_name

    @project_name.setter
    def project_name(self, project_name):
        self._project_name = project_name
        self._context['container_path'] = project_name

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
    def check_location_exists(self, location):
        params = {
            'Accept': 'application/json',
        }
        response = self._session.request('PROPFIND',
                                         self._construct_url('_webdav', self.project_name, '@files', location),
                                         params=params
                                         )
        self._log.debug('status_code %s, text %s' % (response.status_code, response.text))

        if response.status_code == 200:
            try:
                text = self._get_json(response.text)

                if text['status'] == 401:
                    raise AuthenticationException('Authentication failed')

                elif text['status'] == 403:
                    raise AuthenticationException('Authorization failed')

                elif text['status'] == 404:
                    return False

            except AuthenticationException:
                raise

            except:
                self._log.debug('exception ignored...')

        return True

    @wrapper
    def upload_file(self, destination, input_file, create=False, overwrite=False):
        # Sanity Checks
        file_exists(input_file)

        params = {
            'Accept': 'application/json',
        }

        file_exist = self.check_location_exists(os.path.join(destination, os.path.basename(input_file)))

        if overwrite is False and file_exist is True:
            raise LabkeyException('File already exists, try setting overwrite flag')

        if create is False and not self.check_location_exists(destination):
            raise LabkeyException('Either URL is invalid or the directory does not exist')

        response = self._session.put(
            self._construct_url('_webdav', self.project_name, '@files', destination, os.path.basename(input_file)),
            data=open(input_file, 'rb'),
            params=params
        )

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
                        self.create_directory(os.path.join('@files', destination))
                        self.upload_file(destination, input_file=input_file, create=False, overwrite=overwrite)

                    else:
                        raise LabkeyException('Either URL is invalid or the directory does not exist')

                else:
                    self._log.error(text)

    @wrapper
    def download_file(self, destination, file_location, create=False, overwrite=False):
        response = self._session.get(self._construct_url('_webdav', self.project_name, '@files', file_location))
        self._log.debug('status_code %r' % (response.status_code))

        if response.status_code != 200:
            raise LabkeyException('Error HTTP Code %d' % response.status_code)

        if len(response.text) < 150:
            # Labkey Webdav return 200 even on errors
            # So we try to determine if the content represents an error response
            # Error response is JSON, with success, and status keys
            try:
                text = self._get_json(response.text)

                if 'success' in text and 'status' in text:
                    if text['success'] is False:

                        if text['status'] == 401:
                            raise AuthenticationException('Authentication failed')

                        elif text['status'] == 403:
                            raise AuthenticationException('Authorization failed')

                        elif text['status'] == 404:
                            raise LabkeyException('File not found on server')

                        else:
                            self._log.debug(response.text)

            except ValueError:
                pass

        if not os.path.isdir(destination):
            if create:
                os.makedirs(destination)
            else:
                raise LabkeyException(
                    'Destination directory %s does not exist, to create directory set create flag' % os.path.abspath(
                        destination))

        file_path = '%s' % os.path.join(destination, os.path.basename(file_location))
        if os.path.isfile(file_path):
            if not overwrite:
                raise LabkeyException(
                    'File %s already exists, to overwrite the file set overwrite flag' % os.path.abspath(file_path))

        with open(file_path, 'wb') as f:
            f.write(response.text)

    @wrapper
    def download_other_file(self, destination, file_location, create=False, overwrite=False):
        response = self._session.get(
            self._construct_url(file_location % {'project-name': self.project_name}))
        self._log.debug('status_code %r' % (response.status_code))

        if response.status_code != 200:
            raise LabkeyException('Error HTTP Code %d' % response.status_code)

        if response.history:
            # Labkey Webdav return 200 even on errors
            # So we try to determine if the content represents an error response
            last = response.history[-1]

            if 300 <= last.status_code < 400 and (
                last.headers.get('location', None) and '/login.view?' in last.headers.get('location')):
                raise AuthenticationException('Authentication failed')

        if not os.path.isdir(destination):
            if create:
                os.makedirs(destination)
            else:
                raise LabkeyException(
                        'Destination directory %s does not exist, to create directory set create flag' % os.path.abspath(
                                destination))

        output_file = os.path.basename(file_location)

        if response.headers.get('content-disposition', None):
            output_file = response.headers['content-disposition'].strip()

            p = re.match('^filename\w*=\w*"(.*)";?.*$', output_file)

            if p:
                output_file = p.group(1)
            else:
                output_file = os.path.basename(file_location)

        file_path = os.path.join(destination, output_file)
        self._log.info('Downloading to filename %s' % file_path)
        if os.path.isfile(file_path):
            if not overwrite:
                raise LabkeyException(
                        'File %s already exists, to overwrite the file set overwrite flag' % os.path.abspath(file_path))

        with open(file_path, 'wb') as f:
            f.write(response.content)

    @wrapper
    def create_directory(self, destination):
        params = {
            'Accept': 'application/json'
        }

        dest = ''
        last_status = False

        for d in destination.split('/'):
            response = self._session.request('MKCOL', self._construct_url('_webdav', self.project_name, dest, d),
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
                        self._log.debug(response.text)

            else:
                last_status = True

        if last_status is False:
            raise LabkeyException('Directory creation failed')

    def _configure_ms2_analysis(self, **kwargs):
        overrides = {}
        required = ['search-engine', 'input-location', 'fasta-location', 'protocol-location', 'poll-duration']

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

    def _trigger_ms2_analysis(self, input_file, input_location, protocol_name, search_engine, run_search=True):
        params = {
            'path': input_location,
            'file': [os.path.basename(one_input_file) for one_input_file in input_file],
            'protocol': protocol_name,
            'runSearch': run_search,
            'Accept': 'application/json'
        }

        search_engine_map = {
            'xtandem': 'XTandem'
        }

        response = self._session.post(
            self._construct_url('ms2-pipeline', self.project_name, 'search%s.api' % search_engine_map[search_engine]),
            params=params)
        self._log.debug('status_code %s, text %s' % (response.status_code, response.text))

        if response.status_code == 401:
            raise AuthenticationException('Authentication failed')

        elif response.status_code == 403:
            raise AuthenticationException('Authorization failed')

        elif response.status_code == 404:
            raise LabkeyException('Either URL is invalid or the directory does not exist')

    def ms2_analysis(self, input_file, fasta_file, protocol_file, search_engine=None, input_location=None,
                     fasta_location=None, protocol_location=None, upload=True):
        section = 'ms2'

        overrides = self._configure_ms2_analysis(search_engine=search_engine, input_location=input_location,
                                                 fasta_location=fasta_location, protocol_location=protocol_location)

        search_engine = self._config.get(section, 'search-engine', vars=overrides)
        input_location = self._config.get(section, 'input-location', vars=overrides)
        fasta_location = self._config.get(section, 'fasta-location', vars=overrides)
        protocol_location = self._config.get(section, 'protocol-location', vars=overrides)

        if upload is True:
            # Upload input file
            for one_input_file in input_file: 
                self._log.debug('uploading input-file %r to server at %r' % (one_input_file, input_location))
                self.upload_file(input_location, one_input_file, create=True, overwrite=True)
                self._log.info('successfully uploaded input-file')

            # Upload FASTA file
            self._log.debug('uploading fasta-file %r to server at %r' % (fasta_file, fasta_location))
            self.upload_file(fasta_location, fasta_file, create=True, overwrite=True)
            self._log.info('successfully uploaded fasta-file')

            # Upload protocol file
            self._log.debug('uploading protocol-file %r to server at %r' % (protocol_file, protocol_location))
            self.upload_file(protocol_location, protocol_file, create=True, overwrite=True)
            self._log.info('successfully uploaded protocol-file')

        else:
            self._log.info('upload is set to False, skipping file uploads')

        try:
            # Trigger Run
            protocol_name = os.path.splitext(os.path.basename(protocol_file))[0]
            input_name = ""
            input_file_prefix = ""
            if len(input_file) <=1:
		one_input_file=input_file[0]
                input_file_prefix = os.path.splitext(os.path.basename(one_input_file))[0]
		input_name = input_location #temp
		# If only one file is present in directory, labkey jobname: dest_dirname (protocol_name)
		# If > 1 files, then labkey jobname: dest_dirname/fileprefix (protocol_name)
		#dest_dir_url = self._session.get(self._construct_url('_webdav', self.project_name, '@files', input_location))
		#headers = {'Depth': '1'}
		#dest_dir_url = self._session.request('PROPFIND',
                #                         	self._construct_url('_webdav', self.project_name, '@files', input_location), headers=headers)
		#print dest_dir_url.content
		print input_file_prefix
		#count_files_in_dest_dir = len([filename for filename in os.listdir(dest_dir_url) if os.path.isfile(os.path.join(dest_dir_url, filename))])
		#if count_files_in_dest_dir <=1:
		#    input_name = input_location	
		#else: 
                #    input_name = os.path.join(input_location, input_file_prefix)
            else:
		input_file_prefix = "all"
                input_name = input_location

            self._log.info('triggering MS2 analysis run with search-engine %r' % search_engine)
            self._trigger_ms2_analysis(input_file, input_location, protocol_name, search_engine)

            rv = (
                '%s (%s)' % (input_name, protocol_name),
                os.path.join(input_location, search_engine, protocol_name,
                             input_file_prefix)
            )

            return rv

        except Exception as e:
            self._log.exception(e)

    def ms2_status(self, *jobs, **kwargs):
        # Remove duplicates
        jobs = set(jobs)

        if not jobs:
            raise ValueError('job_name(s) are required')

        schema = 'Pipeline'
        sql = "SELECT * FROM Job WHERE Description IN ('%s')" % "', '".join(jobs)
        watch = kwargs.get('watch', False)

        if watch:
            poll_interval = kwargs.get('poll_interval', 5)

            if not poll_interval:
                poll_interval = 5

            is_warned = False

            self._log.debug('poll_interval %d second(s)' % poll_interval)
            self._log.debug('schema %r, sql %r' % (schema, sql))

            while True:
                data = self.execute_sql(schema, sql=sql)

                if is_warned is False and data['rowCount'] != len(jobs):
                    self._log.warning('unable to get status for some jobs')
                    is_warned = True

                completed = 0
                for row in data['rows']:
                    job_name = row['Description']
                    status = row['Status']

                    self._log.debug('Job %s, Status %s' % (job_name, status))

                    if status in set(['COMPLETE', 'ERROR']):
                        self._log.debug('Job %r finished, exiting' % job_name)
                        completed += 1

                        if completed == len(jobs):
                            return data

                    time.sleep(poll_interval)

        else:
            data = self.execute_sql(schema, sql=sql)
            return data

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
            self._log.debug('Found %d rows' % r['rowCount'])
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
            self._log.debug('Found %d rows' % r['rowCount'])
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
