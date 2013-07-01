killall java
mkdir -p ~/.groovy/lib && cp .groovy/lib/*jar ~/.groovy/lib

groovy server.groovy & 
sleep 5
open teacher.html 
sleep 5
open student.html
fg
