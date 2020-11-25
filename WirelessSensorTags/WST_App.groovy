/*
 *	Hubitat Import URL: https://github.com/asmuts/hubitat/blob/master/WirelessSensorTags/WST_App.groovy
 *
 */
definition(
    name: 'Wireless Sensor Tags App',
    namespace: 'asmuts',
    author: 'asmuts',
    description: 'Wireless Sensor Tags Hubitat App',
    category: 'Convenience',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
    oauth: true
)

// Present Auth page. Once Auth is done, prensent Devices page.
preferences {
    page(name: 'auth', title: 'Wireless Sensor Tags Access', nextPage:'deviceList', content:'authPage', uninstall: true)
    page(name: 'deviceList', title: 'Wireless Sensor Tags Listing', content:'wirelessDeviceList', install:true)
}

mappings {
    path('/accessGranted') {
        action: [
            GET: 'getAccessToken'
        ]
    }
    // Consume events from WST via this URL:
    path('/wstcallback') {
        action: [
            GET: 'consumeWSTEvent',
            POST: 'consumeWSTEvent'
        ]
    }
}

////////////////////////////////////////////////////////////////////////////////////

/*
    WST publishes events to the callback url.
    The Hubitat WST app consumes them here.
    We register listeners for a variety of events. WST publishes
    to this endpoint.

    Get the tag identifier.
    Find the child device (the Hubitat device for the tag).
    Generate an event on the device.
*/
def consumeWSTEvent () {
    logTrace "consumeWSTEvent: $params"

    def id = params.id.toInteger()
    def type = params.type

    def dni = getTagUUID(id)

    if (dni) {
        def d = getChildDevice(dni)

        if (d) {
            def data = null

            switch (type) {
                case 'oor': data = [presence: 'not present']; break
                case 'back_in_range': data = [presence: 'present']; break
                case 'motion_detected': data = [acceleration: 'active', motion: 'active']; break

                // AS-timeout callback seems to be fine as of 11/03/2016
                // There's no need for motion timeout checking. polling will handle failures
                case 'motion_timedout': data = [acceleration: 'inactive', motion: 'inactive']; break

                case 'door_opened': data = [contact: 'open']; break
                case 'door_closed': data = [contact: 'closed']; break
                case 'water_detected': data = [water : 'wet']; break
                case 'water_dried': data = [water : 'dry']; break
            }

            logTrace 'callback action = ' + data

            if (data) {
                d.generateEvent(data)
            }
        }
    }
}

/*
    Note: we distinguish between the Hubitat access token
    and the access token provided by WST.  The WST token is stored
    on the state as authToken

    https://docs.hubitat.com/index.php?title=App_OAuth

    The first step to supporting OAuth in your app is to enable it
    following the directions listed at
        https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps

    You will then need to create an access token done using createAccessToken().
    Calling this method will automatically store the access token in `state.accessToken`.

    You will next need to redirect the user to the API's OAuth endpoint.
    The URL should be provided by the vendor.
*/
def authPage() {
    if (!atomicState.accessToken) {
        logDebug 'No Hubitat access token found. Creating access token'
        createAccessToken()
        atomicState.accessToken = state.accessToken
    }

    // oauth docs = http://www.mytaglist.com/eth/oauth2_apps.html
    def description = 'Required'
    def uninstallAllowed = false
    def oauthTokenProvided = false

    if (atomicState.authToken) {
        logDebug('WST Auth Token present.')
        description = 'You are connected.'
        uninstallAllowed = true
        oauthTokenProvided = true
    }

    def redirectUrl = buildOauthInitUrl()
    logDebug "RedirectUrl = ${redirectUrl}"

    // get rid of next button until the user is actually auth'd
    if (!oauthTokenProvided) {
        return dynamicPage(name: 'auth', title: 'Login', nextPage:'auth', uninstall:uninstallAllowed) {
            section() {
                paragraph 'Tap below to log in to the Wireless Sensor Tags service and authorize Hubitat access.'
                href url:redirectUrl, style:'embedded', required:true, title:'Wireless Sensor Tags', description:description
            }
        }
    } else {
        return dynamicPage(name: 'auth', title: 'Setup Devices', nextPage:'deviceList', uninstall:uninstallAllowed) {
            section() {
                paragraph 'Tap Next to continue to setup your devices.'
                href url:redirectUrl, style:'embedded', state:'complete', title:'Wireless Sensor Tags', description:description
            }
        }
    }
}

