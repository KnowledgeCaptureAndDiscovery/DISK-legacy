#
# Copyright (c) 2013-2014 LabKey Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""
############################################################################
NAME:
upload_file
SUMMARY:
This program will upload a file to your LabKey Server.
DESCRIPTION:
This program is designed to show how a file can be uploaded to a
LabKey Server using Python.
USAGE:
upload_file.py -f|--file filename
where
    -f, --file : File to be uploaded to LabKey Server
In order to upload a file to the LabKey Server you will need provide
credentials. This script assumes that you will provide login credentials
using a credential file.
See https://www.labkey.org/wiki/home/Documentation/page.view?name=setupPython
for instructions on creating the credential file.
IMPORTANT: In the Variables section below, change the URL, Project and
Folder Path to point to your LabKey Server.
If you have any questions or need assistance customizing the script for
your use then post a message to the Developer Support Forums at
https://www.labkey.org/project/home/Server/Forum/begin.view?
############################################################################
"""

import sys
import optparse

import os
from poster.encode import multipart_encode
import authenticateLabkeyLogin


# Upload the file to the LabKey Server running at baseUrl
def uploadFileToLabkey(baseUrl, containerPath, subDir, fileNameAndPath):
    if os.path.isfile(fileNameAndPath):
        # URL to be used to be used for uploading the file
        fileBaseUrl = baseUrl.rstrip('/') + \
                      "/_webdav/" + \
                      containerPath + \
                      "/@files/"

        fileuploadUrl = fileBaseUrl + subDir

        # The next step is to create the multipart/form-data postdata.
        # Start the multipart/form-data encoding of the download_file_path.
        # - headers contains the necessary Content-Type and Content-Length
        # - datagen is a generator object that yields the encoded parameters
        try:
            datagen, headers = multipart_encode({"filename": open(fileNameAndPath, "rb")})
        except IOError as e:
            status = 1
            error_message = "There was an error during encoding of file. Error message = " + format(e)
            print error_message
            sys.exit(status)

        # Check if the url (with the given subdir to upload file) exists
        # Create the subdir if doesn't exist already
        # Using MKCOL from webdav for this
        checkFileuploadUrlexists = authenticateLabkeyLogin.authenticateUrlRequests(myurl=fileuploadUrl)

        if (checkFileuploadUrlexists == 404):
            createDirResponse = authenticateLabkeyLogin.authenticateUrlRequests(myurl=fileuploadUrl,
                                                                                method='MKCOL')
            print createDirResponse
            print("Creating subdirectory: " + subDir + " under " + fileBaseUrl)

        print "Uploading", fileNameAndPath, "to", fileuploadUrl
        # Get authenticated to upload the file.
        loginResults = authenticateLabkeyLogin.authenticateUrlRequests(fileuploadUrl, \
                                                                       datagen, \
                                                                       headers)

        fileName = os.path.basename(fileNameAndPath)
        filepathUrl = fileuploadUrl + "/" + fileName
        # Upload was a success. Print the URL of the newly uploaded file.
        print "The file upload was successful. You can see the uploaded file at " + filepathUrl + "\n"

        return (loginResults, filepathUrl)
    else:
        print filename, "does NOT exist! Please double-check!"


def executeFileUpload():
    fileNameAndPath, baseUrl, containerPath, subDir = readAndParseCommandlineArgs()

    loginResults, filepathUrl = uploadFileToLabkey(baseUrl, \
                                                   containerPath, \
                                                   subDir, \
                                                   fileNameAndPath)


#
# Read command line options
#
def readAndParseCommandlineArgs():
    usage = "usage: %prog [options]   (Use -h or --help to see all options)"
    cl = optparse.OptionParser(usage=usage)
    cl.add_option('--file', '-f', action='store',
                  help="Read the content for the message from this file", dest="file")
    cl.add_option('--project', '-p', action='store',
                  help="Name of the project on the labkey to upload the file (default=MallickTemp)",
                  default="MallickTemp", dest="project_name")
    cl.add_option('--subdir', '-d', action='store',
                  help="Name of the subdir under the project to upload the file (default=RAW)",
                  default="RAW", dest="sub_directory")
    cl.add_option('--url', '-u', action='store',
                  help="The url to the local server running labkey (default=http://5800tof.stanford.edu:8080/labkey/)",
                  default="http://5800tof.stanford.edu:8080/labkey/", dest="labkey_url")
    (options, args) = cl.parse_args()

    #
    # Check the command line options
    if not options.file:
        cl.error("You must specify a file to be uploaded. Use -h for more information.\n")

    if options.file:
        if os.path.isfile(options.file):
            fileNameAndPath = options.file
            baseUrl = options.labkey_url
            containerPath = options.project_name
            subDir = options.sub_directory
            return (fileNameAndPath, baseUrl, containerPath, subDir)
        else:
            cl.error("The file specified on the command line does not exist or cannot be accessed. \n")


# Execute this if run from commandline only
if __name__ == "__main__":
    executeFileUpload()
