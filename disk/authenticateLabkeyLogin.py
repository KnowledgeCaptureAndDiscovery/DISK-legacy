import urllib2
import base64
import pprint

import os
from poster.streaminghttp import register_openers


# Authenticate to send URL requests
def authenticateUrlRequests(myurl,
                            datagen=None,
                            headers=None,
                            method=None):
    opener, aHeader = _create_opener()

    # headers are given for file upload only a the moment.
    # Make it generic?
    if headers:
        myrequest = urllib2.Request(myurl, datagen, headers)
    else:
        myrequest = urllib2.Request(myurl, datagen)

    myrequest.add_header("Authorization", aHeader)

    # For now, using get_method mainly for MKCOL to create directory
    # However, keeping it generic for the future!
    if method:
        myrequest.get_method = lambda: method
    else:
        pass

    # Use the opener to fetch a URL request
    # If error, return error_message only. Not aborting!
    try:
        response = opener.open(myrequest)
    except urllib2.HTTPError, e:
        status = 1
        error_message = e.code  # "Error authenticating the url!"
        #        print e.read()
        #        exit(status)
        return (error_message)

    status = 0
    data = response.read()
    return (response)


# Create folders on the webserver using "MKCOL"
# def do_mkcol(baseUrl):

def _print_debug_info(data_dict, myurl, mydata=None):
    # Print the URL and any data used to query the server
    print myurl
    if mydata:
        print mydata

    # Review the results
    pp = pprint.PrettyPrinter(4)
    pp.pprint(data_dict)
    type(data_dict)

    # Look at the dictionary's keys
    mykeys = data_dict.keys()
    print mykeys

    # Look at the list of rows
    rowlist = data_dict['rows']
    # Look at the first row of data
    if rowlist:
        pp.pprint(rowlist[0])

    return


def _create_opener():
    """
    Create an opener and load the login and password into the object. The
    opener will be used when connecting to the LabKey Server
    """
    mymachine, myusername, mypassword = _get_host_username_and_password()

    # Create a password manager
    passmanager = urllib2.HTTPPasswordMgrWithDefaultRealm()

    # Add login info to the password manager
    passmanager.add_password(None, mymachine, myusername, mypassword)

    # Create the AuthHandler
    authhandler = urllib2.HTTPBasicAuthHandler(passmanager)

    # Create the Basic Authentication Header
    authHeader = base64.encodestring("%s:%s" % (myusername, mypassword))[:-1]
    authHeader = "Basic %s" % authHeader

    #    # Create opener
    #    opener = urllib2.build_opener(authhandler)
    # Create opener
    opener = register_openers()
    return opener, authHeader


def _get_host_username_and_password():
    # Check for credential file (which contains login and password for accessing
    # your LabKey Server) in either "LABKEY_CREDENTIALS" environment variable
    # or in the file .labkeycredentials.txt in your home directory
    try:
        credential_file_name = os.environ["LABKEY_CREDENTIALS"]
    except KeyError:
        credential_file_name = os.environ["HOME"] + '/.labkeycredentials.txt'

    f = open(credential_file_name, 'r')
    mymachine = f.readline().strip().split(' ')[1]
    myusername = f.readline().strip().split(' ')[1]
    mypassword = f.readline().strip().split(' ')[1]
    f.close()
    return (mymachine, myusername, mypassword)


def _decode_list(lst):
    # Helper function for dealing with unicode
    # Adapted from Stack Overflow:
    #   http://stackoverflow.com/questions/956867/how-to-get-string-objects-instead-unicode-ones-from-json-in-python
    #   Answer from Mike Brennan: http://stackoverflow.com/users/658138/mike-brennan
    #   Question from Brutus: http://stackoverflow.com/users/11666/brutus
    newlist = []
    for i in lst:
        if isinstance(i, unicode):
            i = i.encode('utf-8')
        elif isinstance(i, list):
            i = _decode_list(i)
        newlist.append(i)
    return newlist


def _decode_dict(dct):
    # Helper function for dealing with unicode
    # Adapted from Stack Overflow:
    #   http://stackoverflow.com/questions/956867/how-to-get-string-objects-instead-unicode-ones-from-json-in-python
    #   Answer from Mike Brennan: http://stackoverflow.com/users/658138/mike-brennan
    #   Question from Brutus: http://stackoverflow.com/users/11666/brutus
    newdict = {}
    for k, v in dct.iteritems():
        if isinstance(k, unicode):
            k = k.encode('utf-8')
        if isinstance(v, unicode):
            v = v.encode('utf-8')
        elif isinstance(v, list):
            v = _decode_list(v)
        newdict[k] = v
    return newdict
