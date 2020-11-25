/*
 *	Hubitat Import URL: https://github.com/asmuts/hubitat/blob/master/WirelessSensorTags/WST_PIR_Driver.groovy
 *
 */
 metadata {
    definition (name: 'Wireless Sensor Tags PIR', namespace: 'asmuts', author: 'asmuts') {
        capability 'Tone'
        capability 'Relative Humidity Measurement'
        capability 'Temperature Measurement'
        capability 'Signal Strength'
        capability 'Battery'
        capability 'Refresh'
        capability 'Polling'
        capability 'Sensor'

        command 'generateEvent'
        command 'disarm'
        command 'arm'
        command 'initialSetup'

        attribute 'tagType', 'string'
        //attribute 'motionMode', 'string'
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

void poll() {
  logDebug 'poll'
  parent.pollChild(this)
}

def refresh() {
  logDebug 'refresh'
  parent.refreshChild(this)
}

// // this will arm
// def setModeToMotion() {
//   logDebug 'set to accel'
//   def newMode = 'accel'
//   parent.setMotionSensorConfig(this, newMode, getMotionDecay())
//   sendEvent(name: 'motionMode', value: newMode)
// }

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

def initialSetup() {
  sendEvent(name: 'motionMode', value: 'accel')
  parent.setMotionMode(this, 'accel')
}


def updated() {
  logTrace 'updated'
  if (debugOutput) runIn(1800, logsOff)
// AS - I have no idea why we'd do this. It messes everything up and times out the init on the parent
//parent.setMotionMode(this, device.currentState("motionMode")?.stringValue, getMotionDecay())
}

void generateEvent(Map results) {
  logDebug "generateEvent. parsing data $results"

  if (results) {
    results.each { name, value ->
      boolean isDisplayed = true

      if (name == 'temperature') {
        def curTemp = device.currentValue(name)
        def tempValue = getTemperature(value)
        logDebug( 'current temp: ' + curTemp )
        boolean isChange = curTemp.toString() != tempValue.toString()
        isDisplayed = isChange

        sendEvent(name: name, value: tempValue, unit: getTemperatureScale(), displayed: isDisplayed)
      }
      else {
        boolean isChange = device.currentValue(name).toString() == value.toString()
        //isStateChange(device, name, value.toString())
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