/*
    Returns a dynamic page with a list of devices.
    This is displayed during the setup process.
*/
def wirelessDeviceList() {
    def availDevices = getWirelessTags()

    def p = dynamicPage(name: 'deviceList', title: 'Select Your Devices', uninstall: true) {
        section('') {
            paragraph ('''Tap below to see the list of Wireless Tag devices available in your
                Wireless Sensor Tags account and select the ones you want to connect to Hubitat.''')
            paragraph '''When you hit Done, the setup can take as much as 10 seconds per device selected.'''
            input(name: 'devices', title:'Tags', type: 'enum', required:true, multiple:true,
                description: 'Tap to choose', options:availDevices)
        }
        section('Optional Settings', hidden: true, hideable: true) {
            input 'pollTimer', 'number', title:'Minutes between poll updates of the sensors', defaultValue:5
            input name: 'debugOutput', type: 'bool', title: 'Enable debug logging?', defaultValue: true
        }
        section([mobileOnly:true]) {
            label title: 'Assign a name for this SmartApp instance (optional)', required: false
        }
    }

    return p
}

/*
    Creates an array of device names from the list returned from WST.
*/
def getWirelessTags() {
    def result = getTagStatusFromServer()

    def availDevices = [:]
    result?.each { device ->
        def dni = device?.uuid
        availDevices[dni] = device?.name
    }

    logDebug "getWirelessTags. devices: $availDevices"

    return availDevices
}

def installed() {
    initialize()
}

def updated() {
    logTrace 'update'
    unsubscribe()
    initialize()
    // turn off debuggin in 30 min
    if (debugOutput) runIn(1800, logsOff)
}

def getChildNamespace() { 'asmuts' }

def getChildName(def tagInfo) {
    def deviceType = 'Wireless Sensor Tags Motion'
    if (tagInfo) {
        switch (tagInfo.tagType) {
            case 32:
            case 33:
                deviceType = 'Wireless Sensor Tags Moisture'
                break
            case 72:
                deviceType = 'Wireless Sensor Tags PIR'
                break
        // the others aren't implemented
        // defaults to motion to create confusing bugs for users
        }
    }
    return deviceType
}

def initialize() {
    logTrace 'initialize'
    unschedule()

    def curDevices = devices.collect { dni ->
        def d = getChildDevice(dni)

        def tag = atomicState.tags.find { it.uuid == dni }

        if (!d) {
            d = addChildDevice(getChildNamespace(), getChildName(tag), dni, null, [label:tag?.name])
            d.initialSetup()
            logDebug "created ${d.displayName} $dni"
        }
        else
        {
            logDebug "found ${d.displayName} $dni already exists"
            d.updated()
        }

        if (d) {
            // configure device
            setupCallbacks(d, tag)
        }

        return dni
    }

    logTrace 'deleting unselected tags'
    def delete
    // Delete any that are no longer in settings
    if (!curDevices) {
        delete = getAllChildDevices()
    }
    else
    {
        delete = getChildDevices().findAll { !curDevices.contains(it.deviceNetworkId) }
    }

    delete.each { deleteChildDevice(it.deviceNetworkId) }

    if (atomicState.tags == null) { atomicState.tags = [:] }

    // AS - this method takes so long, the pollHandler will never be called
    // as the init times out with just 3 or 4 tags per instance.
    // and a synchonous call to the pollHandler will definitely take too long
    // we only have 30 seconds to get all the way through the init (*this was
    // SmartThings limitation. Not sure about Hubitat. . . .)
    //pollHandler()

    logTrace 'scheduling polling'
    runEvery5Minutes( pollHandler )
}

