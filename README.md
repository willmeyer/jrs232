# JRS232

JRS232 is a RS232 communications library for use in Java applications. It essentially abstracts the 
javax.comm mess out of other Java code.  

This could be replaced with another implementation of the actual serial communications (like rxtx).

## Setup

### Windows Machines

javax.comm is required. Install the legacy package located in /externallibs, and get it set up in your 
Maven repo:

mvn install:install-file -Dfile=comm.jar -DgroupId=javax.comm -DartifactId=comm -Dversion=2.0.3 -Dpackaging=jar -DgeneratePom=true 


### MacOS

TBD

