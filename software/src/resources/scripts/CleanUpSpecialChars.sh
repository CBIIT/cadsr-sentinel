#!/bin/bash

DATE=`date +%Y%m%d`
JAVA_HOME=/usr/java8
#JAVA_HOME=/usr/libexec/java_home -v 1.8
BASE_DIR=/local/content/cadsrsentinel/bin
 
export JAVA_HOME BASE_DIR
 
JAVA_PARMS='-Xms1024m -Xmx2048m -XX:MaxMetaspaceSize=512m'
 
#export JAVA_PARMS ORACLE_HOME TNS_ADMIN PATH LD_LIBRARY_PATH
export JAVA_PARMS

echo "Executing new job as `id`"
echo "Executing on `date`"

find $BASE_DIR/../reports -mtime +20 -exec rm {} \;

for x in $BASE_DIR/*.jar
do

CP=$CP:$x

done

export CP

echo $JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.CleanSpecialCharacters $BASE_DIR/log4j.xml $BASE_DIR/CleanSpecialCharacters.xml

$JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.CleanSpecialCharacters $BASE_DIR/log4j.xml $BASE_DIR/CleanSpecialCharacters.xml