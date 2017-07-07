#! /bin/bash

files=($@)

declare -i len=${#files[*]}
declare -i ii jj nwords num best jbest



for ((ii=1; ii < $len; ii++)); do
    ni=${files[$ii]}
    nwords=$(cat $ni | wc -l);
    best=$nwords+999
    for ((jj=0; jj < $ii; jj++)); do
	nj=${files[$jj]}
	num=$(dwdiff -c -C0 $nj $ni | wc -l)
	if [ $num -lt $best ]; then
	    best=$num
	    jbest=$jj;
	fi
    done
    bfile=${files[$jbest]}
    printf "%50s %50s %5d %5d\n" $bfile $ni $best $nwords
done