/*
    Send the user to WST. If they authorize Hubitat access, WST wil redirect
    back to the "accessGranted" url and give us a temporary code.
    We will then use that code to get a proper access-token.

    From WST docs:

    1. Redirect User to Our Login Page
        Our OAuth2 authorize URL is as follows.
            https://www.mytaglist.com/oauth2/authorize.aspx?
                client_id=[client ID of your app]
                &state=[state, optional]
                &redirect_uri=[the URL you have to receive code, if missing,
                default redirect URL configured with your app will be used]
*/
String buildOauthInitUrl() {
    logDebug 'buildOauthInitUrl'
    String cid = getHubitatClientId()

    atomicState.oauthInitState = UUID.randomUUID().toString()

    def oauthParams = [
        client_id: cid,
        state: atomicState.oauthInitState,
        redirect_uri: buildRedirectUrl()
    ]

    String wstURL = 'https://www.mytaglist.com/oauth2/authorize.aspx?' + toQueryString(oauthParams)
    logDebug( 'buildOauthInitUrl. wstURL: $wstURL')
    return wstURL
}

/*
    Creates the URL that WST will call if the user grants Hubitat access.
    This URL should include the Hubitat access token.

    WST will send us a code back.  Since both are params, include a trailing & to avoid
    a double ? problem. ***the param value for code will have ? prefix, so "?code"

    The result will look like:  (see thta "&?")
    http://192.168.1.192/apps/api/50/accessGranted?access_token=5bcdc8e3-964c-foo-bar&?code=e883d919-fabd-foo-bar

    Hubitat docs:
        https://community.hubitat.com/t/app-and-driver-porting-to-hubitat/812

    This initial call can use the local API (if the user is on the local network. . . .)
    Normal callbacks from WST to Hubitat consumers must use the cloud.

*/
String buildRedirectUrl() {
    logDebug 'buildRedirectUrl, using local API'
    // SmartThings =  /api/token/${atomicState.accessToken}/smartapps/installations/${app.id}
    String accessGrantedURL = getFullLocalApiServerUrl() + "/accessGranted?access_token=${atomicState.accessToken}&"
    logDebug( 'buildRedirectUrl. accessGrantedURL: ' + accessGrantedURL)
    return accessGrantedURL
}

