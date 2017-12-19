#! /bin/bash

# cd ~/working/nq0/matter
# rsync -a data etc target/*.jar libs/* fitno.us:$PWD
# ssh fitno.us ~/working/nq0/matter/etc/matter.sh



    cd ~/working/nq0/matter
    mkdir -p db_files
    fallocate -l 10G db_files/hunk.mmap
    java -Xmx2g -cp libs/\* foobar.MatterFull

    sudo cp etc/matter.service /etc/systemd/system
    sudo systemctl enable matter
    sudo systemctl start matter
    sudo ln -s $PWD/etc/mm.nqzero.site /etc/nginx/sites-enabled
    sudo systemctl reload nginx
    


