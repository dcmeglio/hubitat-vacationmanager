/**
 *
 *  Vacation Manager
 *
 *  Copyright 2020 Dominick Meglio
 *
 *	If you find this useful, donations are always appreciated 
 *	https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url
 */
 
 import groovy.transform.Field

@Field static Random randomNumberGenerator = new Random()
 
definition(
    name: "Vacation Manager",
    namespace: "dcm.vacationmanager",
    author: "Dominick Meglio",
    description: "Allows you to schedule devices to turn on/off at random times while you're aware from home to make it look like you are still there.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	documentationLink: "https://github.com/dcmeglio/hubitat-vacationmanager/blob/master/README.md")

preferences {
    page(name: "prefMain")
	page(name: "prefSettings")
}

def prefMain() {
	return dynamicPage(name: "prefMain", title: "Vacation Manager", nextPage: "prefSettings", uninstall:false, install: false) {
		section("Triggers") {
            paragraph "The settings below allow you to configure when Vacation Manager is active"
			
			input "useMode", "bool", title: "Enable when the mode is one of the following", defaultValue: false, submitOnChange: true
			if (useMode) {
				def modes = location.getModes()
				def modeList = []
				modes.each {
					modeList << it.name
				}
				modeList = modeList.sort()
			
				input "modesList", "enum", title: "Modes", required: true, options:modeList, multiple: true
			}
			input "useHsmMode", "bool", title: "Enable when the Hubitat Safety Monitor mode is one of the following", defaultValue: false, submitOnChange: true
			if (useHsmMode) {
				input "hsmModesList", "enum", title: "HSM Modes", required: true, options: ["armedAway", "armingAway", "armedHome", "armingHome",
					"armedNight", "armingNight", "disarmed", "allDisarmed"], multiple: true
			}
			
			input "useSwitch", "bool", title: "Enable when one of the selected switches is on", defaultValue: false, submitOnChange: true
			if (useSwitch) {
				input "switchList", "capability.switch", title: "Switches", required: true, multiple: true
			}
		}
	}
}

def prefSettings() {
    return dynamicPage(name: "prefSettings", title: "", install: true, uninstall: true) {
		section ("Run Times") {
			paragraph: "The settings below define how the vacation manager will manage devices in your absence"
			
			input "startTimeOption", "enum", title: "Start time type:", options: ["A specific time", "Sunrise", "Sunset"], required: true, submitOnChange: true
			if (startTimeOption == "A specific time") {
				input "startTime", "time", title: "Start time", required: true
			}
			else {
				input "startOffset", "number", title: "Offset in minutes (+/-)", range: "-1399..1399", required: true
			}
			input "endTimeOptions", "enum", title: "Start time type:", options: ["A specific time", "Sunrise", "Sunset"], required: true, submitOnChange: true
			if (endTimeOptions == "A specific time") {
				input "endTime", "time", title: "End time", required: true
			}
			else {
				input "endOffset", "number", title: "Offset in minutes (+/-)", range: "-1399..1399", required: true
			}
			
        }
		section ("Lights") {
			input "lightSwitches", "capability.switch", title: "Switches to include", required: true, multiple: true
			input "delayTime", "number", title: "The amount of time in minutes between when one set of lights is turned off and the next is turned on", required: true, defaultValue: 1, range: "0..1399"	
			input "minDevices", "number", title: "Minimum number of devices that can be on at one time", required: true, defaultValue: 1, range: "0..10"
			input "maxDevices", "number", title: "Maximum number of devices that can be on at one time"
		}
	}
}

def installed() {
	initialize()
}

def uninstalled() {
	logDebug "uninstalling app"
}

def updated() {	
    logDebug "Updated with settings: ${settings}"
	unschedule()
    unsubscribe()
	initialize()
}

def initialize() {
	if (useHsmMode)
		subscribe(location, "hsmStatus", hsmStatusHandler)
	
	if (useMode)
		subscribe(location, "mode", modeHandler)
	
	if (useSwitch) {
		subscribe(switchList, "switch.on", switchOnHandler)
		subscribe(switchList, "switch.off", switchOffHandler)
	}
}

def hsmStatusHandler(evt) {
	if (hsmModesList?.contains(evt.value))
		startVacationManager()
	else
		stopVacationManager()
}

def modeHandler(evt) {
	if (modesList.contains(evt.value))
		startVacationManager()
	else
		stopVacationManager()
}

def switchOnHandler(evt) {
	startVacationManager()
}

def switchOffHandler(evt) {
	stopVacationManager()
}

def startVacationManager() {
	log.info "Vacation Manager is active"
	state.lastRun = 0
	state.lastAction = ""
	schedule("0 * * * * ? *", runVacationManager)
}

def stopVacationManager() {
	log.info "Vacation Manager is inactive"
	unschedule()
}

def runVacationManager() {
	def timeRange = getStartAndEndTime()
	
	if (timeOfDayIsBetween(timeRange.start, timeRange.end, new Date())) {
		logDebug "Within time range window"
		
		if (now()-(delayTime*60*1000) > state.lastRun) {
			state.lastRun = now()
			def numberOfDevicesToChange = getRandomInRange(minDevices,maxDevices)
			def devicesToChange = getDevicesToChange(numberOfDevicesToChange)
			
			for (def idx in devicesToChange) {
				if (lightSwitches[idx].currentValue("switch") == "on")
					lightSwitches[idx].off()
				else
					lightSwitches[idx].on()
			}
		}
	}
}

def getRandomInRange(low,high) {
	return randomNumberGenerator.nextInt(high-low+1)+low
}

def getDevicesToChange(count) {
	def deviceIndices = []
	if (count > lightSwitches.size())
		count = lightSwitches.size()
	
		
	for (def i = 0; i < count; i++) {
		def nextIndex 
		while(true) {
			nextIndex = randomNumberGenerator.nextInt(count)
			if(!deviceIndices.contains(nextIndex)) 
				break
		}

		deviceIndices << nextIndex
	}
}

def getStartAndEndTime() {
	def startTimeAsDate = timeToday(startTime, location.timeZone)
	def endTimeAsDate = timeToday(endTime, location.timeZone)
	if (startTimeOption != "A specific time" || endTimeOptions != "A specific time") {
		def sunsetOffset = 0
		def sunriseOffset = 0
		if (startTimeOption == "Sunrise")
			sunriseOffset = startOffset
		else if (startTimeOption == "Sunset")
			sunsetOffset = startOffset
			
		if (endTimeOptions == "Sunrise")
			sunriseOffset = endOffset
		else if (endTimeOptions == "Sunset")
			sunsetOffset = endOffset
			
		def offsetRiseAndSet = getSunriseAndSunset(sunriseOffset: sunriseOffset, sunsetOffset: sunsetOffset)
		
		if (startTimeOption == "Sunrise")
			startTimeAsDate = offsetRiseAndSet.sunrise
		else if (startTimeOption == "Sunset")
			startTimeAsDate = offsetRiseAndSet.sunset
			
		if (endTimeOptions == "Sunrise")
			endTimeAsDate = offsetRiseAndSet.sunrise
		else if (endTimeOptions == "Sunset")
			endTimeAsDate = offsetRiseAndSet.sunset
	}
	return [start: startTimeAsDate, end: endTimeAsDate]
}

def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
	}
}