import optparse
import urllib2
import urllib

import authenticateLabkeyLogin


# taskId only needed for MS1 pipelines!
# taskId = 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:ms1FeaturePipeline'

# WIP!
# def get_xtandem_pipeline_status(baseUrl, containerPath, taskId, protocolName, subDir, fileName, searchEngine):
#  FileBasename = File.rstrip('.')
#  description = os.path.join(subDir, FileBasename) +\
#  ' (' + protocolName + ')'

def pipelineAnalysis(baseUrl, containerPath, protocolName, \
                     subDir, fileName, searchEngine, pipeline):
    pipelineUrl = ""
    values = ""
    if pipeline == "ms2":
        pipelineUrl, values = ms2PipelineAnalysis(baseUrl, containerPath, protocolName, subDir, fileName, searchEngine)
    elif pipeline == "ms1":
        taskId = 'org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:' + searchEngine
        pipelineUrl, values = ms1PipelineAnalysis(baseUrl, containerPath, protocolName, subDir, fileName, taskId)
    else:
        print "ERROR: Cannot find the given search type-" + searchEngine

    pipelineParams = urllib.urlencode(values)
    pipelineParams = pipelineParams.encode('utf-8')

    # print(pipelineUrl + pipelineParams)
    pipelineResults = authenticateLabkeyLogin.authenticateUrlRequests(pipelineUrl, \
                                                                      pipelineParams)
    return (pipelineResults)


def ms2PipelineAnalysis(baseUrl, containerPath, protocolName, subDir, fileName, searchEngine):
    values = {'path': subDir, \
              'file': fileName, \
              'protocol': protocolName, \
              'runSearch': 'true'}

    ms2PipelineUrl = baseUrl.rstrip('/') + \
                     "/ms2-pipeline/" + \
                     urllib2.quote(containerPath.strip('/')) + \
                     "/search" + searchEngine + ".api?"

    return (ms2PipelineUrl, values)


def ms1PipelineAnalysis(baseUrl, containerPath, protocolName, subDir, fileName, taskId):
    values = {'path': subDir, \
              'file': fileName, \
              'protocolName': protocolName, \
              'taskId': taskId}

    ms1PipelineUrl = baseUrl.rstrip('/') + \
                     "/pipeline-analysis/" + \
                     urllib2.quote(containerPath.strip('/')) + \
                     "/startAnalysis.api?"

    return (ms1PipelineUrl, values)


# Call the query (query_custom for now) API and kick off the pipeline
def executeAnalysis():
    fileName, baseUrl, containerPath, subDir, \
    protocolName, searchEngine, pipeline = readAndParseCommandlineArgs()

    pipelineResults = pipelineAnalysis(baseUrl, \
                                       containerPath, \
                                       protocolName, \
                                       subDir, \
                                       fileName, \
                                       searchEngine, \
                                       pipeline)
    # print(pipelineResults)


#
# Read command line options
#
def readAndParseCommandlineArgs():
    usage = "usage: %prog [options]   (Use -h or --help to see all options)"
    cl = optparse.OptionParser(usage=usage)
    cl.add_option('--file', '-f', action='store',
                  help="Filename to run the search (Note: No PATH information)", dest="file")
    cl.add_option('--protocol', '-m', action='store',
                  help="Protocol name to run on the file", dest="protocol")
    cl.add_option('--pipeline', '-l', action='store', default="ms2",
                  help="Type of pipeline (Options: ms1, ms2, default=ms2)", dest="pipeline")
    cl.add_option('--search', '-s', action='store',
                  help="Name of the search engine to run \
                (default: XTandem for ms2, \
                          ms1FeaturePipeline for ms1)", dest="search")
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

    if options.file and options.protocol:
        fileName = options.file
        baseUrl = options.labkey_url
        containerPath = options.project_name
        subDir = options.sub_directory
        protocolName = options.protocol
        pipeline = options.pipeline
        if options.search:
            searchEngine = options.search
        else:
            if pipeline == "ms1":
                searchEngine = "ms1FeaturePipeline"
            elif pipeline == "ms2":
                searchEngine = "XTandem"
            else:
                cl.error("Please select a pipeline to run!")
        return (fileName, baseUrl, containerPath, subDir, protocolName, searchEngine, pipeline)
    else:
        cl.error("You must specify a file and protocol to run the search. Use -h for more information.\n")


# Execute this if run from commandline only
if __name__ == "__main__":
    executeAnalysis()

########## TEMP:
# http://5800tof.stanford.edu:8080/labkey/ms2-pipeline/MallickTemp/searchXTandem.api?path=RAW&runSearch=true&protocol=EcoliSearch_Ravali&file=20141120_MA_ecoli2.

# http://5800tof.stanford.edu:8080/labkey/pipeline-analysis/MallickTemp/getSavedProtocols.api?taskId=org.labkey.ms2.pipeline.tandem.XTandemPipelineJob
# http://5800tof.stanford.edu:8080/labkey/pipeline-analysis/MallickTemp/getFileStatus.api?taskId=org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:ms1FeaturePipeline&file=20141120_MA_ecoli1.mzXML&protocolName=MS1TEST&path=RAW
# http://5800tof.stanford.edu:8080/labkey/pipeline-analysis/MallickTemp/startAnalysis.api?path=RAW&file=20141120_MA_ecoli3.mzXML&protocolName=MS1TEST&taskId=org.labkey.api.pipeline.file.FileAnalysisTaskPipeline:ms1FeaturePipeline
