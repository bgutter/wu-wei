docker container stop wu-wei && docker container rm wu-wei

docker run \
       -p 9500:9500 \
       -p 3449:3449 \
       --name wu-wei \
       -v ~/wu-wei:/data \
       wu-wei
