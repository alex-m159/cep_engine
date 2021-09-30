# 
# You can also run the application with `python client.py`
# but his method is much MUCH faster since it enables the SocketIO
# clients and servers to use normal (read: non-polling/event-driven) communication. 
# Without Gunicorn the SocketIO communication will fallback to polling since the Flask
# debug server can't handle it.
#
gunicorn -k geventwebsocket.gunicorn.workers.GeventWebSocketWorker -w 1 client:app -b localhost:5000
