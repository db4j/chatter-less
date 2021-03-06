# ChatterLess

slack-like real-time chat in team-based channels
* a realworld demo of [db4j](https://github.com/db4j/db4j), a transactional ACID database that uses java as the query language
* implemented in java with db4j, [kilim](https://github.com/nqzero/kilim), jetty and spring


ChatterLess is a java reimplementation of the [Mattermost team-edition server](https://github.com/mattermost/mattermost-server) using db4j as the database.
This is primarily a demo of db4j:

* supports most of the user messaging features including teams, channels, posts, search, reactions, and images
* Slash commands and enterprise features aren't supported
* hasn't been tested for compliance with the mobile apps
* permissions are not generally enforced, eg anyone can delete any post

It was produced by sniffing the API used between the official client and server, and viewing data stored in mysql

The client is unchanged

## Try it Live

https://mm.nqzero.com

## Building

clone this project, download the official MIT-licensed binary client, and build

```
git clone https://github.com/db4j/chatter-less.git && cd chatter-less
wget https://releases.mattermost.com/3.10.3/mattermost-team-3.10.3-linux-amd64.tar.gz
tar -zxf *.tar.gz
sudo apt install nginx
sed "s MATTER_ROOT $PWD/mattermost " etc/matter.site | sudo tee /etc/nginx/sites-enabled/matter.site
sudo systemctl reload nginx
mkdir -p db_files/mm_files
fallocate -l 10G db_files/hunk.mmap
mvn clean package exec:java -Dexec.mainClass=foobar.MatterFull
```

## Version

this is based on Mattermost 3.10 team edition.
it's probably relatively easy to upgrade to the 4.x api, but this work is not planned (this is primarily a demo of db4j)



## License

All code in this repository is offered under the terms of the MIT license.
However, it depends on [db4j](https://github.com/db4j/db4j), which has a [liberal-but-not-free license](https://github.com/db4j/pupl).
A use-license waiver is granted for the use of db4j in the running of this code, including MIT-licensed derived versions that primarily implement a version of the  mattermost team-chat api

The Mattermost client binary (downloaded in the [build section above](#Building)) is MIT licensed.
The client source code is available under the AGPL.
However, this server is not derived from any Mattermost code, so it should be legal to use with the client source code instead of the binary in conjunction with this server