/*
    Gets an access token from WST. Stores it as authToken on the state.
    This allows us to distinguish from the initial access token and the
    One provided by WST.

   After the user grants access to WST, we need to get an access token.
   From the WST docs:

    3. Obtain Access Token
        From your server, without going through user's browser, HTTP POST to below URL.
            curl -X POST https://www.mytaglist.com/oauth2/access_token.aspx -d
                    'client_id=[client ID of your app]
                    &client_secret=[client secret of your app]
                    &code=[code given in step 2]'
        Our server will return
            access_token=[access token, save this]
*/
def getAccessToken() {
    logDebug "Step 2 and 3. Getting WST access token: $params"

    // this is irritating! just dumps another query string on top
    String code = params.code ? params.code : params['?code']
    logDebug("code = ${code}")

    String oauthState = params.state
    if ( oauthState != atomicState.oauthInitState ) {
        log.error( 'State mismatch.')
    // TODO should throw
    }

    String cid = getHubitatClientId()
    String secret = getHubitatClientSecret()

    def accessTokenParams = [
        //method: 'POST',
        uri: 'https://www.mytaglist.com/',
        path: '/oauth2/access_token.aspx',
        query: [
            client_id: cid,
            client_secret: secret,
            code: code,
        ],
    ]

    def error = ''
    try {
        def jsonMap
        httpPost(accessTokenParams) { resp ->
            if (resp.status == 200) {
                jsonMap = resp.data
                if (resp.data) {
                    atomicState.authToken = jsonMap?.access_token
                } else {
                    logTrace 'error = ' + resp
                }
            } else {
                logTrace 'response = ' + resp
            }
        }
    } catch ( ex ) {
        atomicState.authToken = null
        error = ex.message
        logTrace 'Couldn\'t refresh access token. error = ' + ex
    }

    String message = 'Your Wireless Sensor Tags Account is now connected to Hubitat!'
    if ( !atomicState.authToken ) {
        message = 'Sorry. There was a problem setting up WST access.  Please try again.'
    }

    String html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=640">
<title>Wireless Sensor Tags</title>
<style type="text/css">
    .container {
        width: 560px;
        padding: 40px;
        /*background: #eee;*/
        text-align: center;
    }
    img {
        vertical-align: middle;
    }
    img:nth-child(2) {
        margin: 0 30px;
    }
    p {
        font-size: 2.2em;
        font-family: 'Swiss 721 W01 Thin';
        text-align: center;
        color: #666666;
        padding: 0 40px;
        margin-bottom: 0;
    }
    span {
        font-family: 'Swiss 721 W01 Light';
    }
</style>
</head>
<body>
    <div class="container">
        <img src="http://wirelesstag.net/media/product_title.png" alt="Wireless Sensor Tags icon" />
        <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
        <img src="https://community.hubitat.com/uploads/default/original/2X/c/cc857d00b3fd8f34753fa714cf76315b0b00e6ba.svgs" height="50px" alt="Hubitat logo" />
        <p>${message}</p>
        <p>${error}</p>
    </div>
</body>
</html>
"""
// TODO this is awful. It leaves the user stranded. They have to click back a few times.

    render contentType: 'text/html', data: html
}

///////////////////////////////////////////////////////////
// Hubitat WST =
def getHubitatClientId() { '30b8fa3d-7b63-4ef8-8503-cb6e810105b3' }
def getHubitatClientSecret() { '66292003-d4c6-4428-9485-33fa4e2f6813' }

///////////////////////////////////////////////////////////////////////////////////
def getEventStates() {
    def tagEventStates = [ 0: 'Disarmed', 1: 'Armed', 2: 'Moved', 3: 'Opened', 4: 'Closed', 5: 'Detected', 6: 'Timed Out', 7: 'Stabilizing...' ]
    return tagEventStates
}

/*
    Active polling for data.  By default this is run every 5 minutes.
*/
def pollHandler() {
    logTrace 'pollHandler'
    pollAllTags()
    updateAllDevices()
}

void updateAllDevices() {
    atomicState.tags.each { device ->
        def dni = device.uuid
        def d = getChildDevice(dni)

        if (d) {
            updateDeviceStatus(device, d)
        }
    }
}

/*
    Get data for all tags from WST and put it in state.
    Pulls the data from state and updates this device
*/
def pollSingle(def child) {
    logTrace 'pollSingle'
    pollAllTags()

    def device = atomicState.tags.find { it.uuid == child.device.deviceNetworkId }

    if (device) {
        updateDeviceStatus(device, child)
    }
}

def updateChildDeviceFromState( def child ) {
    def device = atomicState.tags.find { it.uuid == child.device.deviceNetworkId }
    if (device) {
        updateDeviceStatus(device, child)
    }
}

def updateDeviceStatus(def device, def d) {
    def tagEventStates = getEventStates()

    logTrace device

    // parsing data here
    def data = [
        tagType: convertTagTypeToString(device),
        temperature: device.temperature.toDouble().round(1),
        rssi: ((Math.max(Math.min(device.signaldBm, -60), -100) + 100) * 100 / 40).toDouble().round(),
        presence: ((device.OutOfRange == true) ? 'not present' : 'present'),
        battery: (device.batteryVolt * 100 / 3).toDouble().round(),
        switch: ((device.lit == true) ? 'on' : 'off'),
        humidity: (device.cap).toDouble().round(),
        contact : (tagEventStates[device.eventState] == 'Opened') ? 'open' : 'closed',
        acceleration  : (tagEventStates[device.eventState] == 'Moved') ? 'active' : 'inactive',
        motion : (tagEventStates[device.eventState] == 'Detected') ? 'active' : 'inactive',
        water : (device.shorted == true) ? 'wet' : 'dry'
    ]
    d.generateEvent(data)
}

def getPollRateMillis() { return 2 * 1000 }

/*
    Polls WSTfor data. Stores the data in the state.

    result.d is an array with data for each tag.

    This is not rate limited. It can be used to force a refresh.

    The format of the WST response is documented here:

    https://www.mytaglist.com/media/mytaglist.com/ethClient.asmx@op=GetTagList.html
*/
def getTagStatusFromServer() {
    logTrace 'getTagStatusFromServer'
    def result = postMessage('/ethClient.asmx/GetTagList', null)
    atomicState.tags = result?.d
    // mark this to indicate that the data is fresh.
    atomicState.lastPoll = now()
    return atomicState.tags
}

/*
    This is rate limited.  pollChild calls this. We don't want n
    number of tags making remote calls at every polling interval.
    Just need the first one to make the call.  Sore the results in the state.

    The rate limiting should be here, in the polling method.
    We might want to force an update.
    */
def pollAllTags() {
    def timeSince = (atomicState.lastPoll != null) ? now() - atomicState.lastPoll : 1000 * 1000
    logTrace "pollAllTags time since last poll: ${timeSince}"

    if ((atomicState.tags == null) || (timeSince > getPollRateMillis())) {
        getTagStatusFromServer()
    //atomicState.lastPoll = now()
    } else {
        logTrace 'Polled too recently. Balking.'
    }
    return atomicState.tags
}

/*
    Round about way to update child data.
    Just have the device call pollSingle unless HE
    uses this directly.

    pollChild is lighter weight than refresh.
*/
def pollChild( child ) {
    pollSingle(child)
    return null
}

/*
    This pings a tag to get it to update.
    Then it runs a standard pollChild call.
    I'm not sure that this will work on a single call
    unless WST blocks until the tag responds.
     . . . It does. Expect timeouts with tags in lowpowermode
     That's fine.

    https://www.mytaglist.com/media/mytaglist.com/ethClient.asmx@op=PingTag.html
*/
def refreshChild( child ) {
    logTrace "Refreshing ${child}"
    def id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        Map query = [
            'id': id
        ]
        // set a longer timeout
        postMessage('/ethClient.asmx/PingTag', query, 45)
        getTagStatusFromServer()
        updateChildDeviceFromState( child )
    } else {
        logTrace 'Could not find tag'
    }
    return null
}

//https://docs.hubitat.com/index.php?title=Common_Methods_Object#httpPost
def getdefaultTimeoutSeconds() { return 15 }
/*
    This handles all communication with WST.
    It includes the access token in the headers.
*/
def postMessage(path, def query) {
    postMessage(path, query, getdefaultTimeoutSeconds())
}
def postMessage(path, def query, timeoutSeconds) {
    logTrace "postMessage ${path}"

    def message = ''
    if (query != null) {
        if (query instanceof String) {
            message = [
                //method: 'POST',
                uri: 'https://www.mytaglist.com/',
                path: path,
                headers: ['Content-Type': 'application/json', 'Authorization': "Bearer ${atomicState.authToken}"],
                body: query,
                timeout: timeoutSeconds
            ]
        } else {
            message = [
                //method: 'POST',
                uri: 'https://www.mytaglist.com/',
                path: path,
                headers: ['Content-Type': 'application/json', 'Authorization': "Bearer ${atomicState.authToken}"],
                body: toJson(query),
                timeout: timeoutSeconds
            ]
        }
    } else {
        message = [
            //method: 'POST',
            uri: 'https://www.mytaglist.com/',
            path: path,
            headers: ['Content-Type': 'application/json', 'Authorization': "Bearer ${atomicState.authToken}"],
            timeout: timeoutSeconds
        ]
    }

    def jsonMap
    try {
        httpPost(message) { resp ->
            if (resp.status == 200) {
                if (resp.data) {
                    logTrace 'success'
                    jsonMap = resp.data
                } else {
                    logTrace 'error = ' + resp
                }
            } else {
                logDebug "http status: ${resp.status}"
                if (resp.status == 500 && resp.data.status.code == 14) {
                    logDebug 'Need to refresh auth token?'
                    atomicState.authToken = null
                }
                else
                {
                    log.error 'Authentication error, invalid authentication method, lack of credentials, etc.'
                }
            }
        }
    } catch ( ex ) {
        //atomicState.authToken = null
        logTrace 'postMessage. error = ' + ex
    }

    return jsonMap
}

/*
    Create callback url for consuming a single event type.
*/
def setSingleCallback(def tag, Map callback, def type) {
    def parameters = "?type=$type&"

    switch (type) {
        case 'water_dried':
        case 'water_detected':
            // 2 params
            parameters = parameters + 'name={0}&id={1}'
            break
        case 'oor':
        case 'back_in_range':
        case 'motion_timedout':
            // 3 params
            parameters = parameters + 'name={0}&time={1}&id={2}'
            break
        case 'door_opened':
        case 'door_closed':
            parameters = parameters + 'name={0}&orientchg={1}&x={2}&y={3}&z={4}&id={5}'
            break
        case 'motion_detected':
            // to do, check if PIR type
            if (getTagTypeInfo(tag).isPIR == true) {
                // pir
                parameters = parameters + 'name={0}&time={1}&id={2}'
            } else {
                // standard
                parameters = parameters + 'name={0}&orientchg={1}&x={2}&y={3}&z={4}&id={5}'
            }
            break    }

    //SmartThings = String callbackString = """{"url":"${getApiServerUrl()}/api/token/${atomicState.accessToken}/smartapps/installations/${app.id}/urlcallback${parameters}","verb":"GET","content":"","disabled":false,"nat":false}"""
    // This needs to be the cloud url
    String callbackString = """{"url":"${getFullApiServerUrl()}/wstcallback${parameters}&access_token=${atomicState?.accessToken}""" +
        """&access_token=${atomicState.accessToken}","verb":"GET","content":"","disabled":false,"nat":false}"""
    return callbackString
}

def getQuoted(def orig) { return (orig != null) ? "\"${orig}\"" : orig }

def useExitingCallback(Map callback) {
    String callbackString = """{"url":"${callback.url}","verb":${getQuoted(callback.verb)},""" +
    """"content":${getQuoted(callback.content)},"disabled":${callback.disabled},"nat":${callback.nat}}"""
    return callbackString
}

/*
    Get the device ids from WST.
    With these, we can create callback urls for the WST events.
    Then register the list of consumers with WST.

    From the WST docs:

    4. Use Access Token to Call Our JSON API
        You can call any of our Wireless Tag JSON APIs that would
            otherwise require login using the access token. For example,
                curl -X POST https://www.mytaglist.com/ethClient.asmx/Beep -d
                    '{id:1, beepDuration:1001}' -H 'Content-Type: application/json'
                    -H 'Authorization: Bearer [access token you saved in step 3]'
*/
def setupCallbacks(def child, def tag) {
    def id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        Map query = [
            'id': id
        ]
        def respMap = postMessage('/ethClient.asmx/LoadEventURLConfig', query)

        if (respMap.d != null) {
            String message = """{"id":${id},
                "config":{
                "__type":"MyTagList.EventURLConfig",
                'oor':${setSingleCallback(tag, respMap.d?.oor, 'oor')},
                'back_in_range':${setSingleCallback(tag, respMap.d?.back_in_range, 'back_in_range')},
                "low_battery":${useExitingCallback(respMap.d?.low_battery)},
                'motion_detected':${setSingleCallback(tag, respMap.d?.motion_detected, 'motion_detected')},
                'door_opened':${setSingleCallback(tag, respMap.d?.door_opened, 'door_opened')},
                'door_closed':${setSingleCallback(tag, respMap.d?.door_closed, 'door_closed')},
                "door_open_toolong":${useExitingCallback(respMap.d?.door_open_toolong)},
                "temp_toohigh":${useExitingCallback(respMap.d?.temp_toohigh)},
                "temp_toolow":${useExitingCallback(respMap.d?.temp_toolow)},
                "temp_normal":${useExitingCallback(respMap.d?.temp_normal)},
                "cap_normal":${useExitingCallback(respMap.d?.cap_normal)},
                "too_dry":${useExitingCallback(respMap.d?.too_dry)},
                "too_humid":${useExitingCallback(respMap.d?.too_humid)},
                'water_detected':${setSingleCallback(tag, respMap.d?.water_detected, 'water_detected')},
                'water_dried':${setSingleCallback(tag, respMap.d?.water_dried, 'water_dried')},
                'motion_timedout':${setSingleCallback(tag, respMap.d?.motion_timedout, 'motion_timedout')}
                },
                "applyAll":false}"""

            postMessage('/ethClient.asmx/SaveEventURLConfig', message)
    }
}
}

