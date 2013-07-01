killall java
mkdir -p ~/.groovy/lib && cp .groovy/lib/*jar ~/.groovy/lib

groovy server.groovy & 
sleep 5
open teacher.html 
sleep 5 # What until the the teacher's page is loaded before trying to load the student page
open student.html
fg
