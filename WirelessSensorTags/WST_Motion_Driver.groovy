/*
 *	Hubitat Import URL: https://github.com/asmuts/hubitat/blob/master/WirelessSensorTags/WST_Motion_Driver.groovy
 *
 */
 metadata {
    definition (name: 'Wireless Sensor Tags Motion', namespace: 'asmuts', author: 'asmuts') {
        capability 'Presence Sensor'
        capability 'Acceleration Sensor'
        capability 'Motion Sensor'
        capability 'Tone'
        capability 'Relative Humidity Measurement'
        capability 'Temperature Measurement'
        capability 'Signal Strength'
        capability 'Battery'
        capability 'Refresh'
        capability 'Polling'
        capability 'Switch'
        capability 'Contact Sensor'
        capability 'Sensor'

        command 'generateEvent'
        command 'setModeToMotion'
        command 'setModeToDoorMonitoring'
        command 'disarm'
        command 'arm'
        command 'setDoorClosedPosition'
        command 'initialSetup'

        attribute 'tagType', 'string'
        attribute 'motionMode', 'string'
        attribute 'armed', 'boolean'
    }

    preferences {
      input name: 'debugOutput', type: 'bool', title: 'Enable debug logging?', defaultValue: true
    }
}

//////////////////////////////////////////////////////////////////////////////////

// parse events into attributes
def parse(String description) {
  logDebug "Parsing '${description}'"
}

// handle commands
def beep() {
  logDebug "Executing 'beep'"
  parent.beep(this, 3)
}

def on() {
  logDebug "Executing 'on'"
  parent.light(this, true, false)
  sendEvent(name: 'switch', value: 'on')
}

def off() {
  logDebug "Executing 'off'"
  parent.light(this, false, false)
  sendEvent(name: 'switch', value: 'off')
}

// TODO I'm not sure there is any point to this.
// The poll handler of the parent doesn use it.
// Client can just use refresh.
// Looks like Hubitat might use it.
// It could call refresh on the parent.
void poll() {
  logDebug 'poll'
  parent.pollChild(this)
}

def refresh() {
  logDebug 'refresh'
  parent.refreshChild(this)
}

def setModeToMotion() {
  logDebug 'set to accel'
  def newMode = 'accel'
  parent.setMotionSensorConfig(this, newMode)
  sendEvent(name: 'motionMode', value: newMode)
}

def setModeToDoorMonitoring() {
  logDebug 'set to door monitoring'
  def newMode = 'door'
  parent.disarm(this)
  parent.setMotionSensorConfig(this, newMode)
  sendEvent(name: 'motionMode', value: newMode)
}

void disarm() {
  logDebug 'set to disarmed'
  parent.disarm(this)
  sendEvent(name: 'armed', value: false)
}

void arm() {
  logDebug 'set to armed'
  parent.armMotion(this)
  sendEvent(name: 'armed', value: true)
}

void setDoorClosedPosition() {
  logDebug 'set door closed pos'
  //parent.disarm(this)
  parent.setDoorClosed(this)
}

def initialSetup() {
  sendEvent(name: 'motionMode', value: 'accel')
  parent.setMotionSensorConfig(this, 'accel')
}

def updated() {
  logTrace 'updated'
  if (debugOutput) runIn(1800, logsOff)
// AThisIt messes everything up and times out the init on the parent
//parent.setMotionSensorConfig(this, device.currentState("motionMode")?.stringValue, getMotionDecay())
}

void generateEvent(Map results) {
  logDebug "generateEvent. parsing data $results"

  if (results) {
    results.each { name, value ->
      boolean isDisplayed = true

      if (name == 'temperature') {
        def curTemp = device.currentValue(name)
        def tempValue = getTemperature(value)
        logDebug( "current temp: ${curTemp} | event temp: ${tempValue}" )
        // TODO test this logic!
        boolean isChange = curTemp.toString() != tempValue.toString()
        isDisplayed = isChange
        //logTrace("isChange ${isChange}")
        sendEvent(name: name, value: tempValue, unit: getTemperatureScale(), displayed: isDisplayed)
      }
      else {
        boolean isChange = device.currentValue(name).toString() == value.toString()
        isDisplayed = isChange
        sendEvent(name: name, value: value, isStateChange: isChange, displayed: isDisplayed)
      }
    }
  }
}

def getTemperature(value) {
  def celsius = value
  if (getTemperatureScale() == 'C') {
    return celsius
  } else {
    return celsiusToFahrenheit(celsius) as Integer
  }
}

////////////////////////////////////////////////////////////

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