////////////////////////////////////////////////////////////////////////////////
// SEND COMMANDS TO WST

def beep(def child, int len) {
    def id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        Map query = [
            'id': id,
            'beepDuration': len
        ]
        postMessage('/ethClient.asmx/Beep', query)
    } else {
        logTrace 'Could not find tag'
    }

    return null
}

def light(def child, def on, def flash) {
    def id = getTagID(child.device.deviceNetworkId)

    def command = (on == true) ? '/ethClient.asmx/LightOn' : '/ethClient.asmx/LightOff'

    if (id != null) {
        Map query = [
            'id': id,
            'flash': flash
        ]
        postMessage(command, query)
    } else {
        logTrace 'Could not find tag'
    }

    return null
}

// //////////////////////////////////////////////////////////////////////////////
//     // AS 2/25/17 - the inactive callback works now
//     // hence, we really don't want this. WST will NOT send a motion event
//    // if there is continuous motion.
//     // Hence, we'll mark the tag as no motion when there really is motion.
//     // Polling should corrent any missed inactive events.

/*
    TODO make sure that door closing is using setDoorClosed . . .
*/
def armMotion(def child) {
    def id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        Map query = [
            'id': id,
        //'door_mode_set_closed': true
        ]
        postMessage('/ethClient.asmx/Arm', query)
    } else {
        logTrace 'Could not find tag'
    }

    return null
}

