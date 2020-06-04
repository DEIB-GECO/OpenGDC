# OpenGDC
OpenGDC is an open-source Java tool for the automatic extraction, extension, and conversion in BED, GTF, CSV, JSON, and XML format of all the genomic experiments and clinical information from the Genomic Data Commons (GDC) portal [https://gdc.cancer.gov/](https://gdc.cancer.gov/).

### How to use
This is a NetBeans project. Just clone the repo, load it into NetBeans, set the GUI.java class as the main class, and compile (JRE 1.8 or higher is required). Double click on the produced JAR and start playing with OpenGDC.

### Build a repository
The software includes a built-in mode to create a repository with all the original public available data of GDC and the converted once. To enable this mode, set the UpdateScheduler.java class as the main class of the project, and produce your JAR. This requires a date as argument like in the following example:

```
java -jar UpdateScheduler.jar 2020-01-01
```

The specified date is internally used to filter and retrieve the GDC data produced starting from Jan 01, 2020 (in this case). To create an automatic procedure to maintan the repository up to date, the most easy solution is to exploit crontab to schedule the execution of the software one time every X days. This can be done by creating a simple bash script like the following one:

```
#!/bin/bash
datetime=$( tail -n 1 opengdc-history.txt )
java -jar UpdateScheduler.jar $datetime
date +\%Y-\%m-\%d >> opengdc-history.txt
```

This script exploit an external TXT file `opengdc-history.txt` which take trace of the last day on which the execution of the software has been performed. We recommend to initialise the `opengdc-history.txt` file with just a single line containing a date as far as possible from the start up of the GDC program to initially build the repo with all the public available data.

### Notes for contributors
Everyone is welcome to contribute to the software development. Simply reply to the issue [#46](https://github.com/fabio-cumbo/OpenGDC/issues/46) and you will receive an invitation email.

### Links
- Software web-page: [http://geco.deib.polimi.it/opengdc/](http://geco.deib.polimi.it/opengdc/)
- Public data repository: [fttp://geco.deib.polimi.it/opengdc/](fttp://geco.deib.polimi.it/opengdc/)

### Credits
Paper under review. Stay tuned!
