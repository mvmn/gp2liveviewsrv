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

* POST /capture?camera=usb:020,016\[&download=true]\[&downloadPreview=true]

Optional body with camera config settings to be set before making a shot:

```
{"/main/settings/capturetarget":"Memory card", "/main/capturesettings/shutterspeed":"1/1000"}
``` 

Trigger capture. If download and/or downloadPreview parameters were specified, JSON response with file names will be returned

```
{ "fileName": "IMG_8970.JPG", "previewFileName": "preview_IMG_8970.JPG" }
```

* GET /images

List files in images folder

* GET /images/{filename}

Get image from images folder

### Command-line parametes

* Optional: ```-imagesFolder <path/to/folder>```

Path to images folder, images from which can be downloaded via HTTP API, and to where captured images and previews will be saved. 

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