/*
    Setting Door closed arms the door sensor.

    https://www.mytaglist.com/media/mytaglist.com/ethClient.asmx@op=Arm.html

    door_mode_set_closed: when true, ask the tag to transmit back the
    current 3D orientation immediately, and record it as the base-line
    to determine if a tag has transitioned to open or closed state.
*/
def setDoorClosed(def child) {
    def id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        Map query = [
            'id': id,
            'door_mode_set_closed': true
        ]
        postMessage('/ethClient.asmx/Arm', query)
    } else {
        logTrace 'Could not find tag'
    }

    return null
}

/*
    Simply calls WST to disarm.
*/
def disarm(def child) {
    def id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        Map query = [
            'id': id
        ]
        postMessage('/ethClient.asmx/DisArm', query)
    } else {
        logTrace 'Could not find tag'
    }

    return null
}

// I hate this method.  Get this out of here.
//
// AS - TODO look into why the child called this on update
// it messes up the closed positions of the tags.
// there is no reason to think the doors are closed on update

/*
    Found the WST docs!  List of all operations

    https://www.mytaglist.com/media/mytaglist.com/ethClient.asmx.html
*/
def setMotionSensorConfig(def child, def mode, def timeDelay) {
    logTrace 'setMotionSensorConfig'

    def id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        if (mode == 'disarmed') {
            disarmMotion(child)
        } else {
            Map query = [
                'id': id
            ]
            def result = postMessage('/ethClient.asmx/LoadMotionSensorConfig', query)

            if (result?.d) {
                switch (mode) {
                    case 'accel':
                        result.d.door_mode = false
                        break
                    case 'door':
                        result.d.door_mode = true
                        break
                }

                result.d.auto_reset_delay = timeDelay

                String jsonString = toJson(result.d)
                jsonString = toJson(result.d).substring(1, toJson(result.d).size() - 1)

                String queryString = """{"id":${id},
                "config":{"__type":"MyTagList.MotionSensorConfig",${jsonString}},
                "applyAll":false}"""

                postMessage('/ethClient.asmx/SaveMotionSensorConfig', queryString)

        //armMotion(child)
        }
    }
    } else {
        logTrace 'Could not find tag'
}

    return null
}
/////////////////////////////////////////////////////////////////////////////////
// Methods for identifying tags and types

