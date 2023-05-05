# 
# You can also run the application with `python client.py`
# but his method is much MUCH faster since it enables the SocketIO
# clients and servers to use normal (read: non-polling/event-driven) communication. 
# Without Gunicorn the SocketIO communication will fallback to polling since the Flask
# debug server can't handle it.
#
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd $SCRIPT_DIR
if [ "$DEBUG" -eq "1" ]; 
then
    # source /app/venv-docker/bin/activate && python /app/client.py
    sleep infinity
else
    source /app/venv-docker/bin/activate && gunicorn -k geventwebsocket.gunicorn.workers.GeventWebSocketWorker -w 1 client:app -b 0.0.0.0:5000
fi