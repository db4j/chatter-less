(echo "["; echo select NotifyProps from Users | mysql -N mattermost | while read line; do echo "$line,"; done | head -c -2; echo; echo "]") > notifyProps.json


~/install/apps/pip/bin/genson -i4 notifyProps.json > notifyProps.schema


cp=$(mvnrun org.jsonschema2pojo:jsonschema2pojo-cli)
alias json="java -cp $cp org.jsonschema2pojo.cli.Jsonschema2PojoCLI"
json -s notifyProps.schema -t t3 -a GSON -P -E -S -l -p mm.rest -ds -dg -da



