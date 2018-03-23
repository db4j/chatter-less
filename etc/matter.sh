#! /bin/bash

# in theory the following could set things up:
#   ssh fitno.us "$PWD/etc/matter.sh"

# but it depends on the nginx default site
# so better to do it manually



    cd ~/working/nq0/matter
    mkdir -p db_files/mm_files
    fallocate -l 10G db_files/hunk.mmap

    # default -> /etc/nginx/sites-available/default
    # sudo rm /etc/nginx/sites-enabled/default
    sudo ln -s $PWD/etc/matter.site /etc/nginx/sites-enabled
    sudo systemctl reload nginx



# copy any local dependencies to the remote:

versions=$(mvn dependency:list -DoutputAbsoluteArtifactFilename -DoutputFile=/dev/fd/2 2>&1 1>/dev/null | grep -o "/.*/" | xargs -Ixxx grep -L "jar>central=$" xxx_remote.repositories | grep -o ".*/")
    
rsync -aRvO $versions fitno.us:/
rsync -av --del pom.xml data etc target fitno.us:$PWD
ssh fitno.us "cd $PWD; mvn exec:java -Dexec.mainClass=foobar.MatterFull &> t1.txt &"



    # alternatively, use systemd (automated, but brittle)
    sudo cp etc/matter.service /etc/systemd/system
    sudo systemctl enable matter
    sudo systemctl start matter


    
