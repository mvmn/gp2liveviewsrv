### HTTP API

* GET /cameras

Returns JSON with list of cameras and their ports, for example:
```
{"cameras":[{"cameraModel":"Canon EOS 600D","portName":"usb:020,017"},{"cameraModel":"USB PTP Class Camera","portName":"usb:020,016"}]}
```
* GET /liveView?camera=usb:020,016

Starts live-view and serves MJPEG stream from chosen camera port (specified via *camera* parameter)

* POST /stopLiveView

Stops currently active live-view

### Command-line parametes

* Optional: ```-port <number>```

Change server port

* Optional: ```-authToken <authorization token>```

Specify shared secret to be sent with every HTTP request for authorization in X-AuthToken header. When specified, all requests without proper token value will be denied.

### Java System properties

* Optional: ```-Djna.library.path=/home/pi/lib```

Path to libgphoto2 containing folder (see more in [JNA documentation](https://github.com/java-native-access/jna/blob/master/www/GettingStarted.md))

### Example execution with parameters

```java -Djna.library.path=/home/pi/lib -jar /path/to/gp2lvs-1.0.0.jar -port 9999```

### Own dependencies

* https://github.com/mvmn/jlibgphoto2
* https://github.com/mvmn/gphoto2-jna
