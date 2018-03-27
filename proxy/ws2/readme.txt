# record WS frames in chrome with dev tools open in a separate window, ie undocked
# ctrl-shift-i to use devtools on devtools

open devtools
open the websocket frames view
pop out devtools ie separate window
ctrl-shift-i to devtools devtools
to view the data table:
  ctrl-shift-c (or the "select an element to inspect it" icon in the upper left)
  select one of the frames

# in DT2

b = $$('td.data-column');
for (var ii=0,c=[], s=[]; ii<b.length; ii++) { var d=b[ii].parentElement.className.match('out'); var obj=JSON.parse(b[ii].innerText); (d?c:s).push(obj); }
copy(c)
copy(s)


# paste the data
xclip -o -sel c > api/server.json
xclip -o -sel c > api/client.json


cp=$(mvnrun org.jsonschema2pojo:jsonschema2pojo-cli)
alias json="java -cp $cp org.jsonschema2pojo.cli.Jsonschema2PojoCLI"
json -s api -t src -T JSON -a GSON -P -da -E -S -p mm.ws -l
cp -R src/mm/ws ../../src/main/java/mm/





# Broadcast.omitUsers
# v4 api the docs say it should be an array of userids
# in v3, it appears to be a dictionary of userid:boolean pairs
# possible that in v3 an array would work too
# for now, leaving it as an Object to allow the app to control the type


json -s api/mapped.json -t src -T JSON -a GSON -P -da -E -S -p mm.ws.server -l -c
json -s api/client.json -t src -T JSON -a GSON -P -da -E -S -p mm.ws.client -l -c
cd mm/ws/client/
mv Data.java ClientData.java 
sed -i "s/\bData\b/ClientData/g" *

cd ../server
rm Broadcast_* OmitUsers* User.java
sed -i "s/public \w* omitUsers/public Object omitUsers/g" *
for ii in Broadcast; do sed -i "s/\b${ii}_*\b/${ii}/g" *.java; done

for ii in Data*; do jj=${ii/.java/}; hostfile=$(grep -l "\b$jj\b" !(Data*)); host=${hostfile/.java/}; data="${host}Data"; sed -i "s/\b${jj}_*\b/${data}/g" *.java; mv $ii "$data".java; done

sed -i "4iimport mm.rest.User;" $(grep -l "\bUser\b" *Data*)

sed -e "s/HelloData/Object/g" -e "s/Hello/Message/g" Hello.java > Message.java
rm !(*Data*|Broadcast.java|Response.java|Message.java)



# devtools: reprocessing server.json

a = JSON.parse($('pre').innerText)
for (var ii=0, map={}; ii < a.length; ii++) {var b=a[ii], name=b.event||'Response', val=map[name]; if (val==null) val=map[name]=[]; val.push(b); }
copy(map)


xclip -o -sel c > api/mapped.json



### payloads
# appear to be identical to the rest (ie non-ws) replies
# encoded as json strings, ie json with json
#   in the database schema, props are strings and the rest sniffing didn't attempt to decode Props
# the only portion of this that was used was Props, and that was (at most) manually copied into Utilmm

var payload = {}; for (var ii in a) { var data = a[ii].data, name=a[ii].event; for (var jj in data) try { d=JSON.parse(data[jj]); var kk=jj; p=payload[kk]; if (p==null) p=payload[kk]=[]; p.push(d); } catch(ex) {} }
copy(payload)

xclip -o -sel c > api/payload.json
json -s api/payload.json -t src2 -T JSON -a NONE -P -da -E -S -p mm.ws.server -l



# ad-hoc processing, example of bash while loop with a variable:

declare -i ii=0; grep post.: api/server.json | while read line; do echo "$line" > /tmp/frame.$ii; echo $((ii++)); done





# docs that helped figure out the devtools on devtools:

// http://jasonlaster.github.io/devtools/js/2016/03/05/getting-websocket-frames.html
// https://stackoverflow.com/questions/11849562/how-to-save-the-output-of-a-console-logobject-to-a-file
// http://bgrins.github.io/devtools-snippets/#console-save


