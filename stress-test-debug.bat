java -showversion -XX:+PrintFlagsFinal -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=.\dumps -Xlog:gc*,gc+phases=debug::time,uptime:file:gc.log -jar target\dbchat-2.0.6.jar --version 
