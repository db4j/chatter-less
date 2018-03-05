#! /bin/bash

# cd ~/working/nq0/matter
# rsync -a data etc target/*.jar libs/* fitno.us:$PWD
# ssh fitno.us ~/working/nq0/matter/etc/matter.sh



    cd ~/working/nq0/matter
    mkdir -p db_files/files
    fallocate -l 10G db_files/hunk.mmap
    java -Xmx2g -cp libs/\* foobar.MatterFull

    sudo cp etc/matter.service /etc/systemd/system
    sudo systemctl enable matter
    sudo systemctl start matter
    # default -> /etc/nginx/sites-available/default
    # sudo rm /etc/nginx/sites-enabled/default
    sudo ln -s $PWD/etc/matterless.site /etc/nginx/sites-enabled
    sudo systemctl reload nginx
    


# copy any local dependencies to the remote:
#   versions=$(mvn dependency:list -DoutputAbsoluteArtifactFilename -DoutputFile=/dev/fd/2 2>&1 1>/dev/null | grep -o "/.*/" | xargs -Ixxx grep -L "jar>central=$" xxx_remote.repositories | grep -o ".*/")
#
#   rsync -aRvO $versions fitno.us:/
