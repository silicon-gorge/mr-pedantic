/bin/echo "postinstall script started [$1]"

SERVICE_NAME=shuppet

if [ "$1" -le 1 ]
then
  /sbin/chkconfig --add $SERVICE_NAME
else
  /sbin/chkconfig --list $SERVIE_NAME
fi

mkdir -p /var/log/$SERVICE_NAME

chown -R $SERVICE_NAME:$SERVICE_NAME /var/log/$SERVICE_NAME

ln -s /var/log/$SERVICE_NAME /usr/local/$SERVICE_NAME/log

chown $SERVICE_NAME:$SERVICE_NAME /usr/local/$SERVICE_NAME

/bin/echo "postinstall script finished"
exit 0
