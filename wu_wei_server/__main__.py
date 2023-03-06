from flask import Flask, Response, send_from_directory, request
import os

# TODO -- serving static content right now
ENV_MOUNT = os.environ.get( 'WW_ENVIRONMENT_STORAGE', '/app/base-environment' )
PORT = int(os.environ.get('PORT', 5000))

app = Flask(__name__)

@app.route( '/', methods=["GET"] )
def hello():
    return send_from_directory(ENV_MOUNT, "index.html")

@app.route('/<path:path>', methods=["GET","PUT", "POST", "PATCH"] )
def send_report(path):
    if request.method == "GET":
        return send_from_directory(ENV_MOUNT, path)
    else:
    	raise NotImplementedError( "TODO" )
        #entity_id = request.args.get( "entity_id" )
        #entity_content = request.args.get( "entity_content" )
        #print( entity_id, entity_content )
        #print( "WHAT" )
        #return Response("", status=200, mimetype='application/json')

app.run(debug=False, host='0.0.0.0', port=PORT)