def getTagID(def uuid) {
    return atomicState.tags.find { it.uuid == uuid }?.slaveId
}

def getTagUUID(def id) {
    return atomicState.tags.find { it.slaveId == id }?.uuid
}

def getTagTypeInfo(def tag) {
    Map tagInfo = [:]

    tagInfo.isMsTag = (tag.tagType == 12 || tag.tagType == 13)
    tagInfo.isMoistureTag = (tag.tagType == 32 || tag.tagType == 33)
    tagInfo.hasBeeper = (tag.tagType == 13 || tag.tagType == 12)
    tagInfo.isReed = (tag.tagType == 52 || tag.tagType == 53)
    tagInfo.isPIR = (tag.tagType == 72)
    tagInfo.isKumostat = (tag.tagType == 62)
    tagInfo.isHTU = (tag.tagType == 52 || tag.tagType == 62 || tag.tagType == 72 || tag.tagType == 13)

    return tagInfo
}

def getTagVersion(def tag) {
    if (tag.version1 == 2) {
        if (tag.rev == 14) return ' (v2.1)'
        else return ' (v2.0)'
    }
    if (tag.tagType != 12) return ''
    if (tag.rev == 0) return ' (v1.1)'
    else if (tag.rev == 1) return ' (v1.2)'
    else if (tag.rev == 11) return ' (v1.3)'
    else if (tag.rev == 12) return ' (v1.4)'
    else if (tag.rev == 13) return ' (v1.5)'
    else return ''
}

String convertTagTypeToString(def tag) {
    String tagString = 'Unknown'

    switch (tag.tagType) {
        case 12:
            tagString = 'MotionSensor'
            break
        case 13:
            tagString = 'MotionHTU'
            break
        case 72:
            tagString = 'PIR'
            break
        case 52:
            tagString = 'ReedHTU'
            break
        case 53:
            tagString = 'Reed'
            break
        case 62:
            tagString = 'Kumostat'
            break
        case 32:
        case 33:
            tagString = 'Moisture'
            break
    }

    return tagString + getTagVersion(tag)
}

//////////////////////////////////////////////////////////////////
def toJson(Map m) {
    return groovy.json.JsonOutput.toJson(m)
}

def toQueryString(Map m) {
    return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join('&')
}

def logsOff() {
    log.warn 'debug logging disabled...'
    device.updateSetting('debugOutput', [value:'false', type:'bool'])
}

private logDebug(msg) {
    if (settings?.debugOutput || settings?.debugOutput == null) {
        log.debug "$msg"
    }
}

private logTrace(msg) {
    if (settings?.debugOutput || settings?.debugOutput == null) {
        log.trace "$msg"
    }
}
