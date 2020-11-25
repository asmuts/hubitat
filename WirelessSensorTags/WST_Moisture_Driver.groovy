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
}

// parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
}

void poll() {
  log.debug 'poll'
  parent.pollChild(this)
}

def refresh() {
  log.debug 'refresh'
  parent.refreshChild(this)
}

def initialSetup() {
}

def updated() {
  log.trace 'updated'
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
