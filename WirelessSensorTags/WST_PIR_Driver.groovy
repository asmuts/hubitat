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
}

//////////////////////////////////////////////////////////////////////////////////

// parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
}

def beep() {
  log.debug "Executing 'beep'"
  parent.beep(this, 3)
}

def on() {
  log.debug "Executing 'on'"
  parent.light(this, true, false)
  sendEvent(name: 'switch', value: 'on')
}

def off() {
  log.debug "Executing 'off'"
  parent.light(this, false, false)
  sendEvent(name: 'switch', value: 'off')
}

void poll() {
  log.debug 'poll'
  parent.pollChild(this)
}

def refresh() {
  log.debug 'refresh'
  parent.refreshChild(this)
}

// // this will arm
// def setModeToMotion() {
//   log.debug 'set to accel'
//   def newMode = 'accel'
//   parent.setMotionSensorConfig(this, newMode, getMotionDecay())
//   sendEvent(name: 'motionMode', value: newMode)
// }

void disarm() {
  log.debug 'set to disarmed'
  parent.disarm(this)
  sendEvent(name: 'armed', value: false)
}

void arm() {
  log.debug 'set to armed'
  parent.armMotion(this)
  sendEvent(name: 'armed', value: true)
}

def initialSetup() {
  sendEvent(name: 'motionMode', value: 'accel')
  parent.setMotionMode(this, 'accel', getMotionDecay())
}

def getMotionDecay() {
  def timer = (settings.motionDecay != null) ? settings.motionDecay.toInteger() : 60
  return timer
}

def updated() {
  log.trace 'updated'
// AS - I have no idea why we'd do this. It messes everything up and times out the init on the parent
//parent.setMotionMode(this, device.currentState("motionMode")?.stringValue, getMotionDecay())
}

void generateEvent(Map results) {
  log.debug "generateEvent. parsing data $results"

  if (results) {
    results.each { name, value ->
      boolean isDisplayed = true

      if (name == 'temperature') {
        def curTemp = device.currentValue(name)
        def tempValue = getTemperature(value)
        log.debug( 'current temp: ' + curTemp )
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
