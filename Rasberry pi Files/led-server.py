import os
import glob
import time
import RPi.GPIO as GPIO
from bluetooth import *
import wiringpi #This is a lib you will need to install. sudo pip install wiringpi
import time
import threading 

#Adam Corbin 7/31/2017
#This module is used to communicate to any device over bluetooth.
#For testing purposes using an Android app here:
#Your android device needs to be paired with the RPi before you can send commands

fadingLED = False
blinking = False

valueOn = 0
valueOff = 1024
wiringpi.wiringPiSetup ()
ledPin = 1 #This pin maps to RasPi3 GPIO 18 which is the PWM pin 
#We need to ensure that the pin mode has been set to PWM so we can fade the led
wiringpi.pinMode(ledPin,wiringpi.PWM_OUTPUT)
wiringpi.pwmWrite(ledPin,valueOn)


server_sock=BluetoothSocket( RFCOMM )
server_sock.bind(("",PORT_ANY))
server_sock.listen(1)

port = server_sock.getsockname()[1]

uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee" #This is the UUID that needs to map the the Android SerialPortService ID

#This sets up the bluetooh module what to look for
advertise_service( server_sock, "PiServer",
                   service_id = uuid,
                   service_classes = [ uuid, SERIAL_PORT_CLASS ],
                   profiles = [ SERIAL_PORT_PROFILE ], 
                    )
                    
#This thread is used to receive commands from the Android app and and replys that the commands have been executed                    
def worker(): 
    global fadingLED
    global blinking
    while True:          
        print "Waiting for connection on RFCOMM channel %d" % port

        client_sock, client_info = server_sock.accept()
        print "Accepted connection from ", client_info

        try:
            data = client_sock.recv(1024)
            if len(data) == 0: break
            print "received [%s]" % data

            if data == 'fadeLED':
                fadingLED = True
                blinking = False
                data = 'Fade LED!'
            elif data == 'lightOn':
                fadingLED = False
                blinking = False
                wiringpi.pwmWrite(ledPin,valueOn)
                data = 'light on!'
            elif data == 'lightOff':
                fadingLED = False
                blinking = False
                wiringpi.pwmWrite(ledPin,valueOff)           
                data = 'light off!'
            elif data == 'blink':
                fadingLED = False
                blinking = True
                data = 'blinking!'
            else:
                data = 'WTF!' 
            client_sock.send(data)
            print "sending [%s]" % data

        except IOError:
            pass

        except KeyboardInterrupt:

            print "disconnected"

            client_sock.close()
            server_sock.close()
            print "all done"

            break
            
#This thread keeps track if we are fading or blinking. 
#They need to be in seperate threads since the bluetooth has a blocking task wating for a message
def worker2():
    value = 0
    increment = 4
    increasing = True
    global fadingLED
    global blinking
    global valueOn
    global valueOff
    ledOn = False
    
    while True:
        if fadingLED:
            wiringpi.pwmWrite(ledPin,value)
     
            if increasing:
                    value += increment
                    time.sleep(0.002)
            else:
                    value -= increment
                    time.sleep(0.002)
     
            if (value >=1024):
                    increasing = False
     
            if (value <= 0):
                    increasing = True
     
            time.sleep(0.002)
        elif blinking:
            if ledOn:
                wiringpi.pwmWrite(ledPin,valueOn)
            else:
                wiringpi.pwmWrite(ledPin,valueOff)
            
            ledOn = not ledOn
            time.sleep(.5)
            
        else:
            time.sleep(1)

t = threading.Thread(name='Bluetooth_thread',target=worker)
t.start()  
t1 = threading.Thread(name='ledFadeThread',target=worker2)
t1.start()          