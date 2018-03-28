

# not clear from history whether api/*pass* was sniffed using node running full/index.js or mitm/mitm.js
# however, looks like multiple responses per file, so guessing mitm.js

cp api/*pass* UsersPassword.json
json -s UsersPassword.json -t t4 -T JSON -a GSON -P -E -S -l -p mm.rest -ds -dg -da
cp t4/mm/rest/UsersPassword.java ../../ws2/src/mm/rest/


# sniffed directly from devtools
# with mattermost running, browse to localhost:8065
# with devtools open, Account settings -> Security -> access history, active sessions

xclip -o -sel c > SessionsReps.json
xclip -o -sel c > AuditsReps.json


