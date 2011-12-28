# jRS232

jRS232 is a RS232 communications library for use in Java applications. It essentially abstracts the
javax.comm mess out of the rest of the Java application that needs to access it, and keeps things a bit
simpler for those just wanting to write to serial ports.

### OSs and Hardware IO

Right now this is targeted to Windows platforms, using the legacy Sun javax.comm implementation (which does still work).
The actual hardware interface layer could be replaced with another implementation such as RXTX, in theory.

The basic default implementation supports physical and virtual serial ports (usually via FTDI 232 chip interfaces).

## Setup

### Windows Machines

javax.comm is required. Install the legacy package located in `/externallibs`, and get it set up in your
Maven repo:

`mvn install:install-file -Dfile=comm.jar -DgroupId=javax.comm -DartifactId=comm -Dversion=2.0.3 -Dpackaging=jar -DgeneratePom=true`

### MacOS

Not yet...

## Using the Library

See `com.willmeyer.jrs232.RS232Device`.
