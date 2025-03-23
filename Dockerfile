# Start from the Clojure image
# Download and install dependencies
FROM clojure:tools-deps AS base
WORKDIR /app
COPY . .
WORKDIR /app/frontend/wu-wei
RUN clojure -Stree

# Set the startup command
# DEVELOPMENT ONLY -- OPENS PORT FOR REMOTE CODE EXECUTION WITHOUT AUTHENTICATION
# Expose ports for fighwheel, nrepl, and HTTP development server hosting
# Downlaod and install dev dependencies
FROM base AS development
EXPOSE 3449 7888 9500
RUN clojure -A:cider-cljs -Stree
CMD [ "clj", "-M:cider-cljs" ]
