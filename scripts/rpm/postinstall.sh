/bin/echo "postinstall script started [$1]"

SERVICE_NAME=shuppet

if [ "$1" -le 1 ]
then
  /sbin/chkconfig --add jetty
else
  /sbin/chkconfig --list jetty
fi

mkdir -p /var/log/jetty

chown -R jetty:jetty /var/log/jetty

ln -s /var/log/jetty /usr/local/jetty/log

chown jetty:jetty /usr/local/jetty

mkdir -p /var/log/${SERVICE_NAME}

chown -R ${SERVICE_NAME}:${SERVICE_NAME} /var/log/${SERVICE_NAME}

ln -s /var/log/${SERVICE_NAME} /usr/local/${SERVICE_NAME}/log

chown ${SERVICE_NAME}:${SERVICE_NAME} /usr/local/${SERVICE_NAME}

/bin/echo "postinstall script finished"
exit 0
