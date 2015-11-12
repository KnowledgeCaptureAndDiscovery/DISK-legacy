import optparse
import csv

import os
import authenticateLabkeyLogin
import labkeyFileUpload
import labkeyRunAnalysis


# First authenticate/login
# Upload the file to labkey
# Run the required search on the uploaded file! Default is ms2-xtandem


# Arguments for now:
## arg1: file to be uploaded
## arg2: tsv/configFileName file with the following information:
## pipeline (ms1/ms2), search name (Xtandem?), project name (container path on labkey), subdir (RAW?)
## hostname is supplied from labkey credentials file! Do we need to pass it in as an arg?


# upload the given input file to CPAS to the given subdir
def executeFileToLabkeyUpload(filePathAndName, baseUrl, \
                              containerPath, subDir):
    loginResults = labkeyFileUpload.uploadFileToLabkey(baseUrl, \
                                                       containerPath, \
                                                       subDir, \
                                                       filePathAndName)
    # print(loginResults)


def runLabkeyPipelineSearch(fileName, baseUrl, containerPath, \
                            subDir, protocolName, searchEngine, pipeline):
    pipelineResults = labkeyRunAnalysis.pipelineAnalysis(baseUrl, \
                                                         containerPath, \
                                                         protocolName, \
                                                         subDir, \
                                                         fileName, \
                                                         searchEngine, \
                                                         pipeline)
    return (pipelineResults)


# Assumption- Config file format: No header
# abs_filepath,pipelinetype,searchEngine,projectdir,subdir,protocolname
def getLabkeySearchParams(configFileName):
    inputFileNameWithParamsDict = {}
    with open(configFileName, 'r') as csvParamsFile:
        csvParamsFileRead = csv.reader(csvParamsFile, delimiter=",")

        for line in csvParamsFileRead:
            if int(len(line)) < 6:
                print "Please double check the parameters/config file info!"
                continue

            inputFilePathAndName, pipeline, searchEngine, \
            project, subDir, protocolName = line
            inputFileNameWithParamsDict[inputFilePathAndName] = [pipeline, searchEngine, project, \
                                                                 subDir, protocolName]
    csvParamsFile.close()
    return (inputFileNameWithParamsDict)


#
# Read command line options
#
def readAndParseCommandlineArgs():
    usage = "usage: %prog [options]   (Use -h or --help to see all options)\
          \nNOTE: Do not forget to create/copy the \".labkeycredentials.txt\" file as instructed in the README file"
    cl = optparse.OptionParser(usage=usage)
    cl.add_option('--config', '-c', action='store',
                  help="csv file containing filename and pipeline params",
                  dest="config")

    (options, args) = cl.parse_args()

    #
    # Check the command line options
    if options.config:
        if os.path.isfile(options.config):
            configFileName = options.config
            return (configFileName)
    else:
        cl.error("You must specify an input configFileName file to run the search. Use -h for more information.\n")


def main():
    configFileName = readAndParseCommandlineArgs()
    #  print(filePathAndName, configFileName)
    inputFileNameWithParamsDict = getLabkeySearchParams(configFileName)
    baseUrl, myusername, mypassword = authenticateLabkeyLogin._get_host_username_and_password()
    for inputFile in inputFileNameWithParamsDict.keys():
        pipeline, searchEngine, project, subDir, protocolName = inputFileNameWithParamsDict[inputFile]

        #    print(baseUrl, inputFile, pipeline, searchEngine, project, subDir, protocolName)
        executeFileToLabkeyUpload(inputFile, baseUrl, project, subDir)

        inputFileName = os.path.basename(inputFile)
        pipelineResults = runLabkeyPipelineSearch(inputFileName, baseUrl, \
                                                  project, subDir, \
                                                  protocolName, searchEngine, \
                                                  pipeline)


main()
