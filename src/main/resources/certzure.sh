#!/bin/sh

if [ $1 = 'deploy_challenge' ] || [ $1 = 'clean_challenge' ] || [ $1 = 'deploy_cert' ]
then
    java -jar /opt/certzure/certzure.jar $1 $2 $3 $4

    if [ $1 = 'deploy_cert' ] && [ -e /opt/hpkpinx/hpkpinx.sh ]
    then
        sh /opt/hpkpinx/config.sh $1 $2
    fi
fi
