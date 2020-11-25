/*
 *	Hubitat Import URL: https://github.com/asmuts/hubitat/blob/master/WirelessSensorTags/WST_Moisture_Driver.groovy
 *
 */
 metadata {
    definition (name: 'Wireless Sensor Tags Moisture', namespace: 'asmuts', author: 'asmuts') {
    capability 'Water Sensor'
    capability 'Presence Sensor'
    capability 'Relative Humidity Measurement'
    capability 'Temperature Measurement'
    capability 'Signal Strength'
    capability 'Battery'
    capability 'Refresh'
    capability 'Polling'

    command 'generateEvent'
    command 'initialSetup'

    attribute 'tagType', 'string'
    }

    preferences {
      input name: 'debugOutput', type: 'bool', title: 'Enable debug logging?', defaultValue: true
    }
}

// parse events into attributes
def parse(String description) {
  logDebug "Parsing '${description}'"
}

void poll() {
  logDebug 'poll'
  parent.pollChild(this)
}

def refresh() {
  logDebug 'refresh'
  parent.refreshChild(this)
}

def initialSetup() {
}

def updated() {
  logTrace 'updated'
  if (debugOutput) runIn(1800, logsOff)
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
        boolean isChange = curTemp.toString() != tempValue.toString()
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
