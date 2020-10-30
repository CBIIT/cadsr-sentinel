#!/bin/bash

echo "Executing Auto Run for Sentinel Tool"
echo "\$Header: /share/content/gforge/sentinel/sentinel/scripts/autorun.sh,v 1.10 2009-07-24 15:36:50 davet Exp $"
echo "\$Name: not supported by cvs2svn $"

DATE=`date +%Y%m%d`
JAVA_HOME=/usr/java8
BASE_DIR=/local/content/cadsrsentinel/bin
 
export JAVA_HOME BASE_DIR
 
#ORACLE_HOME=/app/oracle/product/dbhome/9.2.0
#PATH=$ORACLE_HOME/bin:$PATH
#LD_LIBRARY_PATH=$ORACLE_HOME/lib:$LD_LIBRARY_PATH
#TNS_ADMIN=$ORACLE_HOME/network/admin
JAVA_PARMS='-Xms2048m -Xmx4096m -XX:MaxMetaspaceSize=512m'
 
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

echo $JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.tool.AutoProcessAlerts $BASE_DIR/log4j.xml true $BASE_DIR/cadsrsentinel.xml

$JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.tool.AutoProcessAlerts $BASE_DIR/log4j.xml true $BASE_DIR/cadsrsentinel.xml

echo $JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.SimpleSQL $BASE_DIR/log4j.xml $BASE_DIR/SimpleSQL.xml

$JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.SimpleSQL $BASE_DIR/log4j.xml $BASE_DIR/SimpleSQL.xml

echo $JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.CleanStrings $BASE_DIR/log4j.xml $BASE_DIR/CleanStrings.xml

$JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.CleanStrings $BASE_DIR/log4j.xml $BASE_DIR/CleanStrings.xml

echo $JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.CleanOCR $BASE_DIR/log4j.xml $BASE_DIR/CleanOCR.xml

$JAVA_HOME/bin/java -client $JAVA_PARMS -classpath $BASE_DIR:$CP gov.nih.nci.cadsr.sentinel.daily.CleanOCR $BASE_DIR/log4j.xml $BASE_DIR/CleanOCR.xml