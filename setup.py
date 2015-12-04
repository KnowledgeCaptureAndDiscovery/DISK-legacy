##
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
##

__author__ = 'Rajiv Mayani <mayani@isi.edu>'

import sys

import os
from setuptools import setup, find_packages

install_requires = [
    'labkey==0.4.1',
    'requests==2.8.1'
]


# Utility function to read the README file.
def read(fname):
    return open(os.path.join(os.path.dirname(__file__), fname)).read()


def find_package_data(dirname):
    def find_paths(dirname):
        items = []
        for fname in os.listdir(dirname):
            path = os.path.join(dirname, fname)
            if os.path.isdir(path):
                items += find_paths(path)
            elif not path.endswith('.py') and not path.endswith('.pyc'):
                items.append(path)
        return items

    items = find_paths(dirname)
    return [os.path.relpath(path, dirname) for path in items]


#
# Install conditional dependencies
#
def setup_installer_dependencies():
    global install_requires

    if sys.version_info[1] < 7:
        install_requires.append('argparse==1.4.0')


setup_installer_dependencies()

setup(
    name='wings-labkey-integration',
    version='0.3dev',
    author='Rajiv Mayani',
    author_email='mayani@isi.edu',
    description='Description',
    long_description=read('README.md'),
    license='Apache2',
    url='https://wings.org',
    classifiers=[
        'Topic :: Internet :: WWW/HTTP :: Application',
        'License :: OSI Approved :: Apache Software License',
        'Development Status :: 3 - Alpha',
        'Programming Language :: Python',
        'Programming Language :: Python :: 2.7',
        'Intended Audience :: Science/Research',
        'Operating System :: Unix'
    ],
    namespace_packages=[
        'disk'
    ],
    packages=find_packages(exclude=['disk.tests']),
    scripts=['bin/wings-labkey'],
    zip_safe=False,
    install_requires=install_requires,
    test_suite='disk.tests'
)
