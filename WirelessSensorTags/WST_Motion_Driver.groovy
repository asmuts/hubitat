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
    input 'motionDecay', 'number', title: 'Motion Rearm Time',
        description: 'Seconds (min 60 for now)',
        defaultValue: 60, required: true, displayDuringSetup: true
    }
}

//////////////////////////////////////////////////////////////////////////////////

// parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
}

// handle commands
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

// TODO I'm not sure there is any point to this.
// The poll handler of the parent doesn use it.
// Client can just use refresh.
// Looks like Hubitat might use it.
// It could call refresh on the parent.
void poll() {
  log.debug 'poll'
  parent.pollChild(this)
}

def refresh() {
  log.debug 'refresh'
  parent.refreshChild(this)
}

def setModeToMotion() {
  log.debug 'set to accel'
  def newMode = 'accel'
  parent.setMotionSensorConfig(this, newMode, getMotionDecay())
  sendEvent(name: 'motionMode', value: newMode)
}

def setModeToDoorMonitoring() {
  log.debug 'set to door monitoring'
  def newMode = 'door'
  parent.disarm(this)
  parent.setMotionSensorConfig(this, newMode, getMotionDecay())
  sendEvent(name: 'motionMode', value: newMode)
}

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

void setDoorClosedPosition() {
  log.debug 'set door closed pos'
  //parent.disarm(this)
  parent.setDoorClosed(this)
}

def initialSetup() {
  sendEvent(name: 'motionMode', value: 'accel')
  parent.setMotionSensorConfig(this, 'accel', getMotionDecay())
}

def getMotionDecay() {
  def timer = (settings.motionDecay != null) ? settings.motionDecay.toInteger() : 60
  return timer
}

def updated() {
  log.trace 'updated'
// AThisIt messes everything up and times out the init on the parent
//parent.setMotionSensorConfig(this, device.currentState("motionMode")?.stringValue, getMotionDecay())
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
        // TODO test this logic!
        boolean isChange = curTemp.toString() == tempValue.toString()
        isDisplayed = isChange
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
