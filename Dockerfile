# Start from the Clojure image
FROM clojure:tools-deps

# Set working directory in Docker container
WORKDIR /app

# Copy project files into the container
COPY . .

# Go into frontend dir
# This should be cleaned up some day
WORKDIR ./frontend/wu-wei

# Expose ports for fighwheel, nrepl, and HTTP development server hosting
EXPOSE 3449 7888 9500

# Install dependencies
RUN clojure -Stree

# Set the startup command
# DEVELOPMENT ONLY -- OPENS PORT FOR REMOTE CODE EXECUTION WITHOUT AUTHENTICATION
CMD [ "clj", "-M:cider-cljs" ]
