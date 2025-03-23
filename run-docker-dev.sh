docker container stop wu-wei && docker container rm wu-wei
docker run -t \
      -p 9500:9500 \
      -p 3449:3449 \
      -p 7888:7888 \
      --name wu-wei \
      -v ~/wu-wei:/data \
      -v ./frontend:/app/frontend \
      wu-wei:dev-latest

# Now on host system do M-x cider with cider-connect-clj&cljs
